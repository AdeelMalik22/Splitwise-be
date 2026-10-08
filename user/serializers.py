from rest_framework import serializers
from rest_framework.exceptions import APIException

from user.models import User
from rest_framework_simplejwt.serializers import TokenObtainPairSerializer


class EmailNotVerified(APIException):
    status_code = 403
    default_code = 'email_not_verified'
    default_detail = 'Please verify your email address before logging in. Check your inbox for the link.'


class MyTokenObtainPairSerializer(TokenObtainPairSerializer):
    def validate(self, attrs):
        data = super().validate(attrs)  # raises 401 for wrong credentials first
        if not self.user.email_verified:
            raise EmailNotVerified({'detail': EmailNotVerified.default_detail, 'code': 'email_not_verified'})
        return data

    @classmethod
    def get_token(cls, user):
        token = super(MyTokenObtainPairSerializer, cls).get_token(user)

        # Add custom claims
        token['username'] = user.username
        return token


class UserSerializer(serializers.ModelSerializer):
    password = serializers.CharField(write_only=True, min_length=8)

    class Meta:
        model = User
        fields = ('id', 'username', 'name', 'email', 'password', 'email_verified')
        read_only_fields = ('email_verified',)

    def create(self, validated_data):
        return User.objects.create_user(**validated_data)

    def update(self, instance, validated_data):
        # Passwords change only through /users/change_password/, which checks the old one.
        validated_data.pop('password', None)
        for attr, value in validated_data.items():
            setattr(instance, attr, value)
        instance.save()
        return instance


class ChangePasswordSerializer(serializers.Serializer):
    old_password = serializers.CharField(write_only=True)
    new_password = serializers.CharField(write_only=True, min_length=8)

    def validate_old_password(self, value):
        if not self.context['request'].user.check_password(value):
            raise serializers.ValidationError('Current password is incorrect.')
        return value

    def validate_new_password(self, value):
        from django.contrib.auth.password_validation import validate_password
        from django.core.exceptions import ValidationError as DjangoValidationError
        try:
            validate_password(value, self.context['request'].user)
        except DjangoValidationError as exc:
            raise serializers.ValidationError(list(exc.messages))
        return value


class DeleteAccountSerializer(serializers.Serializer):
    password = serializers.CharField(write_only=True)

    def validate_password(self, value):
        if not self.context['request'].user.check_password(value):
            raise serializers.ValidationError('Password is incorrect.')
        return value
