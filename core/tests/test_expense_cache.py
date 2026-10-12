from django.core.cache import cache
from rest_framework import status
from rest_framework.test import APITestCase

from core.models import Group, UserGroup
from user.models import User


class ExpenseCacheTests(APITestCase):
    def setUp(self):
        cache.clear()
        self.owner = User.objects.create_user(username='owner', email='o@example.com', password='password-123')
        self.member = User.objects.create_user(username='member', email='m@example.com', password='password-123')
        self.group = Group.objects.create(name='Trip', description='Trip')
        UserGroup.objects.create(user_id=self.owner, group_id=self.group)
        UserGroup.objects.create(user_id=self.member, group_id=self.group)

    def _create_expense(self):
        self.client.force_authenticate(self.owner)
        return self.client.post('/expense/', {
            'name': 'Dinner', 'description': 'Food', 'amount': '100.00',
            'paid_by': [self.owner.pk], 'split_on': [self.owner.pk, self.member.pk],
            'group_id': self.group.pk,
        }, format='json')

    def test_new_expense_invalidates_every_members_cached_list(self):
        self.client.force_authenticate(self.member)
        self.assertEqual(self.client.get('/expense/').data, [])

        self.assertEqual(self._create_expense().status_code, status.HTTP_201_CREATED)

        self.client.force_authenticate(self.member)
        self.assertEqual(len(self.client.get('/expense/').data), 1)

    def test_deleting_expense_invalidates_cached_list(self):
        expense_id = self._create_expense().data['id']
        self.assertEqual(len(self.client.get('/expense/').data), 1)

        self.assertEqual(self.client.delete(f'/expense/{expense_id}/').status_code,
                         status.HTTP_204_NO_CONTENT)

        self.assertEqual(self.client.get('/expense/').data, [])

    def test_multiple_payer_amounts_are_returned(self):
        self.client.force_authenticate(self.owner)
        response = self.client.post('/expense/', {
            'name': 'Shared dinner', 'amount': '100.00', 'paid_by': [self.owner.pk, self.member.pk],
            'paid_details': [{'user_id': self.owner.pk, 'amount': '40.00'},
                             {'user_id': self.member.pk, 'amount': '60.00'}],
            'split_on': [self.owner.pk, self.member.pk], 'group_id': self.group.pk,
        }, format='json')
        self.assertEqual(response.status_code, status.HTTP_201_CREATED)
        self.assertEqual([(row['user_id'], str(row['amount'])) for row in response.data['paid_details']], [
            (self.owner.pk, '40.00'), (self.member.pk, '60.00'),
        ])
