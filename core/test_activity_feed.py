from rest_framework import status
from rest_framework.test import APITestCase

from core.models import Activity, Group, UserGroup
from user.models import User


class ActivityFeedTests(APITestCase):
    def setUp(self):
        self.alice = User.objects.create_user(username='alice', email='a@example.com', password='password-123')
        self.bob = User.objects.create_user(username='bob', email='b@example.com', password='password-123')
        self.eve = User.objects.create_user(username='eve', email='e@example.com', password='password-123')
        self.client.force_authenticate(self.alice)
        self.group_id = self.client.post('/groups/', {'name': 'Trip'}, format='json').data['id']
        UserGroup.objects.create(user_id=self.bob, group_id_id=self.group_id)

    def feed(self, user):
        self.client.force_authenticate(user)
        return self.client.get('/activity/').data['results']

    def add_expense(self, amount='90.00'):
        self.client.force_authenticate(self.alice)
        return self.client.post('/expense/', {
            'name': 'Dinner', 'description': '', 'amount': amount, 'group_id': self.group_id,
            'paid_by': [self.alice.pk], 'split_on': [self.alice.pk, self.bob.pk]}, format='json')

    def test_group_members_see_each_others_activity_but_outsiders_do_not(self):
        self.add_expense()
        bob_feed = self.feed(self.bob)
        self.assertIn(('added', 'expense'), [(a['action'], a['entity_type']) for a in bob_feed])
        added = next(a for a in bob_feed if a['action'] == 'added')
        self.assertEqual(added['actor_username'], 'alice')
        self.assertEqual(added['metadata']['name'], 'Dinner')
        self.assertEqual(added['group_id'], self.group_id)
        self.assertEqual(self.feed(self.eve), [])

    def test_edit_delete_leave_and_group_changes_are_logged(self):
        expense_id = self.add_expense().data['id']
        self.client.patch(f'/expense/{expense_id}/', {'name': 'Lunch'}, format='json')
        self.client.delete(f'/expense/{expense_id}/')
        self.client.patch(f'/groups/{self.group_id}/', {'name': 'Trip 2'}, format='json')

        self.client.force_authenticate(self.bob)
        membership = UserGroup.objects.get(user_id=self.bob, group_id_id=self.group_id)
        self.assertEqual(self.client.delete(f'/usersgroup/{membership.pk}/').status_code, status.HTTP_204_NO_CONTENT)

        actions = {(a.action, a.entity_type) for a in Activity.objects.filter(group_id=self.group_id)}
        self.assertTrue({('created', 'group'), ('added', 'expense'), ('edited', 'expense'),
                         ('deleted', 'expense'), ('updated', 'group'), ('left', 'group')} <= actions)

    def test_deleted_group_event_stays_visible_to_the_actor_only(self):
        self.client.delete(f'/groups/{self.group_id}/')
        self.assertIn('deleted', [a['action'] for a in self.feed(self.alice)])
        self.assertEqual(self.feed(self.bob), [])

    def test_payment_events_are_logged(self):
        self.add_expense()
        self.client.force_authenticate(self.bob)
        payment_id = self.client.post('/payments/', {'group': self.group_id, 'payee': self.alice.pk, 'amount': '45.00'}, format='json').data['id']
        self.client.force_authenticate(self.alice)
        self.client.post(f'/payments/{payment_id}/confirm/')
        actions = [(a['action'], a['entity_type']) for a in self.feed(self.alice)]
        self.assertIn(('paid', 'payment'), actions)
        self.assertIn(('confirmed', 'payment'), actions)
