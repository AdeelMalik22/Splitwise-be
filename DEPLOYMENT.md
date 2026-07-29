# Deployment checklist

1. Install dependencies with `pip install -r requirement.txt`.
2. Export the variables in `.env.example`, including a generated
   `DJANGO_SECRET_KEY`, `DJANGO_DEBUG=False`, and the production
   `DJANGO_ALLOWED_HOSTS`.
3. Configure PostgreSQL and set `POSTGRES_*` variables.
4. Configure Redis with `REDIS_URL`.
5. Set `CORS_ALLOWED_ORIGINS` and `CSRF_TRUSTED_ORIGINS` to HTTPS frontend
   origins only.
6. Set `DJANGO_SECURE_SSL_REDIRECT=True` behind an HTTPS-aware proxy and set
   `SECURE_HSTS_SECONDS` after verifying HTTPS works end-to-end.
7. Run `python manage.py migrate --noinput`.
8. Run `python manage.py collectstatic --noinput` and serve `STATIC_ROOT` from
   the web server or object storage.
9. Start the application with a production WSGI server using
   `splitwise.wsgi:application`.
10. Verify `GET /health/` returns HTTP 200 and both dependency checks are true.

Do not commit real environment files, credentials, or generated static/media
files.
