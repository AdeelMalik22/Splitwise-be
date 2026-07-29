from django.core.cache import cache
from django.db import connection
from rest_framework.permissions import AllowAny
from rest_framework.response import Response
from rest_framework.views import APIView


class HealthCheckView(APIView):
    permission_classes = [AllowAny]

    def get(self, request):
        checks = {'database': False, 'cache': False}
        try:
            with connection.cursor() as cursor:
                cursor.execute('SELECT 1')
            checks['database'] = True
        except Exception:
            pass
        try:
            cache.set('health-check', 'ok', timeout=10)
            checks['cache'] = cache.get('health-check') == 'ok'
        except Exception:
            pass
        healthy = all(checks.values())
        return Response(
            {'status': 'ok' if healthy else 'degraded', 'checks': checks},
            status=200 if healthy else 503,
        )
