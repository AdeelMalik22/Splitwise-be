from django.urls import path, include
from rest_framework.routers import DefaultRouter
from core.views import GroupViewSet, UserGroupViewSet, ExpenseViewSet, PaymentViewSet, NotificationViewSet

router = DefaultRouter()
router.register(r'groups', GroupViewSet, basename='group')
router.register(r'usersgroup', UserGroupViewSet, basename='usersgroup'),
router.register(r'expense',ExpenseViewSet,basename='expense')
router.register(r'payments', PaymentViewSet, basename='payment')
router.register(r'notifications', NotificationViewSet, basename='notification')

urlpatterns = [
    path('', include(router.urls)),
]
