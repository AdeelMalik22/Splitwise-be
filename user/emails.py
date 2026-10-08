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
