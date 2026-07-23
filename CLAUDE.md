# Splitwise API Development Guide

## Project overview

This is a Django REST Framework backend for users, groups, group memberships, shared expenses, and settlement calculations.

Main apps:

- `user/`: custom user model, registration, JWT login, and user endpoints.
- `core/`: groups, memberships, expenses, and settlement calculations.
- `splitwise/`: Django settings, root URL configuration, ASGI, and WSGI entry points.
- `postman/`: Postman API collection for manual endpoint testing.

## Local setup

Install the project requirements and the packages used by the current configuration:

```bash
pip install -r requirement.txt
pip install djangorestframework djangorestframework-simplejwt psycopg2-binary django-redis redis
```

The configured PostgreSQL database and Redis server must be running. Apply migrations and start the server:

```bash
python manage.py migrate
python manage.py runserver
```

## Authentication and security

- DRF endpoints require JWT authentication by default.
- Use `Authorization: Bearer <access-token>` for protected requests.
- Account creation is exposed through `POST /users/register/`.
- Passwords must be created or changed through Django password hashing APIs (`create_user()` or `set_password()`).
- Never expose password fields in response serializers.
- Users must only access their own user record and groups in which they are members.
- Any new endpoint that returns or changes private data must enforce object-level authorization.
- Do not commit secrets, database passwords, `.env` files, or production credentials.

## API routes

- `POST /users/register/`: register an account.
- `POST /login/`: obtain access and refresh JWTs.
- `POST /login/refresh/`: refresh an access token.
- `/users/`: authenticated user operations.
- `/groups/`: group operations.
- `/usersgroup/`: group membership operations.
- `/expense/`: expense operations.
- `GET /expense/<group_id>/settlements/`: calculate settlements for an authorized group.

See `README.md` and `postman/Splitwise API.postman_collection.json` for request examples.

## Development conventions

- Use DRF serializers for validation and creation rather than custom unused `post()` methods on viewsets.
- Prefer `DecimalField` for monetary values and validate that participants belong to the selected group.
- Keep queryset filtering scoped to `request.user` where data is private.
- Add or update migrations whenever models change.
- Add regression tests for authentication, authorization, password handling, and settlement calculations.
- Keep cache keys scoped to the authenticated user or group, and invalidate them after writes.

## Verification

Before submitting changes, run:

```bash
python -m py_compile splitwise/settings.py user/serializers.py user/views.py core/views.py
python manage.py check
python manage.py test
git diff --check
```

If local dependencies or PostgreSQL/Redis are unavailable, report that clearly and still run syntax and formatting checks.
