from decimal import Decimal

from django.core.management.base import BaseCommand
from django.db import transaction
from django.utils import timezone

from core.models import Activity, Expense, ExpenseParticipant, Group, Notification, Payment, UserGroup
from user.models import GroupInvite, User


PASSWORD = 'SplitwiseDemo!2026'
USERS = [
    ('umer', 'Umer Farooq', 'umer@example.com'),
    ('adeel', 'Adeel Malik', 'adeel@example.com'),
    ('saim', 'Saim Ahmed', 'saim@example.com'),
    ('fatima', 'Fatima Khan', 'fatima@example.com'),
    ('hamza', 'Hamza Ali', 'hamza@example.com'),
    ('ayesha', 'Ayesha Siddiqui', 'ayesha@example.com'),
    ('bilal', 'Bilal Hussain', 'bilal@example.com'),
    ('zainab', 'Zainab Iqbal', 'zainab@example.com'),
    ('usman', 'Usman Raza', 'usman@example.com'),
    ('hina', 'Hina Tariq', 'hina@example.com'),
    ('danish', 'Danish Nawaz', 'danish@example.com'),
    ('sara', 'Sara Khan', 'sara@example.com'),
    ('faizan', 'Faizan Sheikh', 'faizan@example.com'),
    ('mariam', 'Mariam Aslam', 'mariam@example.com'),
    ('ahmed', 'Ahmed Rauf', 'ahmed@example.com'),
    ('noor', 'Noor Fatima', 'noor@example.com'),
    ('talha', 'Talha Javed', 'talha@example.com'),
    ('iqra', 'Iqra Yousaf', 'iqra@example.com'),
    ('shahzaib', 'Shahzaib Khan', 'shahzaib@example.com'),
    ('laiba', 'Laiba Mehmood', 'laiba@example.com'),
]
GROUPS = {
    'Meal Management': [0, 1, 2, 3, 4],
    'Murree Trip': [0, 1, 2, 5, 6],
    'Office Cricket': [1, 2, 7, 8, 9],
    'Family Budget': [0, 3, 10, 11, 12],
}


class Command(BaseCommand):
    help = 'Create realistic, repeatable demo data for frontend development.'

    def add_arguments(self, parser):
        parser.add_argument('--reset', action='store_true', help='Delete existing demo records first.')

    @transaction.atomic
    def handle(self, *args, **options):
        if options['reset']:
            usernames = [username for username, _, _ in USERS]
            User.objects.filter(username__in=usernames).delete()

        users = []
        for username, name, email in USERS:
            user, created = User.objects.get_or_create(username=username, defaults={'name': name, 'email': email})
            changed = False
            if not user.email:
                user.email = email
                changed = True
            if not user.name:
                user.name = name
                changed = True
            if created or not user.check_password(PASSWORD):
                user.set_password(PASSWORD)
                changed = True
            if changed:
                user.save()
            users.append(user)

        for group_name, indexes in GROUPS.items():
            group, _ = Group.objects.get_or_create(name=group_name, defaults={'description': f'{group_name} shared expenses'})
            members = [users[index] for index in indexes]
            for member in members:
                UserGroup.objects.get_or_create(user_id=member, group_id=group)

            expense_specs = [
                ('Breakfast groceries', Decimal('450.00'), members[0], members),
                ('Transport and supplies', Decimal('1200.00'), members[1], members),
            ]
            for name, amount, payer, splitters in expense_specs:
                expense, created = Expense.objects.get_or_create(
                    group_id=group, name=name,
                    defaults={'description': f'{name} for {group_name}', 'amount': amount},
                )
                if created:
                    for member in [payer]:
                        ExpenseParticipant.objects.create(expense=expense, user=member, role=ExpenseParticipant.PAID)
                    share = (amount / len(splitters)).quantize(Decimal('0.01'))
                    for member in splitters:
                        ExpenseParticipant.objects.create(expense=expense, user=member, role=ExpenseParticipant.SPLIT, share_amount=share)
                    Activity.objects.create(actor=payer, action='created', entity_type='expense', entity_id=expense.pk)

                payee = splitters[1]
                Payment.objects.get_or_create(
                    expense=expense, payer=payee, payee=payer,
                    defaults={'amount': (amount / len(splitters)).quantize(Decimal('0.01'))},
                )

            invitee = users[(GROUPS[group_name][0] + 13) % len(users)]
            if invitee not in members:
                invite, _ = GroupInvite.objects.get_or_create(group=group, invitee=invitee, status=GroupInvite.PENDING, defaults={'inviter': members[0]})
                Notification.objects.get_or_create(recipient=invitee, notification_type='group_invite', message=f'{members[0].name} invited you to {group.name}')

        self.stdout.write(self.style.SUCCESS(
            f'Demo data ready: {len(users)} users, {len(GROUPS)} groups. Password for every demo user: {PASSWORD}'
        ))
