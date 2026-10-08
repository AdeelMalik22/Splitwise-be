from rest_framework import status
from rest_framework.test import APITestCase

from core.models import Group, UserGroup
from user.models import User


class AccountManagementTests(APITestCase):
    def setUp(self):
        self.user = User.objects.create_user(username='sam', name='Sam', email='s@example.com', password='old-password-1')
        self.client.force_authenticate(self.user)

    def test_profile_edit_cannot_change_password(self):
        response = self.client.patch(f'/users/{self.user.pk}/', {'name': 'Samuel', 'password': 'hijacked-123'}, format='json')
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.user.refresh_from_db()
        self.assertEqual(self.user.name, 'Samuel')
        self.assertTrue(self.user.check_password('old-password-1'))

    def test_change_password_requires_the_old_password(self):
        bad = self.client.post('/users/change_password/', {'old_password': 'nope', 'new_password': 'brand-new-pass-9'}, format='json')
        self.assertEqual(bad.status_code, status.HTTP_400_BAD_REQUEST)
        ok = self.client.post('/users/change_password/', {'old_password': 'old-password-1', 'new_password': 'brand-new-pass-9'}, format='json')
        self.assertEqual(ok.status_code, status.HTTP_200_OK)
        self.user.refresh_from_db()
        self.assertTrue(self.user.check_password('brand-new-pass-9'))

    def test_change_password_rejects_weak_passwords(self):
        response = self.client.post('/users/change_password/', {'old_password': 'old-password-1', 'new_password': '12345678'}, format='json')
        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)

    def test_delete_account_requires_password_and_removes_memberships(self):
        group = Group.objects.create(name='G', description='G')
        UserGroup.objects.create(user_id=self.user, group_id=group)
        self.assertEqual(self.client.post('/users/delete_account/', {'password': 'wrong'}, format='json').status_code,
                         status.HTTP_400_BAD_REQUEST)
        self.assertTrue(User.objects.filter(pk=self.user.pk).exists())

        response = self.client.post('/users/delete_account/', {'password': 'old-password-1'}, format='json')
        self.assertEqual(response.status_code, status.HTTP_204_NO_CONTENT)
        self.assertFalse(User.objects.filter(pk=self.user.pk).exists())
        self.assertFalse(UserGroup.objects.filter(group_id=group).exists())
