#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
exec java -jar dist/ubor-sim.jar --desktop
