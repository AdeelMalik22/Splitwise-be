
from django.contrib.auth.password_validation import validate_password
from django.core.exceptions import ValidationError as DjangoValidationError
from django.db.models import Q
from django.utils.html import escape
from rest_framework import viewsets, status
from rest_framework.decorators import action
from rest_framework.response import Response

from core.activity import log_activity
from core.models import UserGroup, Group
from user.models import User, GroupInvite
from user.invite_serializers import UserSearchSerializer, GroupInviteSerializer
from user.emails import read_reset_token, read_verify_token, send_reset_email, send_verification_email
from user.pages import app_link, page
from user.throttles import EmailRateThrottle
from user.serializers import ChangePasswordSerializer, DeleteAccountSerializer, UserSerializer

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
        sent = send_verification_email(user, request)
        return Response({**self.get_serializer(user).data, 'email_sent': sent}, status=status.HTTP_201_CREATED)

    @action(detail=False, methods=['get'], permission_classes=[AllowAny], authentication_classes=[],
            throttle_classes=[], url_path='verify_email')
    def verify_email(self, request):
        """Opened from the emailed link in a browser."""
        user = read_verify_token(request.query_params.get('token', ''))
        if user is None:
            return page('Link expired', 'This verification link is invalid or has expired. Open SplitEase and request a new one.',
                        [('Open SplitEase', app_link('login'), True)], status=400)
        if not user.email_verified:
            user.email_verified = True
            user.save(update_fields=('email_verified',))
        return page('Email verified ✓', 'Your email is confirmed. Open the app and log in to get started.',
                    [('Open SplitEase', app_link('login?verified=1'), True)])

    @action(detail=False, methods=['post'], permission_classes=[AllowAny], authentication_classes=[],
            throttle_classes=[EmailRateThrottle], url_path='resend_verification')
    def resend_verification(self, request):
        """Always answers the same way so the endpoint can't be used to discover which emails have accounts."""
        ident = str(request.data.get('identifier', '')).strip()
        user = User.objects.filter(Q(email__iexact=ident) | Q(username=ident)).first() if ident else None
        if user is not None and not user.email_verified:
            send_verification_email(user, request)
        return Response({'detail': 'If that account needs verification, a new email is on its way.'})

    @action(detail=False, methods=['post'], permission_classes=[AllowAny], authentication_classes=[],
            throttle_classes=[EmailRateThrottle], url_path='forgot_password')
    def forgot_password(self, request):
        """Always answers identically, so it can't be used to find out which emails have accounts."""
        ident = str(request.data.get('identifier', '')).strip()
        user = User.objects.filter(Q(email__iexact=ident) | Q(username=ident)).first() if ident else None
        if user is not None:
            send_reset_email(user, request)
        return Response({'detail': 'If an account matches, we have emailed a link to reset the password.'})

    @action(detail=False, methods=['get', 'post'], permission_classes=[AllowAny], authentication_classes=[],
            throttle_classes=[], url_path='reset_password')
    def reset_password(self, request):
        """The page opened from the reset email: GET shows the form, POST sets the new password."""
        token = request.query_params.get('token') or request.data.get('token', '')
        user = read_reset_token(token)
        if user is None:
            return page('Link expired', 'This reset link is invalid, already used, or has expired. Request a new one from the app.',
                        [('Open SplitEase', app_link('login'), True)], status=400)
        error = ''
        if request.method == 'POST':
            password, confirm = request.data.get('password', ''), request.data.get('confirm', '')
            try:
                if password != confirm:
                    raise DjangoValidationError('The two passwords do not match.')
                validate_password(password, user)
            except DjangoValidationError as exc:
                error = '<div class="err">' + '<br>'.join(escape(m) for m in exc.messages) + '</div>'
            else:
                user.set_password(password)
                user.email_verified = True  # they just proved they control the mailbox
                user.save(update_fields=('password', 'email_verified'))
                return page('Password updated ✓', 'Your password has been changed. Log in with your new password.',
                            [('Open SplitEase', app_link('login'), True)])
        form = (f'{error}<form method="post" action="/users/reset_password/"><input type="hidden" name="token" value="{escape(token)}">'
                '<input type="password" name="password" placeholder="New password" minlength="8" required autocomplete="new-password">'
                '<input type="password" name="confirm" placeholder="Confirm new password" minlength="8" required autocomplete="new-password">'
                '<button class="btn" type="submit">Set new password</button></form>')
        return page('Choose a new password', f'For {user.username}. At least 8 characters.', body_html=form,
                    status=400 if error else 200)

    @action(detail=False, methods=['post'], url_path='change_password')
    def change_password(self, request):
        serializer = ChangePasswordSerializer(data=request.data, context={'request': request})
        serializer.is_valid(raise_exception=True)
        request.user.set_password(serializer.validated_data['new_password'])
        request.user.save(update_fields=('password',))
        return Response({'detail': 'Password updated.'})

    @action(detail=False, methods=['post'], url_path='delete_account')
    def delete_account(self, request):
        serializer = DeleteAccountSerializer(data=request.data, context={'request': request})
        serializer.is_valid(raise_exception=True)
        request.user.delete()
        return Response(status=status.HTTP_204_NO_CONTENT)

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
        log_activity(request.user, 'joined', 'group', invite.group_id, group=invite.group)
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
