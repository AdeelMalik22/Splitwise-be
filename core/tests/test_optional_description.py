from rest_framework import status
from rest_framework.test import APITestCase

from core.models import Group, UserGroup
from user.models import User


class OptionalDescriptionTests(APITestCase):
    def setUp(self):
        self.user = User.objects.create_user(username='u', email='u@example.com', password='password-123')
        self.client.force_authenticate(self.user)

    def test_group_without_description(self):
        response = self.client.post('/groups/', {'name': 'Trip', 'description': ''}, format='json')
        self.assertEqual(response.status_code, status.HTTP_201_CREATED)
        response = self.client.post('/groups/', {'name': 'Trip 2'}, format='json')
        self.assertEqual(response.status_code, status.HTTP_201_CREATED)

    def test_expense_without_description(self):
        group = Group.objects.create(name='Trip')
        UserGroup.objects.create(user_id=self.user, group_id=group)
        response = self.client.post('/expense/', {
            'name': 'Taxi', 'description': '', 'amount': '10.00',
            'paid_by': [self.user.pk], 'split_on': [self.user.pk], 'group_id': group.pk,
        }, format='json')
        self.assertEqual(response.status_code, status.HTTP_201_CREATED)
