from rest_framework import status
from rest_framework.test import APITestCase

from core.models import Group, UserGroup
from user.models import User


class GroupPermissionTests(APITestCase):
    def setUp(self):
        self.owner = User.objects.create_user(username='owner', email='o@example.com', password='password-123')
        self.member = User.objects.create_user(username='member', email='m@example.com', password='password-123')
        self.outsider = User.objects.create_user(username='out', email='x@example.com', password='password-123')
        self.client.force_authenticate(self.owner)
        response = self.client.post('/groups/', {'name': 'Trip', 'description': 'Trip', 'icon': '🏔️'}, format='json')
        self.assertEqual(response.status_code, status.HTTP_201_CREATED)
        self.group = Group.objects.get(pk=response.data['id'])
        UserGroup.objects.create(user_id=self.member, group_id=self.group)

    def test_creator_and_icon_are_recorded(self):
        self.assertEqual(self.group.created_by_id, self.owner.pk)
        self.assertEqual(self.group.icon, '🏔️')

    def test_creator_cannot_be_spoofed_on_create(self):
        response = self.client.post('/groups/', {'name': 'X', 'description': 'X', 'created_by': self.outsider.pk}, format='json')
        self.assertEqual(Group.objects.get(pk=response.data['id']).created_by_id, self.owner.pk)

    def test_any_member_can_rename_and_change_icon(self):
        self.client.force_authenticate(self.member)
        response = self.client.patch(f'/groups/{self.group.pk}/', {'name': 'Renamed', 'icon': '🎉'}, format='json')
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.group.refresh_from_db()
        self.assertEqual((self.group.name, self.group.icon), ('Renamed', '🎉'))

    def test_outsider_cannot_see_or_edit_group(self):
        self.client.force_authenticate(self.outsider)
        self.assertEqual(self.client.patch(f'/groups/{self.group.pk}/', {'name': 'Hacked'}, format='json').status_code,
                         status.HTTP_404_NOT_FOUND)

    def test_only_creator_can_delete_group(self):
        self.client.force_authenticate(self.member)
        self.assertEqual(self.client.delete(f'/groups/{self.group.pk}/').status_code, status.HTTP_403_FORBIDDEN)
        self.assertEqual(self.client.delete(f'/groups/{self.group.pk}/delete_group/').status_code, status.HTTP_403_FORBIDDEN)
        self.assertTrue(Group.objects.filter(pk=self.group.pk).exists())
        self.client.force_authenticate(self.owner)
        self.assertEqual(self.client.delete(f'/groups/{self.group.pk}/').status_code, status.HTTP_204_NO_CONTENT)

    def test_only_creator_can_remove_members(self):
        self.client.force_authenticate(self.member)
        self.assertEqual(self.client.delete(f'/groups/{self.group.pk}/members/{self.owner.pk}/').status_code,
                         status.HTTP_403_FORBIDDEN)
        self.client.force_authenticate(self.owner)
        self.assertEqual(self.client.delete(f'/groups/{self.group.pk}/members/{self.owner.pk}/').status_code,
                         status.HTTP_400_BAD_REQUEST)
        self.assertEqual(self.client.delete(f'/groups/{self.group.pk}/members/{self.member.pk}/').status_code,
                         status.HTTP_204_NO_CONTENT)
        self.assertFalse(UserGroup.objects.filter(group_id=self.group, user_id=self.member).exists())
        self.assertEqual(self.client.delete(f'/groups/{self.group.pk}/members/{self.member.pk}/').status_code,
                         status.HTTP_404_NOT_FOUND)
