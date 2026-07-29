# Splitwise API

A Django REST Framework backend for managing users, groups, shared expenses, and settlements.

## Current security behavior

- JWT authentication is required for API endpoints by default.
- `POST /users/register/` is the only public account-creation endpoint.
- Passwords are hashed and never returned by the API.
- Users can only access their own user record and groups they belong to.
- Expense and settlement data is restricted to the authenticated user's groups.
- Send access tokens using `Authorization: Bearer <access-token>`.

## Requirements

Install the dependencies listed in `requirement.txt`:

```bash
pip install -r requirement.txt
```

PostgreSQL must be available. Local development uses Django's in-memory cache unless `REDIS_URL` is set; deployments should provide Redis through `REDIS_URL`. Use `.env.example` as a template and export the values; the settings module does not load `.env` files automatically.

## Run locally

```bash
python manage.py migrate
python manage.py runserver
```

The API is available at `http://127.0.0.1:8000`.

Run validation locally with:

```bash
python manage.py check
python manage.py test
```

The unauthenticated readiness endpoint is `GET /health/`. It returns `200`
when PostgreSQL and the configured cache are available, otherwise `503`.

## Authentication

Register a user:

```http
POST /users/register/
Content-Type: application/json

{
  "username": "alice",
  "name": "Alice",
  "email": "alice@example.com",
  "password": "strong-password-123"
}
```

Login:

```http
POST /login/
Content-Type: application/json

{
  "username": "alice",
  "password": "strong-password-123"
}
```

Use the returned `access` token for protected requests. Refresh it with `POST /login/refresh/` and a body containing the `refresh` token.

## Main endpoints

| Method | Endpoint | Description |
|---|---|---|
| POST | `/users/register/` | Register a user |
| POST | `/login/` | Obtain JWT access and refresh tokens |
| POST | `/login/refresh/` | Refresh an access token |
| GET/PATCH/DELETE | `/users/{id}/` | Manage your own user record |
| GET/POST | `/groups/` | List or create groups |
| GET/PATCH/DELETE | `/groups/{id}/` | Manage a group you belong to |
| GET/POST | `/usersgroup/` | List or create group memberships |
| GET/PATCH/DELETE | `/usersgroup/{id}/` | Manage a membership you own |
| GET/POST | `/expense/` | List or create expenses in your groups |
| GET/PATCH/DELETE | `/expense/{id}/` | Manage an expense in your groups |
| GET | `/expense/{group_id}/settlements/` | Calculate group settlements |
| GET | `/users/{id}/groups/` | List groups for your own user ID |
| GET | `/usersgroup/{group_id}/users/` | List users in one of your groups |
| GET | `/health/` | Check database and cache readiness |

The exact expense payload uses `amount`, `paid_by`, `split_on`, and `group_id`, for example:

```json
{
  "name": "Dinner",
  "description": "Shared dinner",
  "amount": 120.00,
  "paid_by": [1],
  "split_on": [1, 2],
  "group_id": 1
}
```

## Postman

Import [`postman/Splitwise API.postman_collection.json`](postman/Splitwise%20API.postman_collection.json) into Postman. Set the collection variable `base_url` if the server is not running at `http://127.0.0.1:8000`.

The login request stores the returned access and refresh tokens automatically for subsequent requests.

## Configuration and deployment

The settings are configured through environment variables. `.env.example`
contains the available variables and safe local defaults. Production setup,
including HTTPS, Redis, migrations, static files, and health verification, is
documented in [`DEPLOYMENT.md`](DEPLOYMENT.md).

Every push to `main` and every pull request runs the Django checks, migrations,
and tests in GitHub Actions against PostgreSQL and Redis.

## Remaining domain work

The deployment settings, environment configuration, rate limiting, health endpoint, participant validation, and CI checks are in place. See [`DEPLOYMENT.md`](DEPLOYMENT.md) for the production runbook. The main remaining domain-level improvement is replacing array-based expense participants with relational data and expanding integration tests around all API workflows.
