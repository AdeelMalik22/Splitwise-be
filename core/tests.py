from unittest.mock import patch

from django.test import SimpleTestCase
from rest_framework import status
from rest_framework.test import APITestCase

from core.settlements import get_settlements_for_group
from core.views import GroupViewSet
from core.models import Group, UserGroup
from user.models import User


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
    @patch('core.views.Activity.objects.create')
    def test_group_creation_adds_creator_to_group(self, create_activity, get_or_create):
        group = type('Group', (), {'pk': 1})()
        serializer = type('Serializer', (), {'save': lambda self: group})()
        view = GroupViewSet()
        view.request = type('Request', (), {'user': 'user'})()

        view.perform_create(serializer)

        get_or_create.assert_called_once_with(user_id='user', group_id=group)
        create_activity.assert_called_once_with(
            actor='user', action='created', entity_type='group', entity_id=1
        )


class ExpenseWorkflowIntegrationTests(APITestCase):
    def setUp(self):
        self.owner = User.objects.create_user(
            username='owner', email='owner@example.com', password='password-123'
        )
        self.member = User.objects.create_user(
            username='member', email='member@example.com', password='password-123'
        )
        self.client.force_authenticate(self.owner)

    def create_group_with_member(self):
        response = self.client.post('/groups/', {
            'name': 'Trip', 'description': 'Shared trip',
        }, format='json')
        self.assertEqual(response.status_code, status.HTTP_201_CREATED)
        group_id = response.data['id']
        response = self.client.post('/usersgroup/', {
            'user_id': self.member.pk, 'group_id': group_id,
        }, format='json')
        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)
        return group_id

    def test_group_creation_and_membership_boundaries(self):
        response = self.client.post('/groups/', {
            'name': 'Trip', 'description': 'Shared trip',
        }, format='json')
        self.assertEqual(response.status_code, status.HTTP_201_CREATED)
        group_id = response.data['id']

        # Users can only create memberships for themselves.
        response = self.client.post('/usersgroup/', {
            'user_id': self.member.pk, 'group_id': group_id,
        }, format='json')
        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)

        self.assertEqual(self.client.get('/groups/').status_code, status.HTTP_200_OK)

    def test_expense_and_settlement_workflow(self):
        group = Group.objects.create(name='Trip', description='Shared trip')
        UserGroup.objects.create(user_id=self.owner, group_id=group)
        UserGroup.objects.create(user_id=self.member, group_id=group)

        response = self.client.post('/expense/', {
            'name': 'Dinner', 'description': 'Food', 'amount': '100.00',
            'paid_by': [self.owner.pk], 'split_on': [self.owner.pk, self.member.pk],
            'group_id': group.pk,
        }, format='json')
        self.assertEqual(response.status_code, status.HTTP_201_CREATED)
        self.assertEqual(response.data['paid_by'], [self.owner.pk])
        self.assertEqual(response.data['split_on'], [self.owner.pk, self.member.pk])

        response = self.client.get(f'/expense/{group.pk}/settlements/')
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(response.data['you will get'][0]['amount'], 50.0)

    def test_expense_participants_must_belong_to_group(self):
        group = Group.objects.create(name='Trip', description='Shared trip')
        UserGroup.objects.create(user_id=self.owner, group_id=group)
        response = self.client.post('/expense/', {
            'name': 'Dinner', 'description': 'Food', 'amount': '100.00',
            'paid_by': [self.member.pk], 'split_on': [self.owner.pk],
            'group_id': group.pk,
        }, format='json')
        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)

    def test_expense_supports_unequal_amount_split(self):
        group = Group.objects.create(name='Trip', description='Shared trip')
        UserGroup.objects.create(user_id=self.owner, group_id=group)
        UserGroup.objects.create(user_id=self.member, group_id=group)
        response = self.client.post('/expense/', {
            'name': 'Hotel', 'description': 'Unequal split', 'amount': '100.00',
            'paid_by': [self.owner.pk],
            'split_details': [
                {'user_id': self.owner.pk, 'amount': '70.00'},
                {'user_id': self.member.pk, 'amount': '30.00'},
            ],
            'group_id': group.pk,
        }, format='json')
        self.assertEqual(response.status_code, status.HTTP_201_CREATED)
        self.assertEqual(len(response.data['split_details']), 2)
