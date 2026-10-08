import logging

from django.conf import settings
from django.core import signing
from django.core.mail import send_mail
from django.utils.html import escape

logger = logging.getLogger(__name__)

VERIFY_SALT = 'splitease-email-verify'
VERIFY_MAX_AGE = 3 * 24 * 3600


def public_base_url(request=None):
    """Where emailed links should point: PUBLIC_BASE_URL if set, otherwise the request's own origin."""
    if settings.PUBLIC_BASE_URL:
        return settings.PUBLIC_BASE_URL.rstrip('/')
    if request is not None:
        return request.build_absolute_uri('/').rstrip('/')
    return ''


def make_verify_token(user):
    return signing.dumps({'uid': user.pk, 'email': user.email}, salt=VERIFY_SALT)


def read_verify_token(token):
    """Returns the matching user, or None if the token is bad, expired, or the email has since changed."""
    from user.models import User
    try:
        data = signing.loads(token, salt=VERIFY_SALT, max_age=VERIFY_MAX_AGE)
    except signing.BadSignature:
        return None
    return User.objects.filter(pk=data.get('uid'), email=data.get('email')).first()


def send_email(to, subject, text, html=None):
    """Sends an email; returns False (and logs) instead of raising so a mail outage never breaks the API."""
    try:
        send_mail(subject, text, settings.DEFAULT_FROM_EMAIL, [to], html_message=html, fail_silently=False)
        return True
    except Exception:  # SMTP outages, bad credentials, DNS...
        logger.exception('Could not send "%s" to %s', subject, to)
        return False


def send_verification_email(user, request=None):
    link = f'{public_base_url(request)}/users/verify_email/?token={make_verify_token(user)}'
    name = user.name or user.username
    text = (f'Hi {name},\n\nWelcome to SplitEase! Confirm your email address to finish creating your account:\n\n{link}\n\n'
            'The link works for 3 days. If you did not sign up, you can ignore this email.')
    html = (f'<p>Hi {escape(name)},</p><p>Welcome to SplitEase! Confirm your email address to finish creating your account:</p>'
            f'<p><a href="{escape(link)}" style="background:#0F766E;color:#fff;padding:12px 20px;border-radius:10px;text-decoration:none">'
            'Verify email</a></p><p>Or open this link: ' + escape(link) + '</p><p>The link works for 3 days. '
            'If you did not sign up, you can ignore this email.</p>')
    return send_email(user.email, 'Verify your SplitEase email', text, html)


RESET_SALT = 'splitease-password-reset'
RESET_MAX_AGE = 3600


def make_reset_token(user):
    # The password hash is part of the payload, so the link stops working once the password changes.
    return signing.dumps({'uid': user.pk, 'pw': user.password[-16:]}, salt=RESET_SALT)


def read_reset_token(token):
    from user.models import User
    try:
        data = signing.loads(token, salt=RESET_SALT, max_age=RESET_MAX_AGE)
    except signing.BadSignature:
        return None
    user = User.objects.filter(pk=data.get('uid')).first()
    return user if user and user.password[-16:] == data.get('pw') else None


def send_reset_email(user, request=None):
    link = f'{public_base_url(request)}/users/reset_password/?token={make_reset_token(user)}'
    name = user.name or user.username
    text = (f'Hi {name},\n\nSomeone asked to reset the password for your SplitEase account. Choose a new one here:\n\n{link}\n\n'
            'The link works for 1 hour and only once. If this was not you, ignore this email: your password stays the same.')
    html = (f'<p>Hi {escape(name)},</p><p>Someone asked to reset the password for your SplitEase account.</p>'
            f'<p><a href="{escape(link)}" style="background:#0F766E;color:#fff;padding:12px 20px;border-radius:10px;text-decoration:none">'
            'Choose a new password</a></p><p>The link works for 1 hour and only once. If this was not you, ignore this email: '
            'your password stays the same.</p>')
    return send_email(user.email, 'Reset your SplitEase password', text, html)


def send_invite_email(invite, request=None):
    link = f'{public_base_url(request)}/invite/{invite.token}/'
    who = invite.inviter.name or invite.inviter.username
    text = (f'{who} invited you to join "{invite.group.name}" on SplitEase, the app for sharing expenses with friends.\n\n'
            f'Open this link on your phone to join:\n{link}\n\n'
            f'If you are new, you will be asked to create an account with this email address ({invite.email}). '
            f'The invitation works for {settings.INVITE_MAX_DAYS} days.')
    html = (f'<p><b>{escape(who)}</b> invited you to join <b>{escape(invite.group.name)}</b> on SplitEase.</p>'
            f'<p><a href="{escape(link)}" style="background:#0F766E;color:#fff;padding:12px 20px;border-radius:10px;text-decoration:none">'
            'Open invitation</a></p>'
            f'<p>If you are new, create your account with this email address ({escape(invite.email)}). '
            f'The invitation works for {settings.INVITE_MAX_DAYS} days.</p><p>Or open: {escape(link)}</p>')
    return send_email(invite.email, f'{who} invited you to {invite.group.name} on SplitEase', text, html)


def mask_email(email):
    name, _, domain = email.partition('@')
    return (name[:2] + '***' if len(name) > 2 else name[:1] + '***') + '@' + domain
