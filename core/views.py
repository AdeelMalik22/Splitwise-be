from django.core.cache import cache
from django.db.models import Q

from rest_framework import viewsets, status
from rest_framework.decorators import action
from rest_framework.permissions import IsAuthenticated
from rest_framework.response import Response

from core.models import Group, UserGroup, Expense, ExpenseParticipant, Payment, Notification, Activity
from core.payment_serializers import PaymentSerializer
from core.notification_serializers import NotificationSerializer
from core.activity import log_activity
from core.activity_serializers import ActivitySerializer
from django.utils import timezone
from core.serializers import GroupSerializer, UserGroupSerializer, ExpenseSerializer
from core.settlements import get_settlements_for_group, group_expense_data, payment_rows
from user.models import User


# Create your views here.


class GroupViewSet(viewsets.ModelViewSet):
    """Any member may view and rename a group; only its creator may delete it or remove members."""
    queryset = Group.objects.all()
    serializer_class = GroupSerializer
    permission_classes = [IsAuthenticated]

    def get_queryset(self):
        return Group.objects.filter(usergroup__user_id=self.request.user.pk).order_by('id')

    def perform_create(self, serializer):
        group = serializer.save(created_by=self.request.user)
        UserGroup.objects.get_or_create(user_id=self.request.user, group_id=group)
        log_activity(self.request.user, 'created', 'group', group.pk, group=group)

    def perform_update(self, serializer):
        old_name = serializer.instance.name
        group = serializer.save()
        log_activity(self.request.user, 'updated', 'group', group.pk, group=group,
                     **({'old_name': old_name} if old_name != group.name else {}))

    def perform_destroy(self, instance):
        log_activity(self.request.user, 'deleted', 'group', instance.pk, group=instance)
        instance.delete()

    @staticmethod
    def _not_owner(group, user):
        return Response(
            {'detail': 'Only the group creator can do that.'}, status=status.HTTP_403_FORBIDDEN,
        ) if group.created_by_id != user.pk else None

    def destroy(self, request, *args, **kwargs):
        group = self.get_object()
        return self._not_owner(group, request.user) or super().destroy(request, *args, **kwargs)

    @action(detail=True, methods=['delete'], url_name='delete')
    def delete_group(self, request, pk=None):
        group = self.get_queryset().filter(pk=pk).first()
        if not group:
            return Response({'detail': 'Not found.'}, status=status.HTTP_404_NOT_FOUND)
        denied = self._not_owner(group, request.user)
        if denied:
            return denied
        self.perform_destroy(group)
        return Response({"detail": "Group and its associate records have been deleted."}, status=status.HTTP_204_NO_CONTENT)

    @action(detail=True, methods=['delete'], url_path=r'members/(?P<user_id>\d+)')
    def remove_member(self, request, pk=None, user_id=None):
        group = self.get_object()
        denied = self._not_owner(group, request.user)
        if denied:
            return denied
        if int(user_id) == group.created_by_id:
            return Response({'detail': 'The creator cannot be removed.'}, status=status.HTTP_400_BAD_REQUEST)
        target = User.objects.filter(pk=user_id).first()
        removed, _ = UserGroup.objects.filter(group_id=group, user_id=user_id).delete()
        if not removed:
            return Response({'detail': 'Not a member of this group.'}, status=status.HTTP_404_NOT_FOUND)
        log_activity(request.user, 'removed', 'member', user_id, group=group,
                     member=target.username if target else str(user_id))
        return Response(status=status.HTTP_204_NO_CONTENT)


class UserGroupViewSet(viewsets.ModelViewSet):
    queryset = UserGroup.objects.all()
    serializer_class = UserGroupSerializer
    permission_classes = [IsAuthenticated]

    def get_queryset(self):
        return UserGroup.objects.filter(user_id=self.request.user.pk)

    def perform_create(self, serializer):
        membership = serializer.save()
        log_activity(self.request.user, 'joined', 'group', membership.group_id_id, group=membership.group_id)

    def perform_destroy(self, instance):
        group = instance.group_id
        instance.delete()
        log_activity(self.request.user, 'left', 'group', group.pk, group=group)

    @action(detail=True, methods=['get'], url_path="users")
    def get_group_users(self, request, pk=None):
        if not self.get_queryset().filter(group_id=pk).exists():
            return Response({'detail': 'Not permitted.'}, status=status.HTTP_403_FORBIDDEN)
        user_groups = UserGroup.objects.filter(group_id=pk).values('user_id')

        if not user_groups.exists():
            return Response({"detail": "No users found for this group."}, status=status.HTTP_404_NOT_FOUND)

        user_ids = [ug['user_id'] for ug in user_groups]
        users = User.objects.filter(id__in=user_ids).values('id', 'username', 'name')

        return Response(users, status=status.HTTP_200_OK)



