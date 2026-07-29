from rest_framework import serializers

from core.models import Expense, Payment, UserGroup


class PaymentSerializer(serializers.ModelSerializer):
    class Meta:
        model = Payment
        fields = ('id', 'expense', 'payer', 'payee', 'amount', 'status', 'created_at', 'completed_at')
        read_only_fields = ('id', 'status', 'created_at', 'completed_at')

    def validate(self, attrs):
        request = self.context['request']
        expense = attrs['expense']
        if not UserGroup.objects.filter(user_id=request.user, group_id=expense.group_id).exists():
            raise serializers.ValidationError({'expense': 'You are not a member of this group.'})
        if attrs['payer'] == attrs['payee']:
            raise serializers.ValidationError('Payer and payee must be different users.')
        member_ids = set(UserGroup.objects.filter(group_id=expense.group_id).values_list('user_id', flat=True))
        if attrs['payer'].pk not in member_ids or attrs['payee'].pk not in member_ids:
            raise serializers.ValidationError('Payer and payee must belong to the expense group.')
        if attrs['amount'] <= 0 or attrs['amount'] > expense.amount:
            raise serializers.ValidationError({'amount': 'Amount must be positive and no greater than the expense amount.'})
        return attrs
