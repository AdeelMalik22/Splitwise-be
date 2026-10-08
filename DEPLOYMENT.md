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

## Free hosting (Render + Neon)

1. Create a free PostgreSQL project on neon.tech and copy its connection string.
2. On render.com choose New → Blueprint, select this repository (`render.yaml`
   is picked up automatically) and paste the Neon string into `DATABASE_URL`.
3. Redis is not required: without `REDIS_URL` the cache uses a database table
   created by `build.sh` (`createcachetable`).
4. After the first deploy open `https://<service>.onrender.com/health/` and
   confirm both checks are true. Optionally seed demo users from the Render
   shell with `python manage.py seed_demo_data`.

The free web service sleeps after ~15 minutes idle; the first request after
that takes up to a minute to wake it.

## Single server (AWS EC2) with Docker

Runs Caddy (automatic HTTPS) -> Django (gunicorn) -> PostgreSQL on one instance.

1. Launch an Ubuntu 22.04/24.04 instance (`t3.small` is comfortable, `t3.micro` works for a handful of users).
   Attach an **Elastic IP**. Security group: allow 80 and 443 from anywhere, 22 only from your IP.
2. Point a hostname at the Elastic IP (a free DuckDNS subdomain works). Android release builds need HTTPS,
   which needs a hostname.
3. On the server:
   ```bash
   curl -fsSL https://get.docker.com | sudo sh && sudo usermod -aG docker $USER   # re-login after this
   git clone https://github.com/AdeelMalik22/Splitwise-be.git && cd Splitwise-be
   cp .env.production.example .env && nano .env      # set DOMAIN, DJANGO_SECRET_KEY, POSTGRES_PASSWORD
   docker compose up -d --build
   ```
4. Check `https://<DOMAIN>/health/` returns `"database": true, "cache": true`.
5. Update later with `git pull && docker compose up -d --build`.
6. Back up the database regularly, e.g. a daily cron job:
   `docker compose exec -T db pg_dump -U splitwise splitwise | gzip > ~/backup-$(date +%F).sql.gz`
7. Set an AWS billing alert so spend never surprises you.
