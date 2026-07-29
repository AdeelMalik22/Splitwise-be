from django.urls import path, include
from rest_framework.routers import DefaultRouter
from rest_framework_simplejwt.views import TokenRefreshView

from user.views import UserVietSet, MyObtainTokenPairView, GroupInviteViewSet


router = DefaultRouter()
router.register(r'users', UserVietSet, basename='users')
router.register(r'invites', GroupInviteViewSet, basename='invite')
urlpatterns = [
    path('', include(router.urls)),
    path('login/', MyObtainTokenPairView.as_view(), name='token_obtain_pair'),
    path('login/refresh/', TokenRefreshView.as_view(), name='token_refresh'),
]
