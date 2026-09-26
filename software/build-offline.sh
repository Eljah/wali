#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
rm -rf dist/classes
mkdir -p dist/classes
find robot-core/src/main/java robot-app/src/main/java -name '*.java' > dist/sources.txt
javac --release 21 -encoding UTF-8 -d dist/classes @dist/sources.txt
jar --create --file dist/ubor-sim.jar --main-class com.ubor.app.Main -C dist/classes .
java -Djava.awt.headless=true -jar dist/ubor-sim.jar --self-test dist/test-report

mkdir -p dist/test-classes
find robot-app/src/test/java -name '*.java' > dist/test-sources.txt
javac --release 21 -encoding UTF-8 -cp dist/ubor-sim.jar -d dist/test-classes @dist/test-sources.txt
java -Djava.awt.headless=true -cp "dist/ubor-sim.jar:dist/test-classes" com.ubor.app.SimBoundaryTest dist/boundary-report
