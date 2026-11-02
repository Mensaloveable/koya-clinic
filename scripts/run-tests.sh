#!/usr/bin/env sh
set -eu
: "${TEST_DB_URL:?Set TEST_DB_URL to a disposable PostgreSQL database}"
exec mvn -B verify "$@"
