from rest_framework import status
from rest_framework.test import APITestCase

from core.models import Group, UserGroup
from user.models import User


class GroupUsersPrivacyTests(APITestCase):
    def test_group_users_response_excludes_sensitive_fields(self):
        owner = User.objects.create_user(username='owner', email='o@example.com', password='password-123')
        member = User.objects.create_user(username='member', email='m@example.com', password='password-123')
        group = Group.objects.create(name='Trip', description='Trip')
        UserGroup.objects.create(user_id=owner, group_id=group)
        UserGroup.objects.create(user_id=member, group_id=group)
        self.client.force_authenticate(owner)

        response = self.client.get(f'/usersgroup/{group.pk}/users/')

        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(len(response.data), 2)
        for user in response.data:
            self.assertNotIn('password', user)
            self.assertNotIn('email', user)
