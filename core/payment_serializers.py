from rest_framework import serializers

from core.models import Payment, UserGroup


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
        return attrs
