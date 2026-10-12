from rest_framework.exceptions import AuthenticationFailed
from rest_framework_simplejwt.authentication import JWTAuthentication


class SessionJWTAuthentication(JWTAuthentication):
    def get_user(self, validated_token):
        user = super().get_user(validated_token)
        version = validated_token.get('session_version')
        if version is not None and int(version) != user.session_version:
            raise AuthenticationFailed('This session has been revoked. Please log in again.', code='session_revoked')
        return user
