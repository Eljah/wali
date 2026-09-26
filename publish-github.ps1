param(
    [ValidateSet('private', 'public')]
    [string]$Visibility = 'private'
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
# Native exit codes are checked explicitly (PowerShell 5.1 and 7).
if (Get-Variable PSNativeCommandUseErrorActionPreference -ErrorAction SilentlyContinue) {
    $PSNativeCommandUseErrorActionPreference = $false
}
function Invoke-Checked {
    param([string]$Program, [string[]]$ArgList)
    & $Program @ArgList
    if ($LASTEXITCODE -ne 0) { throw "$Program failed with exit code $LASTEXITCODE. No force-push is performed." }
}
foreach ($tool in @('git', 'gh')) {
    if (-not (Get-Command $tool -ErrorAction SilentlyContinue)) {
        throw "Install $tool and open a new terminal before running this script."
    }
}
$root = $PSScriptRoot
foreach ($file in @('README.md', 'software/pom.xml', 'software/robot-wpilib/pom.xml', 'docs/UBOR_JAVA_BOOK_R02.pdf')) {
    if (-not (Test-Path -LiteralPath (Join-Path $root $file))) { throw "Missing project file: $file" }
}
if (Test-Path -LiteralPath (Join-Path $root '.git')) {
    throw 'A local .git already exists. Inspect it and use the recovery instructions in PUBLISH_GITHUB_RU.md; nothing was changed.'
}
$previousHost = $env:GH_HOST
$env:GH_HOST = 'github.com'
Push-Location $root
try {
    $json = Invoke-Checked -Program gh -ArgList @('api', '--hostname', 'github.com', 'user')
    $account = ($json -join "`n") | ConvertFrom-Json
    if ($account.login -cne 'Eljah') {
        throw "Authenticated as $($account.login), expected Eljah. Run: gh auth switch --hostname github.com --user Eljah"
    }
    $login = [string]$account.login
    $email = "$($account.id)+$login@users.noreply.github.com"
    Invoke-Checked -Program git -ArgList @('init', '--initial-branch=main')
    Invoke-Checked -Program git -ArgList @('config', '--local', 'user.name', $login)
    Invoke-Checked -Program git -ArgList @('config', '--local', 'user.email', $email)
    Invoke-Checked -Program git -ArgList @('add', '--all')
    $shellFiles = @(Invoke-Checked -Program git -ArgList @('ls-files', '*.sh'))
    foreach ($file in $shellFiles) {
        if ($file) { Invoke-Checked -Program git -ArgList @('update-index', '--chmod=+x', '--', $file) }
    }
    Invoke-Checked -Program git -ArgList @('commit', '-m', 'Initial import: UBOR-JAVA R02 with WPILib, CAD, electronics and book')
    # gh refuses to create an already-existing repository; never delete or overwrite it.
    Invoke-Checked -Program gh -ArgList @('repo', 'create', 'Eljah/wali', "--$Visibility", '--source=.', '--remote=origin', '--push', '--description', 'Java litter-collection robot: CAD, electronics, Pi4J, DL4J and WPILib simulation')
    $localSha = (Invoke-Checked -Program git -ArgList @('rev-parse', 'HEAD') | Out-String).Trim()
    $remoteSha = (Invoke-Checked -Program gh -ArgList @('api', '--hostname', 'github.com', 'repos/Eljah/wali/git/ref/heads/main', '--jq', '.object.sha') | Out-String).Trim()
    if ($localSha -cne $remoteSha) { throw 'Remote main does not match the local commit. Publication is not confirmed.' }
    Write-Host "Created and verified Eljah/wali ($Visibility); commit $localSha"
    Invoke-Checked -Program gh -ArgList @('repo', 'view', 'Eljah/wali', '--json', 'nameWithOwner,url,isPrivate')
} finally {
    Pop-Location
    $env:GH_HOST = $previousHost
}
