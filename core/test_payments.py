from decimal import Decimal

from django.test import SimpleTestCase
from rest_framework import status
from rest_framework.test import APITestCase

from core.models import Expense, ExpenseParticipant, Group, Notification, Payment, UserGroup
from core.settlements import get_settlements_for_group
from user.models import User


class SettlementWithPaymentsTests(SimpleTestCase):
    def setUp(self):
        from unittest.mock import patch
        patcher = patch('core.settlements.get_username', side_effect=lambda uid: f'user-{uid}')
        patcher.start()
        self.addCleanup(patcher.stop)
        self.expenses = [{'amount': 100, 'paid_by': [1], 'split_on': [1, 2]}]

    def test_confirmed_payment_reduces_what_the_debtor_owes(self):
        payments = [{'payer': 2, 'payee': 1, 'amount': Decimal('30.00')}]
        result = get_settlements_for_group(self.expenses, 2, payments)
        self.assertEqual(result['You need to pay'], [{'to_user': 'user-1', 'amount': Decimal('20.00')}])

    def test_full_payment_clears_the_balance_for_both_sides(self):
        payments = [{'payer': 2, 'payee': 1, 'amount': Decimal('50.00')}]
        for uid in (1, 2):
            result = get_settlements_for_group(self.expenses, uid, payments)
            self.assertEqual(result, {'You need to pay': [], 'you will get': []})


class PaymentApiTests(APITestCase):
    def setUp(self):
        self.alice = User.objects.create_user(username='alice', email='a@example.com', password='password-123')
        self.bob = User.objects.create_user(username='bob', email='b@example.com', password='password-123')
        self.eve = User.objects.create_user(username='eve', email='e@example.com', password='password-123')
        self.group = Group.objects.create(name='Trip', description='Trip')
        for u in (self.alice, self.bob):
            UserGroup.objects.create(user_id=u, group_id=self.group)
        expense = Expense.objects.create(name='Dinner', description='', amount='100.00', group_id=self.group)
        ExpenseParticipant.objects.create(expense=expense, user=self.alice, role=ExpenseParticipant.PAID)
        for u in (self.alice, self.bob):
            ExpenseParticipant.objects.create(expense=expense, user=u, role=ExpenseParticipant.SPLIT)

    def pay(self, amount='50.00', payee=None):
        self.client.force_authenticate(self.bob)
        return self.client.post('/payments/', {
            'group': self.group.pk, 'payee': (payee or self.alice).pk, 'amount': amount,
        }, format='json')

    def test_payer_is_the_signed_in_user_and_payee_is_notified(self):
        response = self.pay()
        self.assertEqual(response.status_code, status.HTTP_201_CREATED)
        self.assertEqual(response.data['payer'], self.bob.pk)
        self.assertEqual(response.data['status'], 'pending')
        self.assertTrue(Notification.objects.filter(recipient=self.alice, notification_type='payment').exists())

    def test_cannot_pay_non_members_or_yourself(self):
        self.assertEqual(self.pay(payee=self.eve).status_code, status.HTTP_400_BAD_REQUEST)
        self.assertEqual(self.pay(payee=self.bob).status_code, status.HTTP_400_BAD_REQUEST)

    def test_only_payee_confirms_and_confirmation_settles_the_balance(self):
        payment_id = self.pay().data['id']

        self.assertEqual(self.client.post(f'/payments/{payment_id}/confirm/').status_code, status.HTTP_403_FORBIDDEN)

        self.client.force_authenticate(self.alice)
        self.assertEqual(self.client.get(f'/expense/{self.group.pk}/settlements/').data['you will get'][0]['amount'], Decimal('50.00'))
        self.assertEqual(self.client.post(f'/payments/{payment_id}/confirm/').status_code, status.HTTP_200_OK)
        self.assertEqual(self.client.get(f'/expense/{self.group.pk}/settlements/').data['you will get'], [])

    def test_pending_payment_does_not_change_balances(self):
        self.pay()
        self.client.force_authenticate(self.alice)
        self.assertEqual(len(self.client.get(f'/expense/{self.group.pk}/settlements/').data['you will get']), 1)

    def test_payer_can_cancel_pending_but_not_completed(self):
        payment_id = self.pay().data['id']
        self.client.force_authenticate(self.alice)
        self.assertEqual(self.client.delete(f'/payments/{payment_id}/').status_code, status.HTTP_403_FORBIDDEN)
        self.client.force_authenticate(self.bob)
        self.assertEqual(self.client.delete(f'/payments/{payment_id}/').status_code, status.HTTP_204_NO_CONTENT)

        payment_id = self.pay().data['id']
        Payment.objects.filter(pk=payment_id).update(status=Payment.COMPLETED)
        self.assertEqual(self.client.delete(f'/payments/{payment_id}/').status_code, status.HTTP_400_BAD_REQUEST)

    def test_outsiders_cannot_see_or_edit_payments(self):
        payment_id = self.pay().data['id']
        self.client.force_authenticate(self.eve)
        self.assertEqual(self.client.get('/payments/').data['results'], [])
        self.assertEqual(self.client.post(f'/payments/{payment_id}/confirm/').status_code, status.HTTP_404_NOT_FOUND)
        self.assertEqual(self.client.patch(f'/payments/{payment_id}/', {'amount': '1.00'}, format='json').status_code,
                         status.HTTP_405_METHOD_NOT_ALLOWED)
