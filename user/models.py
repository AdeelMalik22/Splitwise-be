import secrets
from datetime import timedelta

from django.conf import settings
from django.contrib.auth.models import AbstractUser
from django.db import models
from django.utils import timezone

from core.images import RandomName

# Create your models here.


class User(AbstractUser):
    name = models.CharField(blank=True, max_length=255)
    username = models.CharField(max_length=30, unique=True)
    email = models.EmailField(unique=True)
    password = models.CharField(max_length=255)
    email_verified = models.BooleanField(default=False)
    avatar = models.ImageField(upload_to=RandomName('avatars'), null=True, blank=True)

    def save(self, *args, **kwargs):
        if self.is_superuser:
            self.email_verified = True  # admins created on the command line have no mailbox step
        super().save(*args, **kwargs)


class GroupInvite(models.Model):
    PENDING = 'pending'
    ACCEPTED = 'accepted'
    DECLINED = 'declined'
    STATUS_CHOICES = ((PENDING, 'Pending'), (ACCEPTED, 'Accepted'), (DECLINED, 'Declined'))
    group = models.ForeignKey('core.Group', on_delete=models.CASCADE, related_name='invites')
    inviter = models.ForeignKey(User, on_delete=models.CASCADE, related_name='sent_invites')
    # Username invites set `invitee`; email invites set `email` (and `invitee` too when that address already has an account).
    invitee = models.ForeignKey(User, null=True, blank=True, on_delete=models.CASCADE, related_name='received_invites')
    email = models.EmailField(blank=True, default='')
    token = models.CharField(max_length=64, unique=True, null=True, blank=True, default=None)
    status = models.CharField(max_length=10, choices=STATUS_CHOICES, default=PENDING)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        constraints = [
            models.UniqueConstraint(fields=('group', 'invitee'), condition=models.Q(status='pending'),
                                    name='unique_pending_group_invite'),
            models.UniqueConstraint(fields=('group', 'email'), condition=models.Q(status='pending') & ~models.Q(email=''),
                                    name='unique_pending_group_email_invite'),
        ]

    def save(self, *args, **kwargs):
        if self.email:
            self.email = self.email.strip().lower()
            if not self.token:
                self.token = secrets.token_urlsafe(24)
        super().save(*args, **kwargs)

    @property
    def expired(self):
        return timezone.now() - self.created_at > timedelta(days=settings.INVITE_MAX_DAYS)
