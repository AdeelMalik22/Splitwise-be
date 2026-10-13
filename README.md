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

### Load realistic demo data

After running migrations, load 20 users, overlapping groups, expenses,
participants, payments, invites, notifications, and activity records:

```bash
python manage.py seed_demo_data
```

The command is safe to run repeatedly. Add `--reset` only when you want to
delete the demo users and their related records first. Every demo user uses
the password `SplitwiseDemo!2026`; usernames and names are listed in the
collection and include Umer, Adeel, Saim, Fatima, and Hamza.

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
| GET | `/expense/report/` | Monthly and category spending totals |
| GET | `/expense/settlement-suggestions/?group={id}` | Minimized transfers for a group |
| POST | `/users/logout_all/` | Revoke all existing JWT sessions for the account |
| GET | `/users/{id}/groups/` | List groups for your own user ID |
| GET | `/usersgroup/{group_id}/users/` | List users in one of your groups |
| GET | `/health/` | Check database and cache readiness |

The exact expense payload uses `amount`, `paid_by`, `split_on`, and `group_id`, for example:

```json
{
  "name": "Dinner",
  "description": "Shared dinner",
  "category": "food",
  "amount": 120.00,
  "paid_by": [1],
  "split_on": [1, 2],
  "group_id": 1
}
```

Expenses support the categories `food`, `travel`, `rent`, `entertainment`, and
`other`. Multiple payers can send `paid_details`, for example:

```json
"paid_by": [1, 2],
"paid_details": [
  {"user_id": 1, "amount": "200.00"},
  {"user_id": 2, "amount": "400.00"}
]
```

Expense lists accept `q`, `category`, `month` (`YYYY-MM`), `payer`,
`min_amount`, and `max_amount` query parameters.

## Security and sessions

JWT access tokens include a per-user session version. Changing a password or
using `POST /users/logout_all/` revokes previously issued sessions. The Android
client also provides local logout, fingerprint login, and a danger-zone action
for logging out other devices.

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

## Testing

Run the full backend suite with `python manage.py test`. The Android unit suite
is run from `android/` with `./gradlew :app:testDebugUnitTest`.

The test coverage includes participant validation, multiple payer amounts,
expense filtering, settlement minimization, cache isolation, session
revocation, and the Android offline write queue.
