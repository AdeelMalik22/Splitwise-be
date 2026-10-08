from collections import defaultdict
from decimal import Decimal, ROUND_HALF_UP

from user.models import User


def get_username(uid):
    user = User.objects.filter(id=uid).first()
    return user.username if user else f"User {uid}"


def net_balances(expenses, user_id, payments=()):
    """Per other member: positive = they owe `user_id`, negative = `user_id` owes them."""
    balance = defaultdict(Decimal)

    for expense in expenses:
        total_amount = expense.get("amount")
        payers = expense.get("paid_by") or []
        splitters = expense.get("split_on") or []

        if not payers or not splitters:
            continue

        num_payers = len(payers)
        num_splitters = len(splitters)

        details = expense.get('split_details') or []
        shares = {item.user_id: item.share_amount for item in details if item.share_amount is not None}
        percentages = {item.user_id: item.share_percentage for item in details if item.share_percentage is not None}
        if shares:
            owed_share_for = lambda uid: Decimal(str(shares.get(uid, 0)))
        elif percentages:
            owed_share_for = lambda uid: Decimal(str(total_amount)) * Decimal(str(percentages.get(uid, 0))) / 100
        else:
            owed_share_for = lambda uid: Decimal(str(total_amount)) / num_splitters

        for splitter in splitters:
            for payer in payers:
                if splitter == payer:
                    continue
                split_amount = owed_share_for(splitter) / num_payers

                if splitter == user_id:
                    balance[payer] -= split_amount  # user owes others
                elif payer == user_id:
                    balance[splitter] += split_amount  # others owe user

    # Confirmed payments settle debts: they move the balance back towards zero.
    for payment in payments:
        amount = Decimal(str(payment['amount']))
        if payment['payer'] == user_id:
            balance[payment['payee']] += amount
        elif payment['payee'] == user_id:
            balance[payment['payer']] -= amount

    return balance


def get_settlements_for_group(expenses, user_id, payments=()):
    balance = net_balances(expenses, user_id, payments)
    response = {
        "You need to pay": [],
        "you will get": []
    }

    for uid, net_amount in balance.items():
        rounded_amount = abs(net_amount).quantize(Decimal('0.01'), rounding=ROUND_HALF_UP)
        username = get_username(uid)

        if net_amount < 0:
            response["You need to pay"].append({
                "to_user": username,
                "amount": rounded_amount
            })
        elif net_amount > 0:
            response["you will get"].append({
                "from_user": username,
                "amount": rounded_amount
            })

    return response


def group_expense_data(group_id):
    """Expenses of a group in the shape the settlement maths expects."""
    from core.models import Expense, ExpenseParticipant

    data = []
    for expense in Expense.objects.filter(group_id=group_id).prefetch_related('participants'):
        participants = expense.participants.all()
        data.append({
            'amount': expense.amount,
            'paid_by': [p.user_id for p in participants if p.role == ExpenseParticipant.PAID],
            'split_on': [p.user_id for p in participants if p.role == ExpenseParticipant.SPLIT],
            'split_details': [p for p in participants if p.role == ExpenseParticipant.SPLIT],
        })
    return data


def payment_rows(group_id, statuses):
    from core.models import Payment

    return [
        {'payer': p.payer_id, 'payee': p.payee_id, 'amount': p.amount}
        for p in Payment.objects.filter(group_id=group_id, status__in=statuses)
    ]
