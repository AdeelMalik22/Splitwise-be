import re

from django.core import mail
from django.core.cache import cache
from django.test import override_settings
from rest_framework import status
from rest_framework.test import APITestCase

from user.models import User


@override_settings(EMAIL_BACKEND='django.core.mail.backends.locmem.EmailBackend', PUBLIC_BASE_URL='http://testserver')
class PasswordResetTests(APITestCase):
    def setUp(self):
        cache.clear()
        self.user = User.objects.create_user(username='sam', name='Sam', email='sam@example.com', password='old-password-1',
                                             email_verified=False)

    def request_reset(self, identifier='sam@example.com'):
        return self.client.post('/users/forgot_password/', {'identifier': identifier}, format='json')

    def link(self):
        return re.search(r'(/users/reset_password/\?token=\S+)', mail.outbox[-1].body).group(1)

    def login(self, password):
        return self.client.post('/login/', {'username': 'sam', 'password': password}, format='json').status_code

    def test_existing_and_unknown_accounts_get_the_same_answer_but_only_one_email(self):
        known = self.request_reset()
        unknown = self.request_reset('nobody@example.com')
        self.assertEqual((known.status_code, unknown.status_code), (200, 200))
        self.assertEqual(known.data, unknown.data)
        self.assertEqual(len(mail.outbox), 1)
        self.assertEqual(mail.outbox[0].to, ['sam@example.com'])

    def test_username_works_too(self):
        self.request_reset('sam')
        self.assertEqual(len(mail.outbox), 1)

    def test_full_reset_flow_and_it_also_verifies_the_email(self):
        self.request_reset()
        link = self.link()
        form = self.client.get(link)
        self.assertEqual(form.status_code, 200)
        self.assertIn('name="password"', form.content.decode())

        token = link.split('token=')[1]
        done = self.client.post('/users/reset_password/', {'token': token, 'password': 'brand-new-pass-9', 'confirm': 'brand-new-pass-9'})
        self.assertEqual(done.status_code, 200)
        self.assertIn('Password updated', done.content.decode())

        self.assertEqual(self.login('brand-new-pass-9'), status.HTTP_200_OK)  # reset also verified the account
        self.assertEqual(self.login('old-password-1'), status.HTTP_401_UNAUTHORIZED)

    def test_link_works_only_once(self):
        self.request_reset()
        token = self.link().split('token=')[1]
        data = {'token': token, 'password': 'brand-new-pass-9', 'confirm': 'brand-new-pass-9'}
        self.assertEqual(self.client.post('/users/reset_password/', data).status_code, 200)
        again = self.client.post('/users/reset_password/', {**data, 'password': 'another-pass-123', 'confirm': 'another-pass-123'})
        self.assertEqual(again.status_code, 400)
        self.assertEqual(self.login('brand-new-pass-9'), 200)

    def test_weak_or_mismatched_passwords_re_show_the_form_and_change_nothing(self):
        self.request_reset()
        token = self.link().split('token=')[1]
        for password, confirm in (('12345678', '12345678'), ('good-password-1', 'different-1')):
            response = self.client.post('/users/reset_password/', {'token': token, 'password': password, 'confirm': confirm})
            self.assertEqual(response.status_code, 400)
            self.assertIn('name="password"', response.content.decode())
        self.user.refresh_from_db()
        self.assertTrue(self.user.check_password('old-password-1'))

    def test_garbage_tokens_are_rejected(self):
        self.assertEqual(self.client.get('/users/reset_password/', {'token': 'nope'}).status_code, 400)
        self.assertEqual(self.client.post('/users/reset_password/', {'token': 'nope', 'password': 'x' * 10, 'confirm': 'x' * 10}).status_code, 400)

    def test_forgot_password_is_rate_limited(self):
        statuses = [self.request_reset('x@example.com').status_code for _ in range(12)]
        self.assertIn(429, statuses)
