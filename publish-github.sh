#!/usr/bin/env bash
set -euo pipefail
visibility="${1:-private}"
case "$visibility" in private|public) ;; *) echo 'Usage: bash publish-github.sh [private|public]' >&2; exit 2;; esac
for tool in git gh; do
  command -v "$tool" >/dev/null || { echo "Install $tool first." >&2; exit 2; }
done
root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$root"
for file in README.md software/pom.xml software/robot-wpilib/pom.xml docs/UBOR_JAVA_BOOK_R02.pdf; do
  [[ -f "$file" ]] || { echo "Missing project file: $file" >&2; exit 2; }
done
[[ ! -e .git ]] || { echo 'A local .git already exists. See PUBLISH_GITHUB_RU.md; nothing was changed.' >&2; exit 2; }
export GH_HOST=github.com
login="$(gh api --hostname github.com user --jq .login)"
[[ "$login" == Eljah ]] || { echo "Authenticated as $login, expected Eljah. Use gh auth switch." >&2; exit 2; }
uid="$(gh api --hostname github.com user --jq .id)"
[[ "$uid" =~ ^[0-9]+$ ]] || { echo 'Invalid GitHub user id.' >&2; exit 2; }
git init --initial-branch=main
git config --local user.name "$login"
git config --local user.email "${uid}+${login}@users.noreply.github.com"
git add --all
while IFS= read -r -d '' file; do git update-index --chmod=+x -- "$file"; done < <(git ls-files -z '*.sh')
git commit -m 'Initial import: UBOR-JAVA R02 with WPILib, CAD, electronics and book'
# gh refuses to create an existing repository; there is no delete or force-push.
gh repo create Eljah/wali "--$visibility" --source=. --remote=origin --push \
  --description 'Java litter-collection robot: CAD, electronics, Pi4J, DL4J and WPILib simulation'
local_sha="$(git rev-parse HEAD)"
remote_sha="$(gh api --hostname github.com repos/Eljah/wali/git/ref/heads/main --jq .object.sha)"
[[ "$local_sha" == "$remote_sha" ]] || { echo 'Remote main differs; publication not confirmed.' >&2; exit 1; }
echo "Created and verified Eljah/wali ($visibility); commit $local_sha"
gh repo view Eljah/wali --json nameWithOwner,url,isPrivate
