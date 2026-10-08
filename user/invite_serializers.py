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

    class Meta:
        model = GroupInvite
        fields = ('id', 'group', 'group_name', 'inviter', 'inviter_username', 'inviter_name',
                  'invitee', 'invitee_username', 'status', 'created_at')
        read_only_fields = ('id', 'inviter', 'status', 'created_at')

    def validate(self, attrs):
        request = self.context['request']
        group = attrs['group']
        invitee = attrs['invitee']
        if not UserGroup.objects.filter(user_id=request.user, group_id=group).exists():
            raise serializers.ValidationError({'group': 'You must belong to this group to invite users.'})
        if invitee == request.user or UserGroup.objects.filter(user_id=invitee, group_id=group).exists():
            raise serializers.ValidationError({'invitee': 'This user is already a group member.'})
        return attrs
