#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
command -v mvn >/dev/null || { echo "Install Maven 3.9+ and Java 21, then rerun." >&2; exit 2; }
exec mvn -Pwpilib clean verify "$@"
