#!/usr/bin/env python3
"""Import the original wali package without exposing its short-lived download URL.

An ephemeral RSA key remains only on the runner. The authenticated publisher sends
OAEP-encrypted URL chunks; neither an access token nor a plaintext URL is committed.
Only this exact SHA-256-identified archive is accepted. Existing Git history stays.
"""
import base64
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import shutil
import stat
import subprocess
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
import zipfile

REPO = "Eljah/wali"
EXPECTED_SHA256 = "9a66c389bd0a0f7488ee5cc6723fb9fcbf9e8fed8d08fbb66c7742066980ac96"
EXPECTED_BYTES = 5418822
EXPECTED_FILES = 316
API = "https://api.github.com/repos/" + REPO
ROOT = Path.cwd()
TOKEN = os.environ["GH_TOKEN"]


def api(method, path, body=None):
    request = urllib.request.Request(
        API + path,
        data=None if body is None else json.dumps(body).encode(),
        method=method,
        headers={"Authorization": "Bearer " + TOKEN,
                 "Accept": "application/vnd.github+json",
                 "X-GitHub-Api-Version": "2022-11-28",
                 "Content-Type": "application/json",
                 "User-Agent": "wali-verified-package-import"})
    try:
        with urllib.request.urlopen(request, timeout=45) as response:
            return json.load(response)
    except urllib.error.HTTPError as error:
        if method == "GET" and error.code == 404:
            return None
        raise RuntimeError("GitHub API operation failed with HTTP " + str(error.code)) from None


def read_file(path):
    result = api("GET", "/contents/" + path + "?ref=main")
    if result is None:
        return None
    return json.loads(base64.b64decode(result["content"]))


def publish_receiver(data):
    path = ".github/import/receiver.json"
    previous = api("GET", "/contents/" + path + "?ref=main")
    body = {"branch": "main", "message": "Publish ephemeral import receiver public key",
            "content": base64.b64encode(json.dumps(data, indent=2).encode()).decode()}
    if previous:
        body["sha"] = previous["sha"]
    api("PUT", "/contents/" + path, body)


def git(*arguments):
    return subprocess.check_output(["git", *arguments], text=True).strip()


