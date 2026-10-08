from rest_framework import serializers

from decimal import Decimal

from core.models import Payment, UserGroup
from core.settlements import group_expense_data, net_balances, payment_rows


class PaymentSerializer(serializers.ModelSerializer):
    group_name = serializers.CharField(source='group.name', read_only=True)
    payer_username = serializers.CharField(source='payer.username', read_only=True)
    payee_username = serializers.CharField(source='payee.username', read_only=True)

    class Meta:
        model = Payment
        fields = ('id', 'group', 'group_name', 'expense', 'payer', 'payer_username',
                  'payee', 'payee_username', 'amount', 'status', 'created_at', 'completed_at')
        # The payer is always the signed-in user; status changes only through /confirm/.
        read_only_fields = ('id', 'payer', 'status', 'created_at', 'completed_at')

    def validate(self, attrs):
        request = self.context['request']
        group = attrs['group']
        payee = attrs['payee']
        if not UserGroup.objects.filter(user_id=request.user, group_id=group).exists():
            raise serializers.ValidationError({'group': 'You are not a member of this group.'})
        if payee == request.user:
            raise serializers.ValidationError({'payee': 'You cannot pay yourself.'})
        if not UserGroup.objects.filter(user_id=payee, group_id=group).exists():
            raise serializers.ValidationError({'payee': 'The payee must belong to the group.'})
        expense = attrs.get('expense')
        if expense is not None and expense.group_id_id != group.pk:
            raise serializers.ValidationError({'expense': 'The expense must belong to the group.'})
        if attrs['amount'] <= 0:
            raise serializers.ValidationError({'amount': 'Amount must be greater than zero.'})
        owed = self._owed(request.user, payee, group)
        if attrs['amount'] > owed:
            raise serializers.ValidationError({'amount': f'You only owe {payee.username} Rs {owed} in this group.'})
        return attrs

    @staticmethod
    def _owed(payer, payee, group):
        """What `payer` still owes `payee`, counting confirmed payments and payments awaiting confirmation."""
        payments = payment_rows(group.pk, [Payment.COMPLETED, Payment.PENDING])
        balance = net_balances(group_expense_data(group.pk), payer.pk, payments)
        return max(-balance.get(payee.pk, Decimal('0')), Decimal('0')).quantize(Decimal('0.01'))
