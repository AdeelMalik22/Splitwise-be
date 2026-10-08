from rest_framework import status
from rest_framework.test import APITestCase

from core.models import Group, UserGroup
from user.models import User


class InviteNamesTests(APITestCase):
    def test_invite_includes_display_names(self):
        owner = User.objects.create_user(username='owner', name='Olive', email='o@example.com', password='password-123')
        friend = User.objects.create_user(username='friend', email='f@example.com', password='password-123')
        group = Group.objects.create(name='Trip', description='Trip')
        UserGroup.objects.create(user_id=owner, group_id=group)
        self.client.force_authenticate(owner)

        response = self.client.post('/invites/', {'group': group.pk, 'invitee': friend.pk}, format='json')

        self.assertEqual(response.status_code, status.HTTP_201_CREATED)
        self.assertEqual(response.data['group_name'], 'Trip')
        self.assertEqual(response.data['inviter_username'], 'owner')
        self.assertEqual(response.data['invitee_username'], 'friend')
