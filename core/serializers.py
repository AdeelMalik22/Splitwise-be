from rest_framework import serializers

from core.models import Group, UserGroup, Expense, ExpenseParticipant


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
    paid_by = serializers.ListField(child=serializers.IntegerField(), write_only=True)
    split_on = serializers.ListField(child=serializers.IntegerField(), write_only=True)

    class Meta:
        model = Expense
        fields = ('id', 'name', 'description', 'amount', 'paid_by', 'split_on',
                  'group_id', 'created_at', 'updated_at')

    def to_representation(self, instance):
        data = super().to_representation(instance)
        participants = instance.participants.all()
        data['paid_by'] = [p.user_id for p in participants if p.role == ExpenseParticipant.PAID]
        data['split_on'] = [p.user_id for p in participants if p.role == ExpenseParticipant.SPLIT]
        return data

    def create(self, validated_data):
        paid_by = validated_data.pop('paid_by')
        split_on = validated_data.pop('split_on')
        expense = Expense.objects.create(**validated_data)
        ExpenseParticipant.objects.bulk_create(
            [ExpenseParticipant(expense=expense, user_id=user_id, role=role)
             for role, user_ids in ((ExpenseParticipant.PAID, paid_by),
                                    (ExpenseParticipant.SPLIT, split_on))
             for user_id in user_ids]
        )
        return expense

    def update(self, instance, validated_data):
        paid_by = validated_data.pop('paid_by', None)
        split_on = validated_data.pop('split_on', None)
        instance = super().update(instance, validated_data)
        for role, user_ids in ((ExpenseParticipant.PAID, paid_by),
                               (ExpenseParticipant.SPLIT, split_on)):
            if user_ids is not None:
                instance.participants.filter(role=role).delete()
                ExpenseParticipant.objects.bulk_create(
                    [ExpenseParticipant(expense=instance, user_id=user_id, role=role)
                     for user_id in user_ids]
                )
        return instance

    def validate(self, attrs):
        amount = attrs.get('amount', getattr(self.instance, 'amount', None))
        if amount is not None and amount <= 0:
            raise serializers.ValidationError({'amount': 'Amount must be greater than zero.'})
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
