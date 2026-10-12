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
        paid_details = expense.get("paid_details") or []
        splitters = expense.get("split_on") or []

        if not payers or not splitters:
            continue

        num_splitters = len(splitters)
        paid_amounts = {item.get('user_id'): Decimal(str(item.get('amount', 0))) for item in paid_details}
        paid_total = sum(paid_amounts.values(), Decimal('0'))

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
                contribution = (paid_amounts.get(payer, Decimal(str(total_amount)) / len(payers))
                                if paid_total else Decimal(str(total_amount)) / len(payers))
                split_amount = owed_share_for(splitter) * contribution / Decimal(str(total_amount))

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


def minimize_settlements(expenses, payments=()):
    """Return a small set of transfers that settles the group's net balances."""
    users = sorted({uid for e in expenses for uid in (e.get('paid_by') or []) + (e.get('split_on') or [])})
    nets = {uid: Decimal('0') for uid in users}
    for expense in expenses:
        total = Decimal(str(expense['amount']))
        payers = expense.get('paid_by') or []
        paid = {item.get('user_id'): Decimal(str(item.get('amount', 0))) for item in expense.get('paid_details', [])}
        if not paid:
            paid = {uid: total / len(payers) for uid in payers}
        for uid, value in paid.items(): nets[uid] += value
        splitters = expense.get('split_on') or []
        details = expense.get('split_details') or []
        def detail_value(item, name, fallback=None):
            return getattr(item, name, item.get(name, fallback) if isinstance(item, dict) else fallback)
        shares = {detail_value(item, 'user_id'): Decimal(str(detail_value(item, 'share_amount', detail_value(item, 'amount', 0)) or 0)) for item in details}
        for uid in splitters: nets[uid] -= shares.get(uid, total / len(splitters))
    for payment in payments:
        amount = Decimal(str(payment['amount']))
        nets[payment['payer']] += amount; nets[payment['payee']] -= amount
    creditors = [[uid, amount.quantize(Decimal('0.01'))] for uid, amount in nets.items() if amount > 0]
    debtors = [[uid, (-amount).quantize(Decimal('0.01'))] for uid, amount in nets.items() if amount < 0]
    transfers = []
    while debtors and creditors:
        debtor, owed = debtors[0]
        creditor, credit = creditors[0]
        amount = min(owed, credit).quantize(Decimal('0.01'))
        transfers.append({'from_user': debtor, 'to_user': creditor, 'amount': amount})
        debtors[0][1] -= amount; creditors[0][1] -= amount
        if debtors[0][1] <= 0: debtors.pop(0)
        if creditors[0][1] <= 0: creditors.pop(0)
    return transfers


def group_expense_data(group_id):
    """Expenses of a group in the shape the settlement maths expects."""
    from core.models import Expense, ExpenseParticipant

    data = []
    for expense in Expense.objects.filter(group_id=group_id).prefetch_related('participants'):
        participants = expense.participants.all()
        data.append({
            'amount': expense.amount,
            'paid_by': [p.user_id for p in participants if p.role == ExpenseParticipant.PAID],
            'paid_details': [{'user_id': p.user_id, 'amount': p.share_amount}
                             for p in participants if p.role == ExpenseParticipant.PAID and p.share_amount is not None],
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
