
from rest_framework import viewsets, status
from rest_framework.decorators import action
from rest_framework.response import Response

from core.models import UserGroup, Group
from user.models import User
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
