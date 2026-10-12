from rest_framework.test import APITestCase

from core.models import Expense, Group, UserGroup
from user.models import User


class ExpenseFilterTests(APITestCase):
    def setUp(self):
        self.user = User.objects.create_user(username='filter', email='filter@example.com', password='password-123', email_verified=True)
        self.group = Group.objects.create(name='Filters')
        UserGroup.objects.create(user_id=self.user, group_id=self.group)
        for name, category, amount in [('Dinner', 'food', 50), ('Taxi', 'travel', 25)]:
            expense = Expense.objects.create(name=name, amount=amount, category=category, group_id=self.group)
            from core.models import ExpenseParticipant
            ExpenseParticipant.objects.create(expense=expense, user=self.user, role=ExpenseParticipant.PAID)
            ExpenseParticipant.objects.create(expense=expense, user=self.user, role=ExpenseParticipant.SPLIT)
        self.client.force_authenticate(self.user)

    def test_category_and_search_filters(self):
        self.assertEqual(len(self.client.get('/expense/?category=food').data), 1)
        self.assertEqual(len(self.client.get('/expense/?q=taxi').data), 1)
