from rest_framework.throttling import SimpleRateThrottle


class EmailRateThrottle(SimpleRateThrottle):
    """Limits endpoints that send email (verification, password reset, invites) per client address."""
    scope = 'email'

    def get_cache_key(self, request, view):
        return self.cache_format % {'scope': self.scope, 'ident': self.get_ident(request)}


class InviteRateThrottle(SimpleRateThrottle):
    """Caps how many invitation emails one signed-in user can trigger."""
    scope = 'invite'

    def get_cache_key(self, request, view):
        ident = request.user.pk if request.user and request.user.is_authenticated else self.get_ident(request)
        return self.cache_format % {'scope': self.scope, 'ident': ident}
