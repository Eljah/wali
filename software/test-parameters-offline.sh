#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p dist/parameter-test-classes
javac --release 21 -encoding UTF-8 -d dist/parameter-test-classes robot-wpilib/src/main/java/com/ubor/wpilib/WpiParameters.java robot-wpilib/src/test/java/com/ubor/wpilib/ParametersContract.java
java -cp dist/parameter-test-classes com.ubor.wpilib.ParametersContract config/wpilib-sim.properties dist/parameter-report
