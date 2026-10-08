from rest_framework import serializers

from core.models import UserGroup
from user.models import GroupInvite, User


class UserSearchSerializer(serializers.ModelSerializer):
    class Meta:
        model = User
        fields = ('id', 'username', 'name', 'email')


class GroupInviteSerializer(serializers.ModelSerializer):
    group_name = serializers.CharField(source='group.name', read_only=True)
    inviter_username = serializers.CharField(source='inviter.username', read_only=True)
    inviter_name = serializers.CharField(source='inviter.name', read_only=True)
    invitee_username = serializers.CharField(source='invitee.username', read_only=True)
    invitee = serializers.PrimaryKeyRelatedField(queryset=User.objects.all(), required=False, allow_null=True)
    email = serializers.EmailField(required=False, allow_blank=True)

    class Meta:
        model = GroupInvite
        fields = ('id', 'group', 'group_name', 'inviter', 'inviter_username', 'inviter_name',
                  'invitee', 'invitee_username', 'email', 'status', 'created_at')
        read_only_fields = ('id', 'inviter', 'status', 'created_at')
        # Duplicate invites are checked in validate(); DRF's generated unique-together check would demand `invitee` for email invites.
        validators = []

    def validate(self, attrs):
        request = self.context['request']
        group = attrs['group']
        invitee = attrs.get('invitee')
        email = (attrs.get('email') or '').strip().lower()
        if bool(invitee) == bool(email):
            raise serializers.ValidationError('Invite either an existing user or an email address, not both.')
        if not UserGroup.objects.filter(user_id=request.user, group_id=group).exists():
            raise serializers.ValidationError({'group': 'You must belong to this group to invite users.'})
        if email:
            existing = User.objects.filter(email__iexact=email).first()
            if existing is not None:
                invitee = attrs['invitee'] = existing  # they already have an account: it shows up in their Invites too
            attrs['email'] = email
            if GroupInvite.objects.filter(group=group, email=email, status=GroupInvite.PENDING).exists():
                raise serializers.ValidationError({'email': 'This address has already been invited.'})
        if invitee is not None and (invitee == request.user or UserGroup.objects.filter(user_id=invitee, group_id=group).exists()):
            raise serializers.ValidationError({'email' if email else 'invitee': 'This user is already a group member.'})
        if invitee is not None and not email and GroupInvite.objects.filter(group=group, invitee=invitee, status=GroupInvite.PENDING).exists():
            raise serializers.ValidationError({'invitee': 'This user has already been invited.'})
        return attrs
