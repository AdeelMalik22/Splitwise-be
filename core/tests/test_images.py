import io
import shutil
import tempfile

from django.core.files.uploadedfile import SimpleUploadedFile
from django.test import override_settings
from PIL import Image
from rest_framework import status
from rest_framework.test import APITestCase

from core.models import Group, UserGroup
from user.models import User

MEDIA = tempfile.mkdtemp(prefix='splitease-test-media-')


def picture(width=1200, height=800, fmt='PNG', name='photo.png'):
    buffer = io.BytesIO()
    Image.new('RGB', (width, height), (200, 80, 40)).save(buffer, fmt)
    return SimpleUploadedFile(name, buffer.getvalue(), content_type=f'image/{fmt.lower()}')


@override_settings(MEDIA_ROOT=MEDIA)
class PictureUploadTests(APITestCase):
    @classmethod
    def tearDownClass(cls):
        super().tearDownClass()
        shutil.rmtree(MEDIA, ignore_errors=True)

    def setUp(self):
        self.alice = User.objects.create_user(username='alice', email='a@example.com', password='password-123')
        self.bob = User.objects.create_user(username='bob', email='b@example.com', password='password-123')
        self.eve = User.objects.create_user(username='eve', email='e@example.com', password='password-123')
        self.group = Group.objects.create(name='Trip', created_by=self.alice)
        for u in (self.alice, self.bob):
            UserGroup.objects.create(user_id=u, group_id=self.group)
        self.client.force_authenticate(self.alice)

    def test_avatar_is_resized_reencoded_and_served(self):
        response = self.client.post('/users/avatar/', {'image': picture()}, format='multipart')
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        url = response.data['avatar']
        self.assertTrue(url.startswith('http://testserver/media/avatars/') and url.endswith('.jpg'))

        served = self.client.get(url.replace('http://testserver', ''))
        self.assertEqual(served.status_code, 200)
        image = Image.open(io.BytesIO(b''.join(served.streaming_content)))
        self.assertEqual(image.format, 'JPEG')
        self.assertLessEqual(max(image.size), 512)

    def test_replacing_and_removing_delete_the_old_file(self):
        self.client.post('/users/avatar/', {'image': picture()}, format='multipart')
        first = User.objects.get(pk=self.alice.pk).avatar
        first_path = first.path
        self.client.post('/users/avatar/', {'image': picture(300, 300)}, format='multipart')
        import os
        self.assertFalse(os.path.exists(first_path))
        second = User.objects.get(pk=self.alice.pk).avatar.path
        self.assertTrue(os.path.exists(second))
        self.assertEqual(self.client.delete('/users/avatar/').data['avatar'], None)
        self.assertFalse(os.path.exists(second))

    def test_non_images_and_oversized_uploads_are_rejected(self):
        fake = SimpleUploadedFile('evil.png', b'<?php echo 1; ?>', content_type='image/png')
        self.assertEqual(self.client.post('/users/avatar/', {'image': fake}, format='multipart').status_code, 400)
        self.assertEqual(self.client.post('/users/avatar/', {}, format='multipart').status_code, 400)
        with override_settings():
            from core import images
            old = images.MAX_UPLOAD_BYTES
            images.MAX_UPLOAD_BYTES = 100
            try:
                self.assertEqual(self.client.post('/users/avatar/', {'image': picture()}, format='multipart').status_code, 400)
            finally:
                images.MAX_UPLOAD_BYTES = old

    def test_exif_location_data_is_stripped(self):
        buffer = io.BytesIO()
        image = Image.new('RGB', (100, 100), 'red')
        exif = Image.Exif()
        exif[0x010F] = 'SecretPhone'  # camera make
        image.save(buffer, 'JPEG', exif=exif)
        upload = SimpleUploadedFile('p.jpg', buffer.getvalue(), content_type='image/jpeg')
        self.client.post('/users/avatar/', {'image': upload}, format='multipart')
        stored = Image.open(User.objects.get(pk=self.alice.pk).avatar.path)
        self.assertEqual(len(stored.getexif()), 0)

    def test_any_member_can_set_the_group_image_but_outsiders_cannot(self):
        self.client.force_authenticate(self.bob)
        response = self.client.post(f'/groups/{self.group.pk}/image/', {'image': picture()}, format='multipart')
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertIn('/media/groups/', response.data['image'])

        self.client.force_authenticate(self.eve)
        self.assertEqual(self.client.post(f'/groups/{self.group.pk}/image/', {'image': picture()}, format='multipart').status_code, 404)
        self.assertEqual(self.client.delete(f'/groups/{self.group.pk}/image/').status_code, 404)

        self.client.force_authenticate(self.alice)
        self.assertIsNone(self.client.delete(f'/groups/{self.group.pk}/image/').data['image'])

    def test_member_list_and_search_include_avatars(self):
        self.client.post('/users/avatar/', {'image': picture()}, format='multipart')
        members = self.client.get(f'/usersgroup/{self.group.pk}/users/').data
        mine = next(m for m in members if m['username'] == 'alice')
        theirs = next(m for m in members if m['username'] == 'bob')
        self.assertIn('/media/avatars/', mine['avatar'])
        self.assertIsNone(theirs['avatar'])
        found = self.client.get('/users/search/', {'q': 'ali'}).data
        self.assertEqual(found, [])  # you don't find yourself
        self.client.force_authenticate(self.bob)
        found = self.client.get('/users/search/', {'q': 'ali'}).data
        self.assertIn('/media/avatars/', found[0]['avatar'])

    def test_deleting_a_group_or_account_removes_the_files(self):
        import os
        self.client.post(f'/groups/{self.group.pk}/image/', {'image': picture()}, format='multipart')
        self.client.post('/users/avatar/', {'image': picture()}, format='multipart')
        group_file = Group.objects.get(pk=self.group.pk).image.path
        avatar_file = User.objects.get(pk=self.alice.pk).avatar.path
        self.assertEqual(self.client.delete(f'/groups/{self.group.pk}/').status_code, 204)
        self.assertFalse(os.path.exists(group_file))
        self.assertEqual(self.client.post('/users/delete_account/', {'password': 'password-123'}, format='json').status_code, 204)
        self.assertFalse(os.path.exists(avatar_file))
