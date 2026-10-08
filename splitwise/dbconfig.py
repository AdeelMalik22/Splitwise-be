from urllib.parse import parse_qsl, unquote, urlparse


def database_from_url(url, conn_max_age=60):
    """Build a Django DATABASES entry from a postgres:// URL (Neon, Render, Heroku style)."""
    parsed = urlparse(url)
    if parsed.scheme not in ('postgres', 'postgresql'):
        raise ValueError('DATABASE_URL must start with postgres:// or postgresql://')
    config = {
        'ENGINE': 'django.db.backends.postgresql',
        'NAME': unquote(parsed.path.lstrip('/')),
        'USER': unquote(parsed.username or ''),
        'PASSWORD': unquote(parsed.password or ''),
        'HOST': parsed.hostname or '',
        'PORT': str(parsed.port or 5432),
        'CONN_MAX_AGE': conn_max_age,
    }
    options = dict(parse_qsl(parsed.query))
    # psycopg2 understands these libpq options directly.
    allowed = {k: v for k, v in options.items() if k in ('sslmode', 'sslrootcert', 'connect_timeout')}
    if allowed:
        config['OPTIONS'] = allowed
    return config
