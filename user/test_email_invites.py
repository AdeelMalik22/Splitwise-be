import re

from django.core import mail
from django.core.cache import cache
from django.test import override_settings
from rest_framework import status
from rest_framework.test import APITestCase

from core.models import Activity, Group, UserGroup
from user.models import GroupInvite, User


@override_settings(EMAIL_BACKEND='django.core.mail.backends.locmem.EmailBackend', PUBLIC_BASE_URL='http://testserver')
class EmailInviteTests(APITestCase):
    def setUp(self):
        cache.clear()
        self.owner = User.objects.create_user(username='owner', name='Olive', email='o@example.com', password='password-123', email_verified=True)
        self.group = Group.objects.create(name='Trip', created_by=self.owner)
        UserGroup.objects.create(user_id=self.owner, group_id=self.group)
        self.client.force_authenticate(self.owner)

    def invite(self, email='friend@example.com'):
        return self.client.post('/invites/', {'group': self.group.pk, 'email': email}, format='json')

    def token(self):
        return re.search(r'/invite/(\S+?)/', mail.outbox[-1].body).group(1)

    def test_inviting_an_email_sends_a_link_and_records_it(self):
        response = self.invite('Friend@Example.com')
        self.assertEqual(response.status_code, status.HTTP_201_CREATED)
        self.assertEqual(response.data['email'], 'friend@example.com')
        self.assertTrue(response.data['email_sent'])
        self.assertEqual(mail.outbox[0].to, ['friend@example.com'])
        self.assertIn('Olive invited you to Trip', mail.outbox[0].subject)
        self.assertRegex(mail.outbox[0].body, r'http://testserver/invite/\S+/')
        self.assertTrue(Activity.objects.filter(action='invited', group_id=self.group.pk).exists())

    def test_must_give_exactly_one_of_user_or_email_and_be_a_member(self):
        other = User.objects.create_user(username='x', email='x@example.com', password='password-123')
        self.assertEqual(self.client.post('/invites/', {'group': self.group.pk}, format='json').status_code, 400)
        self.assertEqual(self.client.post('/invites/', {'group': self.group.pk, 'invitee': other.pk, 'email': 'a@b.com'}, format='json').status_code, 400)
        self.client.force_authenticate(other)
        self.assertEqual(self.invite().status_code, status.HTTP_400_BAD_REQUEST)  # not a member

    def test_duplicate_and_existing_member_invites_are_rejected(self):
        self.assertEqual(self.invite().status_code, 201)
        self.assertEqual(self.invite().status_code, 400)
        self.assertEqual(self.invite('o@example.com').status_code, 400)  # yourself

    def test_existing_account_is_linked_so_it_shows_in_their_invites(self):
        friend = User.objects.create_user(username='friend', email='friend@example.com', password='password-123', email_verified=True)
        self.assertEqual(self.invite().data['invitee'], friend.pk)

    def test_public_landing_page_and_lookup(self):
        self.invite()
        token = self.token()
        page = self.client.get(f'/invite/{token}/')
        self.assertEqual(page.status_code, 200)
        self.assertIn('splitease://invite/' + token, page.content.decode())
        self.client.force_authenticate(None)
        info = self.client.get('/invites/lookup/', {'token': token})
        self.assertEqual(info.status_code, 200)
        self.assertEqual((info.data['group_name'], info.data['inviter_name']), ('Trip', 'Olive'))
        self.assertEqual(info.data['email_hint'], 'fr***@example.com')
        self.assertEqual(self.client.get('/invites/lookup/', {'token': 'nope'}).status_code, 404)
        self.assertEqual(self.client.get('/invite/nope/').status_code, 404)

    def test_only_the_invited_verified_email_can_join_with_the_token(self):
        self.invite()
        token = self.token()
        stranger = User.objects.create_user(username='stranger', email='stranger@example.com', password='password-123', email_verified=True)
        unverified = User.objects.create_user(username='unv', email='friend@example.com', password='password-123', email_verified=False)

        self.client.force_authenticate(stranger)
        self.assertEqual(self.client.post('/invites/accept_token/', {'token': token}, format='json').status_code, 403)
        self.client.force_authenticate(unverified)
        self.assertEqual(self.client.post('/invites/accept_token/', {'token': token}, format='json').status_code, 403)

        unverified.email_verified = True
        unverified.save()
        response = self.client.post('/invites/accept_token/', {'token': token}, format='json')
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.data['group_name'], 'Trip')
        self.assertTrue(UserGroup.objects.filter(user_id=unverified, group_id=self.group).exists())
        self.assertEqual(GroupInvite.objects.get(token=token).status, GroupInvite.ACCEPTED)

    def test_token_is_single_use_and_expires(self):
        self.invite()
        token = self.token()
        friend = User.objects.create_user(username='friend', email='friend@example.com', password='password-123', email_verified=True)
        self.client.force_authenticate(friend)
        self.assertEqual(self.client.post('/invites/accept_token/', {'token': token}, format='json').status_code, 200)
        self.assertEqual(self.client.post('/invites/accept_token/', {'token': token}, format='json').status_code, 410)

        self.client.force_authenticate(self.owner)
        self.invite('late@example.com')
        late = self.token()
        from datetime import timedelta
        from django.utils import timezone
        GroupInvite.objects.filter(token=late).update(created_at=timezone.now() - timedelta(days=30))
        self.assertEqual(self.client.get('/invites/lookup/', {'token': late}).status_code, 410)

    def test_username_invites_still_work_and_need_no_email(self):
        friend = User.objects.create_user(username='friend', email='friend@example.com', password='password-123')
        response = self.client.post('/invites/', {'group': self.group.pk, 'invitee': friend.pk}, format='json')
        self.assertEqual(response.status_code, 201)
        self.assertEqual(len(mail.outbox), 0)
        self.client.force_authenticate(friend)
        self.assertEqual(self.client.post(f"/invites/{response.data['id']}/accept/").status_code, 200)

    def test_invite_emails_are_rate_limited_per_user(self):
        codes = [self.invite(f'p{i}@example.com').status_code for i in range(33)]
        self.assertEqual(codes.count(201), 30)
        self.assertEqual(codes[-1], 429)
