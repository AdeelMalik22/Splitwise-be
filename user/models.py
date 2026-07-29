from django.contrib.auth.models import AbstractUser
from django.db import models

# Create your models here.


class User(AbstractUser):
    name = models.CharField(blank=True, max_length=255)
    username = models.CharField(max_length=30, unique=True)
    email = models.EmailField(unique=True)
    password = models.CharField(max_length=255)


class GroupInvite(models.Model):
    PENDING = 'pending'
    ACCEPTED = 'accepted'
    DECLINED = 'declined'
    STATUS_CHOICES = ((PENDING, 'Pending'), (ACCEPTED, 'Accepted'), (DECLINED, 'Declined'))
    group = models.ForeignKey('core.Group', on_delete=models.CASCADE, related_name='invites')
    inviter = models.ForeignKey(User, on_delete=models.CASCADE, related_name='sent_invites')
    invitee = models.ForeignKey(User, on_delete=models.CASCADE, related_name='received_invites')
    status = models.CharField(max_length=10, choices=STATUS_CHOICES, default=PENDING)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        constraints = [models.UniqueConstraint(
            fields=('group', 'invitee'), condition=models.Q(status='pending'),
            name='unique_pending_group_invite')]
