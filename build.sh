#!/usr/bin/env bash
# Render build step: install, migrate, and create the database-backed cache table.
set -o errexit
pip install -r requirement.txt
python manage.py migrate --noinput
python manage.py createcachetable
