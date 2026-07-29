from django.core.cache import cache

from rest_framework import viewsets, status
from rest_framework.decorators import action
from rest_framework.permissions import IsAuthenticated
from rest_framework.response import Response

from core.models import Group, UserGroup, Expense, ExpenseParticipant, Payment
from core.payment_serializers import PaymentSerializer
from django.utils import timezone
from core.serializers import GroupSerializer, UserGroupSerializer, ExpenseSerializer
from core.settlements import get_settlements_for_group
from user.models import User


# Create your views here.


class GroupViewSet(viewsets.ModelViewSet):
    queryset = Group.objects.all()
    serializer_class = GroupSerializer
    permission_classes = [IsAuthenticated]

    def get_queryset(self):
        return Group.objects.filter(usergroup__user_id=self.request.user.pk)

    def perform_create(self, serializer):
        group = serializer.save()
        UserGroup.objects.get_or_create(user_id=self.request.user, group_id=group)

    def post(self, request, *args, **kwargs):
        serializer = GroupSerializer(data=request.data)
        if serializer.is_valid():
            serializer.save()
            return Response(serializer.data, status=status.HTTP_201_CREATED)

    @action(detail=True, methods=['delete'],url_name='delete')
    def delete_group(self,request,pk=None):
        delete_group = self.get_queryset().filter(pk=pk).first()
        if not delete_group:
            return Response({'detail': 'Not found.'}, status=status.HTTP_404_NOT_FOUND)
        delete_group.delete()
        return Response({"detail": "Group and its associate records have been deleted."},status=status.HTTP_204_NO_CONTENT)


class UserGroupViewSet(viewsets.ModelViewSet):
    queryset = UserGroup.objects.all()
    serializer_class = UserGroupSerializer
    permission_classes = [IsAuthenticated]

    def get_queryset(self):
        return UserGroup.objects.filter(user_id=self.request.user.pk)

    def post(self, request, *args, **kwargs):
        serializer = UserGroupSerializer(data=request.data)
        if serializer.is_valid():
            serializer.save()
            return Response(serializer.data, status=status.HTTP_201_CREATED)

    @action(detail=True, methods=['get'], url_path="users")
    def get_group_users(self, request, pk=None):
        if not self.get_queryset().filter(group_id=pk).exists():
            return Response({'detail': 'Not permitted.'}, status=status.HTTP_403_FORBIDDEN)
        user_groups = UserGroup.objects.filter(group_id=pk).values('user_id')

        if not user_groups.exists():
            return Response({"detail": "No users found for this group."}, status=status.HTTP_404_NOT_FOUND)

        user_ids = [ug['user_id'] for ug in user_groups]
        users = User.objects.filter(id__in=user_ids).values()

        return Response(users, status=status.HTTP_200_OK)



class ExpenseViewSet(viewsets.ModelViewSet):
    queryset = Expense.objects.all()
    serializer_class = ExpenseSerializer
    permission_classes = [IsAuthenticated]

    def get_queryset(self):
        return Expense.objects.filter(group_id__usergroup__user_id=self.request.user.pk)


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

    def post(self, request, *args, **kwargs):
        serializer = ExpenseSerializer(data=request.data)
        if serializer.is_valid():
            serializer.save()
            return Response(serializer.data, status=status.HTTP_201_CREATED)

    @action(detail=True, methods=['get'], url_path="settlements")
    def get_settlements(self, request, pk=None):
        if not UserGroup.objects.filter(group_id=pk, user_id=request.user.pk).exists():
            return Response({'detail': 'Not permitted.'}, status=status.HTTP_403_FORBIDDEN)
        expenses = list(Expense.objects.filter(group_id=pk).prefetch_related('participants'))
        if not expenses:
            return Response({"detail": "No expense found for this group."}, status=status.HTTP_404_NOT_FOUND)
        expense_data = []
        for expense in expenses:
            participants = expense.participants.all()
            expense_data.append({
                'amount': expense.amount,
                'paid_by': [p.user_id for p in participants if p.role == ExpenseParticipant.PAID],
                'split_on': [p.user_id for p in participants if p.role == ExpenseParticipant.SPLIT],
            })
        settlements = get_settlements_for_group(expense_data, request.user.id)
        return Response(settlements,status.HTTP_200_OK)


class PaymentViewSet(viewsets.ModelViewSet):
    serializer_class = PaymentSerializer
    permission_classes = [IsAuthenticated]

    def get_queryset(self):
        return Payment.objects.filter(
            expense__group_id__usergroup__user_id=self.request.user.pk
        ).select_related('expense', 'payer', 'payee')

    def perform_update(self, serializer):
        payment = serializer.save()
        if payment.status == Payment.COMPLETED and payment.completed_at is None:
            payment.completed_at = timezone.now()
            payment.save(update_fields=('completed_at',))
