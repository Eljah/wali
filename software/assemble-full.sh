#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
mvn -Pfull clean install
rm -rf dist/full
mkdir -p dist/full
for module in robot-core robot-app robot-pi robot-ml; do
  cp "$module/target/$module-0.2.0.jar" dist/full/
  mvn -f "$module/pom.xml" dependency:copy-dependencies -DincludeScope=runtime -DoutputDirectory="$(pwd)/dist/full"
done
printf '%s\n' 'Run: java -cp "dist/full/*" com.ubor.ml.Preflight'
