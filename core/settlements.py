from collections import defaultdict
from decimal import Decimal, ROUND_HALF_UP

from user.models import User


def get_username(uid):
    user = User.objects.filter(id=uid).first()
    return user.username if user else f"User {uid}"


def get_settlements_for_group(expenses, user_id):
    balance = defaultdict(Decimal)

    for expense in expenses:
        total_amount = expense.get("amount")
        payers = expense.get("paid_by") or []
        splitters = expense.get("split_on") or []

        if not payers or not splitters:
            continue

        num_payers = len(payers)
        num_splitters = len(splitters)

        owed_share = Decimal(str(total_amount)) / num_splitters

        for splitter in splitters:
            for payer in payers:
                if splitter == payer:
                    continue
                split_amount = owed_share / num_payers

                if splitter == user_id:
                    balance[payer] -= split_amount  # user owes others
                elif payer == user_id:
                    balance[splitter] += split_amount  # others owe user

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
