from decimal import Decimal, InvalidOperation

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
    split_on = serializers.ListField(child=serializers.IntegerField(), write_only=True, required=False)
    split_details = serializers.ListField(child=serializers.DictField(), write_only=True, required=False)

    class Meta:
        model = Expense
        fields = ('id', 'name', 'description', 'amount', 'paid_by', 'split_on', 'split_details',
                  'group_id', 'created_at', 'updated_at')

    def to_representation(self, instance):
        data = super().to_representation(instance)
        participants = instance.participants.all()
        data['paid_by'] = [p.user_id for p in participants if p.role == ExpenseParticipant.PAID]
        data['split_on'] = [p.user_id for p in participants if p.role == ExpenseParticipant.SPLIT]
        data['split_details'] = [
            {'user_id': p.user_id, 'amount': p.share_amount, 'percentage': p.share_percentage}
            for p in participants if p.role == ExpenseParticipant.SPLIT
            and (p.share_amount is not None or p.share_percentage is not None)
        ]
        return data

    def create(self, validated_data):
        paid_by = validated_data.pop('paid_by')
        split_details = validated_data.pop('split_details', [])
        split_on = validated_data.pop('split_on', [item['user_id'] for item in split_details])
        expense = Expense.objects.create(**validated_data)
        ExpenseParticipant.objects.bulk_create(
            [ExpenseParticipant(expense=expense, user_id=user_id, role=role)
             for role, user_ids in ((ExpenseParticipant.PAID, paid_by),
                                    (ExpenseParticipant.SPLIT, split_on))
             for user_id in user_ids]
        )
        if split_details:
            expense.participants.filter(role=ExpenseParticipant.SPLIT).delete()
            ExpenseParticipant.objects.bulk_create([
                ExpenseParticipant(expense=expense, user_id=item['user_id'], role=ExpenseParticipant.SPLIT,
                                   share_amount=item.get('amount'), share_percentage=item.get('percentage'))
                for item in split_details
            ])
        return expense

    def update(self, instance, validated_data):
        paid_by = validated_data.pop('paid_by', None)
        split_on = validated_data.pop('split_on', None)
        split_details = validated_data.pop('split_details', None)
        instance = super().update(instance, validated_data)
        for role, user_ids in ((ExpenseParticipant.PAID, paid_by),
                               (ExpenseParticipant.SPLIT, split_on)):
            if user_ids is not None:
                instance.participants.filter(role=role).delete()
                ExpenseParticipant.objects.bulk_create(
                    [ExpenseParticipant(expense=instance, user_id=user_id, role=role)
                     for user_id in user_ids]
                )
        if split_details is not None:
            instance.participants.filter(role=ExpenseParticipant.SPLIT).delete()
            ExpenseParticipant.objects.bulk_create([
                ExpenseParticipant(expense=instance, user_id=item['user_id'], role=ExpenseParticipant.SPLIT,
                                   share_amount=item.get('amount'), share_percentage=item.get('percentage'))
                for item in split_details
            ])
        return instance

    def validate(self, attrs):
        amount = attrs.get('amount', getattr(self.instance, 'amount', None))
        if amount is not None and amount <= 0:
            raise serializers.ValidationError({'amount': 'Amount must be greater than zero.'})
        request = self.context.get('request')
        group = attrs.get('group_id', self.instance.group_id if self.instance else None)
        if request and group and not UserGroup.objects.filter(
            user_id=request.user, group_id=group
        ).exists():
            raise serializers.ValidationError({'group_id': 'You are not a member of this group.'})

        member_ids = set(UserGroup.objects.filter(group_id=group).values_list('user_id', flat=True))
        details = attrs.get('split_details', [])
        for field in ('paid_by', 'split_on'):
            default_participants = [item['user_id'] for item in details] if field == 'split_on' and details else getattr(self.instance, field, None)
            participants = attrs.get(field, default_participants) or []
            if not participants:
                raise serializers.ValidationError({field: 'At least one participant is required.'})
            if len(participants) != len(set(participants)):
                raise serializers.ValidationError({field: 'Participants must be unique.'})
            if not set(participants).issubset(member_ids):
                raise serializers.ValidationError({field: 'All participants must belong to the group.'})
        if details:
            try:
                for item in details:
                    if item.get('amount') is not None:
                        item['amount'] = Decimal(str(item['amount']))
                    if item.get('percentage') is not None:
                        item['percentage'] = Decimal(str(item['percentage']))
            except (InvalidOperation, ValueError):
                raise serializers.ValidationError({'split_details': 'Share values must be valid numbers.'})
            if len(details) < 1 or len({item.get('user_id') for item in details}) != len(details):
                raise serializers.ValidationError({'split_details': 'Users must be unique.'})
            total_amount = sum((item.get('amount') or Decimal('0')) for item in details)
            total_percentage = sum((item.get('percentage') or Decimal('0')) for item in details)
            if any(item.get('amount') is not None and item.get('percentage') is not None for item in details):
                raise serializers.ValidationError({'split_details': 'Use amount or percentage, not both.'})
            if total_amount and total_amount.quantize(Decimal('0.01')) != Decimal(str(amount)).quantize(Decimal('0.01')):
                raise serializers.ValidationError({'split_details': 'Split amounts must equal the expense amount.'})
            if total_percentage and total_percentage.quantize(Decimal('0.01')) != Decimal('100.00'):
                raise serializers.ValidationError({'split_details': 'Split percentages must total 100.'})
        return attrs
