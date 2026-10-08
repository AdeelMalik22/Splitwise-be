import re

from django.core import mail
from django.core.cache import cache
from django.test import override_settings
from rest_framework import status
from rest_framework.test import APITestCase

from user.emails import make_verify_token
from user.models import User

PAYLOAD = {'username': 'newbie', 'name': 'New Bie', 'email': 'newbie@example.com', 'password': 'strong-password-123'}


@override_settings(EMAIL_BACKEND='django.core.mail.backends.locmem.EmailBackend', PUBLIC_BASE_URL='http://testserver')
class EmailVerificationTests(APITestCase):
    def setUp(self):
        cache.clear()

    def register(self):
        return self.client.post('/users/register/', PAYLOAD, format='json')

    def login(self):
        return self.client.post('/login/', {'username': 'newbie', 'password': PAYLOAD['password']}, format='json')

    def test_registration_sends_a_verification_email_with_a_link(self):
        response = self.register()
        self.assertEqual(response.status_code, status.HTTP_201_CREATED)
        self.assertTrue(response.data['email_sent'])
        self.assertFalse(response.data['email_verified'])
        self.assertEqual(len(mail.outbox), 1)
        self.assertEqual(mail.outbox[0].to, ['newbie@example.com'])
        self.assertRegex(mail.outbox[0].body, r'http://testserver/users/verify_email/\?token=')

    def test_login_is_blocked_until_the_email_is_verified(self):
        self.register()
        response = self.login()
        self.assertEqual(response.status_code, status.HTTP_403_FORBIDDEN)
        self.assertEqual(response.data['code'], 'email_not_verified')

        link = re.search(r'(/users/verify_email/\?token=\S+)', mail.outbox[0].body).group(1)
        page = self.client.get(link)
        self.assertEqual(page.status_code, 200)
        self.assertIn('Email verified', page.content.decode())
        self.assertIn('splitease://', page.content.decode())

        self.assertEqual(self.login().status_code, status.HTTP_200_OK)

    def test_wrong_password_is_still_a_401_not_a_verification_hint(self):
        self.register()
        response = self.client.post('/login/', {'username': 'newbie', 'password': 'wrong'}, format='json')
        self.assertEqual(response.status_code, status.HTTP_401_UNAUTHORIZED)

    def test_bad_or_tampered_tokens_are_rejected(self):
        self.register()
        user = User.objects.get(username='newbie')
        for token in ('garbage', make_verify_token(user) + 'x'):
            self.assertEqual(self.client.get('/users/verify_email/', {'token': token}).status_code, 400)
        user.refresh_from_db()
        self.assertFalse(user.email_verified)

    def test_token_stops_working_if_the_email_changed(self):
        self.register()
        user = User.objects.get(username='newbie')
        token = make_verify_token(user)
        User.objects.filter(pk=user.pk).update(email='other@example.com')
        self.assertEqual(self.client.get('/users/verify_email/', {'token': token}).status_code, 400)

    def test_resend_sends_only_for_unverified_accounts_and_never_reveals_which(self):
        self.register()
        mail.outbox.clear()
        self.assertEqual(self.client.post('/users/resend_verification/', {'identifier': 'newbie@example.com'}, format='json').status_code, 200)
        self.assertEqual(len(mail.outbox), 1)
        unknown = self.client.post('/users/resend_verification/', {'identifier': 'nobody@example.com'}, format='json')
        self.assertEqual(unknown.status_code, 200)
        self.assertEqual(len(mail.outbox), 1)

        User.objects.filter(username='newbie').update(email_verified=True)
        self.client.post('/users/resend_verification/', {'identifier': 'newbie'}, format='json')
        self.assertEqual(len(mail.outbox), 1)

    def test_resend_is_rate_limited(self):
        statuses = [self.client.post('/users/resend_verification/', {'identifier': 'x@example.com'}, format='json').status_code for _ in range(12)]
        self.assertIn(429, statuses)

    def test_a_broken_mail_server_does_not_break_registration(self):
        with override_settings(EMAIL_BACKEND='django.core.mail.backends.smtp.EmailBackend', EMAIL_HOST='127.0.0.1', EMAIL_PORT=1), \
                self.assertLogs('user.emails', level='ERROR'):
            response = self.register()
        self.assertEqual(response.status_code, status.HTTP_201_CREATED)
        self.assertFalse(response.data['email_sent'])

    def test_existing_and_admin_accounts_are_verified(self):
        admin = User.objects.create_superuser(username='boss', email='boss@example.com', password='password-123')
        self.assertTrue(admin.email_verified)
