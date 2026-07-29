from unittest.mock import patch

from django.test import SimpleTestCase

from core.settlements import get_settlements_for_group
from core.views import GroupViewSet


class SettlementTests(SimpleTestCase):
    @patch('core.settlements.get_username', side_effect=lambda user_id: f'user-{user_id}')
    def test_settlements_split_expense_between_two_users(self, get_username):
        expenses = [{
            'amount': 100,
            'paid_by': [1],
            'split_on': [1, 2],
        }]

        result = get_settlements_for_group(expenses, user_id=1)

        self.assertEqual(result['you will get'], [{'from_user': 'user-2', 'amount': 50.0}])
        self.assertEqual(result['You need to pay'], [])

    @patch('core.settlements.get_username', side_effect=lambda user_id: f'user-{user_id}')
    def test_empty_participants_are_ignored(self, get_username):
        result = get_settlements_for_group([
            {'amount': 100, 'paid_by': [], 'split_on': [1]},
            {'amount': 100, 'paid_by': [1], 'split_on': []},
        ], user_id=1)

        self.assertEqual(result, {'You need to pay': [], 'you will get': []})


class GroupOwnershipTests(SimpleTestCase):
    @patch('core.views.UserGroup.objects.get_or_create')
    def test_group_creation_adds_creator_to_group(self, get_or_create):
        serializer = type('Serializer', (), {'save': lambda self: 'group'})()
        view = GroupViewSet()
        view.request = type('Request', (), {'user': 'user'})()

        view.perform_create(serializer)

        get_or_create.assert_called_once_with(user_id='user', group_id='group')
