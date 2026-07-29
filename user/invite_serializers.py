from rest_framework import serializers

from core.models import UserGroup
from user.models import GroupInvite, User


class UserSearchSerializer(serializers.ModelSerializer):
    class Meta:
        model = User
        fields = ('id', 'username', 'name', 'email')


class GroupInviteSerializer(serializers.ModelSerializer):
    class Meta:
        model = GroupInvite
        fields = ('id', 'group', 'inviter', 'invitee', 'status', 'created_at')
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
