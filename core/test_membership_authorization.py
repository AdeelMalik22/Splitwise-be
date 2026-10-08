from rest_framework import status
from rest_framework.test import APITestCase

from core.models import Group, UserGroup
from user.models import GroupInvite, User


class MembershipAuthorizationTests(APITestCase):
    def setUp(self):
        self.owner = User.objects.create_user(username='owner', email='o@example.com', password='password-123')
        self.outsider = User.objects.create_user(username='out', email='x@example.com', password='password-123')
        self.group = Group.objects.create(name='Trip', description='Trip')
        self.other_group = Group.objects.create(name='Other', description='Other')
        UserGroup.objects.create(user_id=self.owner, group_id=self.group)

    def test_cannot_join_group_without_accepted_invite(self):
        self.client.force_authenticate(self.outsider)
        response = self.client.post('/usersgroup/', {
            'user_id': self.outsider.pk, 'group_id': self.group.pk,
        }, format='json')
        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)
        self.assertFalse(UserGroup.objects.filter(user_id=self.outsider).exists())

    def test_can_join_group_with_accepted_invite(self):
        GroupInvite.objects.create(group=self.group, inviter=self.owner, invitee=self.outsider,
                                   status=GroupInvite.ACCEPTED)
        self.client.force_authenticate(self.outsider)
        response = self.client.post('/usersgroup/', {
            'user_id': self.outsider.pk, 'group_id': self.group.pk,
        }, format='json')
        self.assertEqual(response.status_code, status.HTTP_201_CREATED)

    def test_membership_group_cannot_be_changed(self):
        membership = UserGroup.objects.get(user_id=self.owner)
        self.client.force_authenticate(self.owner)
        response = self.client.patch(f'/usersgroup/{membership.pk}/', {
            'group_id': self.other_group.pk,
        }, format='json')
        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)
        membership.refresh_from_db()
        self.assertEqual(membership.group_id_id, self.group.pk)
