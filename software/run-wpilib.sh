#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
jar="robot-wpilib/target/robot-wpilib-0.2.0.jar"
if [[ ! -f "$jar" ]]; then echo "WPILib backend is not built. Run: mvn -Pwpilib clean verify" >&2; exit 2; fi
native="$(pwd)/robot-wpilib/target/native"
export LD_LIBRARY_PATH="$native${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"
export DYLD_LIBRARY_PATH="$native${DYLD_LIBRARY_PATH:+:$DYLD_LIBRARY_PATH}"
if [[ $# -eq 0 ]]; then set -- --desktop; fi
exec java "-Djava.library.path=$native" -cp "$jar:robot-wpilib/target/lib/*" com.ubor.wpilib.WpiMain "$@"
