from rest_framework import status
from rest_framework.test import APITestCase

from core.models import Group, UserGroup, Expense, ExpenseParticipant
from user.models import User


class PartialUpdateTests(APITestCase):
    def setUp(self):
        self.owner = User.objects.create_user(username='owner', email='o@example.com', password='password-123')
        self.member = User.objects.create_user(username='member', email='m@example.com', password='password-123')
        self.group = Group.objects.create(name='Trip', description='Trip')
        UserGroup.objects.create(user_id=self.owner, group_id=self.group)
        UserGroup.objects.create(user_id=self.member, group_id=self.group)
        self.expense = Expense.objects.create(name='Dinner', description='Food', amount='100.00',
                                              group_id=self.group)
        ExpenseParticipant.objects.create(expense=self.expense, user=self.owner, role=ExpenseParticipant.PAID)
        for user in (self.owner, self.member):
            ExpenseParticipant.objects.create(expense=self.expense, user=user, role=ExpenseParticipant.SPLIT)
        self.client.force_authenticate(self.owner)

    def test_patch_expense_name_keeps_participants(self):
        response = self.client.patch(f'/expense/{self.expense.pk}/', {'name': 'Lunch'}, format='json')
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(response.data['name'], 'Lunch')
        self.assertEqual(response.data['paid_by'], [self.owner.pk])
        self.assertEqual(len(response.data['split_on']), 2)
