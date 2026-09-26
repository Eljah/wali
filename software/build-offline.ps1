$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot
if (Test-Path dist/classes) { Remove-Item -Recurse -Force dist/classes }
New-Item -ItemType Directory -Force dist/classes | Out-Null
$src = Get-ChildItem robot-core/src/main/java,robot-app/src/main/java -Recurse -Filter *.java | ForEach-Object { $_.FullName }
& javac --release 21 -encoding UTF-8 -d dist/classes $src
if ($LASTEXITCODE -ne 0) { throw "javac failed" }
& jar --create --file dist/ubor-sim.jar --main-class com.ubor.app.Main -C dist/classes .
if ($LASTEXITCODE -ne 0) { throw "jar failed" }
& java -Djava.awt.headless=true -jar dist/ubor-sim.jar --self-test dist/test-report
if ($LASTEXITCODE -ne 0) { throw "Tests failed" }

New-Item -ItemType Directory -Force dist/test-classes | Out-Null
$testSources = Get-ChildItem robot-app/src/test/java -Filter *.java -Recurse | ForEach-Object { $_.FullName }
& javac --release 21 -encoding UTF-8 -cp dist/ubor-sim.jar -d dist/test-classes $testSources
if ($LASTEXITCODE -ne 0) { throw "Contract test compilation failed" }
& java -Djava.awt.headless=true -cp "dist/ubor-sim.jar;dist/test-classes" com.ubor.app.SimBoundaryTest dist/boundary-report
if ($LASTEXITCODE -ne 0) { throw "Contract tests failed" }
