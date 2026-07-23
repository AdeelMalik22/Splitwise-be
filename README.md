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

Install the dependencies listed in `requirement.txt`. The current project also requires the packages used by its configuration:

```bash
pip install -r requirement.txt
pip install djangorestframework djangorestframework-simplejwt psycopg2-binary django-redis redis
```

PostgreSQL must be available using the database settings in `splitwise/settings.py`. Redis is required by the expense-list cache configuration.

## Run locally

```bash
python manage.py migrate
python manage.py runserver
```

The API is available at `http://127.0.0.1:8000`.

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

## Important production work remaining

Before production deployment, move secrets and database credentials to environment variables, set `DEBUG = False`, configure `ALLOWED_HOSTS`, use HTTPS, add rate limiting and comprehensive tests, and replace array-based expense participants with validated relational data.
