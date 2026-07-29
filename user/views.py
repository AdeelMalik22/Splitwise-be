
from django.db.models import Q
from rest_framework import viewsets, status
from rest_framework.decorators import action
from rest_framework.response import Response

from core.models import UserGroup, Group
from user.models import User, GroupInvite
from user.invite_serializers import UserSearchSerializer, GroupInviteSerializer
from user.serializers import UserSerializer

from .serializers import MyTokenObtainPairSerializer
from rest_framework.permissions import AllowAny, IsAuthenticated
from rest_framework_simplejwt.views import TokenObtainPairView


class MyObtainTokenPairView(TokenObtainPairView):
    permission_classes = (AllowAny,)
    serializer_class = MyTokenObtainPairSerializer

class UserVietSet(viewsets.ModelViewSet):
    serializer_class = UserSerializer
    permission_classes = (IsAuthenticated,)

    def get_queryset(self):
        # Users must not be able to enumerate or edit other accounts.
        return User.objects.filter(pk=self.request.user.pk)

    @action(detail=False, methods=['get'])
    def search(self, request):
        query = request.query_params.get('q', '').strip()
        if len(query) < 2:
            return Response({'detail': 'q must contain at least 2 characters.'}, status=400)
        users = User.objects.exclude(pk=request.user.pk).filter(username__icontains=query)[:20]
        return Response(UserSearchSerializer(users, many=True).data)

    def create(self, request, *args, **kwargs):
        return Response(
            {'detail': 'Use POST /users/register/ to create an account.'},
            status=status.HTTP_405_METHOD_NOT_ALLOWED,
        )

    @action(detail=False, methods=['post'], permission_classes=[AllowAny])
    def register(self, request):
        serializer = self.get_serializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        user = serializer.save()
        return Response(self.get_serializer(user).data, status=status.HTTP_201_CREATED)

    @action(detail=True, methods=['get'], url_path="groups")
    def get_group_users(self, request, pk=None):
        if str(request.user.pk) != str(pk):
            return Response({'detail': 'Not permitted.'}, status=status.HTTP_403_FORBIDDEN)
        user_groups = UserGroup.objects.filter(user_id=pk).values('group_id')

        if not user_groups.exists():
            return Response({"detail": "No found found for this user."}, status=status.HTTP_404_NOT_FOUND)

        group_ids = [ug['group_id'] for ug in user_groups]
        groups = Group.objects.filter(id__in=group_ids).values()

        return Response(groups, status=status.HTTP_200_OK)

    # def list(self, request):
    #     queryset = User.objects.all().values()
    #     serializer = UserSerializer(queryset, many=True)
    #     return Response(serializer.data)


class GroupInviteViewSet(viewsets.ModelViewSet):
    serializer_class = GroupInviteSerializer
    permission_classes = [IsAuthenticated]
    http_method_names = ['get', 'post', 'head', 'options']

    def get_queryset(self):
        return GroupInvite.objects.filter(
            Q(inviter=self.request.user) | Q(invitee=self.request.user)
        ).select_related('group', 'inviter', 'invitee')

    def perform_create(self, serializer):
        serializer.save(inviter=self.request.user)

    @action(detail=True, methods=['post'])
    def accept(self, request, pk=None):
        invite = self.get_queryset().filter(pk=pk, invitee=request.user, status=GroupInvite.PENDING).first()
        if not invite:
            return Response({'detail': 'Pending invite not found.'}, status=404)
        UserGroup.objects.get_or_create(user_id=request.user, group_id=invite.group)
        invite.status = GroupInvite.ACCEPTED
        invite.save(update_fields=('status',))
        return Response(GroupInviteSerializer(invite).data)

    @action(detail=True, methods=['post'])
    def decline(self, request, pk=None):
        invite = self.get_queryset().filter(invitee=request.user, pk=pk, status=GroupInvite.PENDING).first()
        if not invite:
            return Response({'detail': 'Pending invite not found.'}, status=404)
        invite.status = GroupInvite.DECLINED
        invite.save(update_fields=('status',))
        return Response(GroupInviteSerializer(invite).data)
