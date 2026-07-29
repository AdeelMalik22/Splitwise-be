from rest_framework import serializers

from core.models import Group, UserGroup, Expense


class GroupSerializer(serializers.ModelSerializer):
    class Meta:
        model = Group
        fields = '__all__'

class UserGroupSerializer(serializers.ModelSerializer):
    class Meta:
        model = UserGroup
        fields = '__all__'
    def validate(self, attrs):
        request = self.context.get('request')
        if request and attrs.get('user_id') and attrs['user_id'] != request.user:
            raise serializers.ValidationError({'user_id': 'You can only manage your own memberships.'})
        return attrs


class ExpenseSerializer(serializers.ModelSerializer):
    class Meta:
        model = Expense
        fields = '__all__'

    def validate(self, attrs):
        request = self.context.get('request')
        group = attrs.get('group_id', self.instance.group_id if self.instance else None)
        if request and group and not UserGroup.objects.filter(
            user=request.user, group_id=group
        ).exists():
            raise serializers.ValidationError({'group_id': 'You are not a member of this group.'})

        member_ids = set(UserGroup.objects.filter(group_id=group).values_list('user_id', flat=True))
        for field in ('paid_by', 'split_on'):
            participants = attrs.get(field, getattr(self.instance, field, None)) or []
            if not participants:
                raise serializers.ValidationError({field: 'At least one participant is required.'})
            if len(participants) != len(set(participants)):
                raise serializers.ValidationError({field: 'Participants must be unique.'})
            if not set(participants).issubset(member_ids):
                raise serializers.ValidationError({field: 'All participants must belong to the group.'})
        return attrs