class ExpenseViewSet(viewsets.ModelViewSet):
    queryset = Expense.objects.all()
    serializer_class = ExpenseSerializer
    permission_classes = [IsAuthenticated]

    def get_queryset(self):
        return Expense.objects.filter(group_id__usergroup__user_id=self.request.user.pk)

    @staticmethod
    def _invalidate_cache(group_id):
        member_ids = UserGroup.objects.filter(group_id=group_id).values_list('user_id', flat=True)
        cache.delete_many([f"expense-data-user-{uid}" for uid in member_ids])

    def perform_create(self, serializer):
        expense = serializer.save()
        self._invalidate_cache(expense.group_id_id)
        log_activity(self.request.user, 'added', 'expense', expense.pk, group=expense.group_id,
                     name=expense.name, amount=str(expense.amount))

    def perform_update(self, serializer):
        old_group = serializer.instance.group_id_id
        expense = serializer.save()
        self._invalidate_cache(old_group)
        self._invalidate_cache(expense.group_id_id)
        log_activity(self.request.user, 'edited', 'expense', expense.pk, group=expense.group_id,
                     name=expense.name, amount=str(expense.amount))

    def perform_destroy(self, instance):
        group, name, amount, pk = instance.group_id, instance.name, str(instance.amount), instance.pk
        instance.delete()
        self._invalidate_cache(group.pk)
        log_activity(self.request.user, 'deleted', 'expense', pk, group=group, name=name, amount=amount)


    def list(self, request, *args, **kwargs):
        # Never share cached expense data between authenticated users.
        cache_key = f"expense-data-user-{request.user.pk}"

        cached_data = cache.get(cache_key)

        if cached_data:
            return Response(cached_data, status=status.HTTP_200_OK)

        queryset = self.get_queryset()
        serializer = self.get_serializer(queryset, many=True)
        serialized_data = serializer.data
        cache.set(cache_key, serialized_data, timeout=60 * 60 * 24)
        return Response(serialized_data, status=status.HTTP_200_OK)

    @action(detail=True, methods=['get'], url_path="settlements")
    def get_settlements(self, request, pk=None):
        if not UserGroup.objects.filter(group_id=pk, user_id=request.user.pk).exists():
            return Response({'detail': 'Not permitted.'}, status=status.HTTP_403_FORBIDDEN)
        expense_data = group_expense_data(pk)
        if not expense_data:
            return Response({"detail": "No expense found for this group."}, status=status.HTTP_404_NOT_FOUND)
        settlements = get_settlements_for_group(
            expense_data, request.user.id, payment_rows(pk, [Payment.COMPLETED]))
        return Response(settlements,status.HTTP_200_OK)


class PaymentViewSet(viewsets.ModelViewSet):
    """Payments between two group members.

    The payer records a payment (pending); the payee confirms receipt, and only
    confirmed payments reduce balances. The payer may cancel while it is pending.
    """
    serializer_class = PaymentSerializer
    permission_classes = [IsAuthenticated]
    http_method_names = ['get', 'post', 'delete', 'head', 'options']

    def get_queryset(self):
        user = self.request.user
        return Payment.objects.filter(Q(payer=user) | Q(payee=user)).select_related('group', 'payer', 'payee')

    def perform_create(self, serializer):
        payment = serializer.save(payer=self.request.user)
        Notification.objects.create(
            recipient=payment.payee, notification_type='payment',
            message=f'{payment.payer.username} says they paid you Rs {payment.amount} in {payment.group.name}. Confirm when received.',
        )
        log_activity(self.request.user, 'paid', 'payment', payment.pk, group=payment.group,
                     amount=str(payment.amount), to=payment.payee.username)

    def destroy(self, request, *args, **kwargs):
        payment = self.get_object()
        if payment.payer_id != request.user.pk:
            return Response({'detail': 'Only the payer can cancel a payment.'}, status=status.HTTP_403_FORBIDDEN)
        if payment.status != Payment.PENDING:
            return Response({'detail': 'Completed payments cannot be cancelled.'}, status=status.HTTP_400_BAD_REQUEST)
        log_activity(request.user, 'cancelled', 'payment', payment.pk, group=payment.group,
                     amount=str(payment.amount), to=payment.payee.username)
        return super().destroy(request, *args, **kwargs)

    @action(detail=True, methods=['post'])
    def confirm(self, request, pk=None):
        payment = self.get_object()
        if payment.payee_id != request.user.pk:
            return Response({'detail': 'Only the payee can confirm a payment.'}, status=status.HTTP_403_FORBIDDEN)
        if payment.status == Payment.PENDING:
            payment.status = Payment.COMPLETED
            payment.completed_at = timezone.now()
            payment.save(update_fields=('status', 'completed_at'))
            log_activity(request.user, 'confirmed', 'payment', payment.pk, group=payment.group,
                         amount=str(payment.amount), **{'from': payment.payer.username})
            Notification.objects.create(
                recipient=payment.payer, notification_type='payment',
                message=f'{payment.payee.username} confirmed your Rs {payment.amount} payment in {payment.group.name}.',
            )
        return Response(self.get_serializer(payment).data)


class NotificationViewSet(viewsets.ReadOnlyModelViewSet):
    serializer_class = NotificationSerializer
    permission_classes = [IsAuthenticated]

    def get_queryset(self):
        return Notification.objects.filter(recipient=self.request.user)

    @action(detail=True, methods=['post'])
    def mark_read(self, request, pk=None):
        notification = self.get_object()
        if notification.read_at is None:
            notification.read_at = timezone.now()
            notification.save(update_fields=('read_at',))
        return Response(self.get_serializer(notification).data)


class ActivityViewSet(viewsets.ReadOnlyModelViewSet):
    serializer_class = ActivitySerializer
    permission_classes = [IsAuthenticated]

    def get_queryset(self):
        """The user's own actions plus everything that happened in groups they belong to."""
        user = self.request.user
        my_groups = UserGroup.objects.filter(user_id=user).values('group_id')
        return Activity.objects.filter(Q(actor=user) | Q(group_id__in=my_groups)).select_related('actor').order_by('-created_at', '-id')