def main():
    if os.environ.get("GITHUB_REPOSITORY", "").lower() != REPO.lower():
        raise RuntimeError("Wrong repository")
    session = uuid.uuid4().hex
    envelope_path = ".github/import/package-" + session + ".json"
    with tempfile.TemporaryDirectory(prefix="wali-private-", dir=os.environ.get("RUNNER_TEMP")) as td:
        temporary = Path(td)
        private = temporary / "receiver.pem"
        subprocess.run(["openssl", "genpkey", "-algorithm", "RSA", "-pkeyopt",
                        "rsa_keygen_bits:3072", "-out", str(private)],
                       check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        private.chmod(0o600)
        public_key = subprocess.check_output(
            ["openssl", "pkey", "-in", str(private), "-pubout"], text=True)
        publish_receiver({"session": session, "run_id": os.environ["GITHUB_RUN_ID"],
                          "public_key_pem": public_key, "envelope_path": envelope_path,
                          "encryption": "RSA-3072-OAEP-SHA256; independent chunks <=318 bytes",
                          "expected_archive_sha256": EXPECTED_SHA256,
                          "expected_archive_bytes": EXPECTED_BYTES,
                          "expected_source_files": EXPECTED_FILES})
        print("Ephemeral receiver ready; waiting for encrypted package location.", flush=True)
        deadline = time.monotonic() + 600
        envelope = None
        while time.monotonic() < deadline:
            envelope = read_file(envelope_path)
            if envelope and envelope.get("session") == session:
                break
            time.sleep(4)
        else:
            raise RuntimeError("Encrypted package handoff was not received")
        parts = envelope.get("rsa_oaep_sha256_chunks", [])
        if not 1 <= len(parts) <= 16:
            raise RuntimeError("Invalid encrypted handoff")
        plaintext = b""
        for part in parts:
            encrypted = base64.b64decode(part, validate=True)
            if len(encrypted) != 384:
                raise RuntimeError("Invalid RSA ciphertext size")
            plaintext += subprocess.check_output(
                ["openssl", "pkeyutl", "-decrypt", "-inkey", str(private),
                 "-pkeyopt", "rsa_padding_mode:oaep", "-pkeyopt", "rsa_oaep_md:sha256",
                 "-pkeyopt", "rsa_mgf1_md:sha256"], input=encrypted, stderr=subprocess.DEVNULL)
        location = json.loads(plaintext)
        if location.get("session") != session or location.get("sha256") != EXPECTED_SHA256:
            raise RuntimeError("Handoff session or archive identity mismatch")
        url = location["url"]
        parsed = urllib.parse.urlsplit(url)
        if parsed.scheme != "https" or not parsed.hostname or not parsed.hostname.endswith(".oaiusercontent.com"):
            raise RuntimeError("Unexpected source download host")
        print("::add-mask::" + url, flush=True)
        package = temporary / "package.zip"
        try:
            with urllib.request.urlopen(url, timeout=90) as response:
                data = response.read(EXPECTED_BYTES + 1)
        except Exception:
            raise RuntimeError("Temporary archive download failed; source URL was not logged") from None
        if len(data) != EXPECTED_BYTES or hashlib.sha256(data).hexdigest() != EXPECTED_SHA256:
            raise RuntimeError("Archive length or SHA-256 mismatch")
        package.write_bytes(data)
        staged = temporary / "source"
        staged.mkdir()
        source_manifest = {}
        with zipfile.ZipFile(package) as archive:
            entries = archive.infolist()
            if len(entries) != EXPECTED_FILES or sum(item.file_size for item in entries) > 25000000:
                raise RuntimeError("Unexpected archive contents")
            for item in entries:
                path = PurePosixPath(item.filename)
                if (item.is_dir() or path.is_absolute() or ".." in path.parts or
                        "\\" in item.filename or path.parts[0] != "wali" or
                        len(path.parts) < 2 or ".git" in path.parts or
                        stat.S_ISLNK(item.external_attr >> 16)):
                    raise RuntimeError("Unsafe archive member")
                name = PurePosixPath(*path.parts[1:]).as_posix()
                if name in source_manifest:
                    raise RuntimeError("Duplicate archive member")
                contents = archive.read(item)
                destination = staged / name
                destination.parent.mkdir(parents=True, exist_ok=True)
                destination.write_bytes(contents)
                source_manifest[name] = {
                    "bytes": len(contents), "sha256": hashlib.sha256(contents).hexdigest(),
                    "git_blob_sha1": hashlib.sha1(b"blob " + str(len(contents)).encode() + b"\0" + contents).hexdigest()}
        checked_legacy_manifest = 0
        for line in (staged / "MANIFEST_SHA256.txt").read_text().splitlines():
            if not line.strip():
                continue
            expected, name = line.split("  ", 1)
            if name not in source_manifest or source_manifest[name]["sha256"] != expected:
                raise RuntimeError("Original project manifest mismatch: " + name)
            checked_legacy_manifest += 1
        print("Exact original archive verified: %d files, %d legacy manifest entries." %
              (len(source_manifest), checked_legacy_manifest), flush=True)
        # The receiver and encrypted envelope added commits while this job was running.
        git("pull", "--ff-only", "origin", "main")
        parent = git("rev-parse", "HEAD")
        preserved = []
        lower_readme = ROOT / "readme.md"
        if lower_readme.exists():
            saved = ROOT / "docs" / "ORIGINAL_REPOSITORY_README.md"
            if saved.exists():
                raise RuntimeError("Refusing to overwrite preserved original README")
            saved.parent.mkdir(parents=True, exist_ok=True)
            shutil.move(str(lower_readme), str(saved))
            preserved.append("docs/ORIGINAL_REPOSITORY_README.md")
        for name in source_manifest:
            destination = ROOT / name
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(staged / name, destination)
            if name.endswith(".sh"):
                destination.chmod(0o755)
        # No raw archive, credentials, private key, or active bearer URL enters Git.
        for name in [".github/import/receiver.json", envelope_path, ".github/import/READY"]:
            (ROOT / name).unlink(missing_ok=True)
        verification = ROOT / "verification"
        verification.mkdir(exist_ok=True)
        (verification / "github-source-manifest.json").write_text(
            json.dumps(source_manifest, indent=2, sort_keys=True) + "\n")
        git("add", "--all")
        indexed = {}
        for entry in subprocess.check_output(["git", "ls-files", "-s", "-z"]).split(b"\0"):
            if entry:
                meta, name = entry.split(b"\t", 1)
                indexed[name.decode()] = meta.split()[1].decode()
        for name, info in source_manifest.items():
            if indexed.get(name) != info["git_blob_sha1"]:
                raise RuntimeError("Git index differs from original file: " + name)
        report = {"repository": REPO, "branch": "main", "import_parent_commit": parent,
                  "workflow_run_id": os.environ["GITHUB_RUN_ID"],
                  "archive_sha256": EXPECTED_SHA256, "archive_bytes": EXPECTED_BYTES,
                  "source_files": len(source_manifest), "verified_git_index_files": len(source_manifest),
                  "original_manifest_entries_verified": checked_legacy_manifest,
                  "source_bytes_preserved": True, "force_push_used": False,
                  "preserved_preexisting_files": preserved,
                  "verification_scope": "Publication integrity only; no new WPILib/ML/hardware tests were run.",
                  "historical_reports": "PUBLICATION_CHECKS.json and PUBLICATION_PACKAGE.txt are unchanged pre-publication snapshots."}
        (verification / "github-import.json").write_text(json.dumps(report, indent=2) + "\n")
        (ROOT / "GITHUB_PUBLICATION.md").write_text(
            "# GitHub publication\n\nThe original wali/UBOR-JAVA R02 package was imported as ordinary files. "
            "All 316 source files were checked byte-for-byte against the exact original archive, "
            "including the 307 entries of its original manifest.\n\n"
            "See verification/github-import.json and verification/github-source-manifest.json. "
            "The pre-publication reports remain unchanged historical records. "
            "This import does not establish that WPILib, DL4J or physical hardware tests passed.\n\n"
            "The pre-existing lowercase readme.md was preserved as docs/ORIGINAL_REPOSITORY_README.md "
            "to avoid a case-insensitive filesystem collision. No force-push was used.\n")
        git("add", "--all")
        print("Git index verified against all 316 original files; ready for commit.", flush=True)


if __name__ == "__main__":
    main()
