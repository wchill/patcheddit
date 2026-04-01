#!/usr/bin/env python3
"""
Rebuilds the patch bundle and runs the morphe-cli patcher against all APKs in test/.
"""

import json
import subprocess
import sys
import urllib.request
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
PROJECT_DIR = SCRIPT_DIR.parent
PATCHES_LIBS = PROJECT_DIR / "patches" / "build" / "libs"
TEST_DIR = SCRIPT_DIR
CACHE_DIR = SCRIPT_DIR / ".cache"
OUTPUT_DIR = SCRIPT_DIR / "output"

GITHUB_RELEASES_URL = (
    "https://api.github.com/repos/MorpheApp/morphe-cli/releases?per_page=20"
)


def download_latest_cli_jar() -> Path:
    """
    Download the latest morphe-cli fat jar from GitHub releases (stable or pre-release).

    Returns a cached copy if the same version was already downloaded.
    """
    print("Fetching latest morphe-cli release from GitHub ...")
    req = urllib.request.Request(
        GITHUB_RELEASES_URL,
        headers={"Accept": "application/vnd.github+json"},
    )
    with urllib.request.urlopen(req) as resp:
        releases = json.loads(resp.read())

    if not releases:
        print("ERROR: No releases found on GitHub", file=sys.stderr)
        sys.exit(1)

    # The API returns releases newest-first; pick the first one (stable or pre-release).
    release = releases[0]
    tag = release["tag_name"]
    assets = [a for a in release["assets"] if a["name"].endswith("-all.jar")]
    if not assets:
        print(
            f"ERROR: Release {tag} has no fat jar (*-all.jar) asset",
            file=sys.stderr,
        )
        sys.exit(1)

    asset = assets[0]
    asset_name: str = asset["name"]
    download_url: str = asset["browser_download_url"]

    CACHE_DIR.mkdir(parents=True, exist_ok=True)
    cached_jar = CACHE_DIR / asset_name

    if cached_jar.exists():
        print(f"Using cached jar:     {cached_jar}  (release {tag})")
        return cached_jar

    print(f"Downloading {asset_name} from release {tag} ...")
    urllib.request.urlretrieve(download_url, cached_jar)
    print(f"Saved to:             {cached_jar}")
    return cached_jar


def find_latest_patch_bundle(directory: Path) -> Path:
    """Find the most recently modified *.mpp in the given directory."""
    bundles = list(directory.glob("*.mpp"))
    if not bundles:
        print(f"ERROR: No patch bundle (*.mpp) found in {directory}", file=sys.stderr)
        sys.exit(1)
    latest = max(bundles, key=lambda p: p.stat().st_mtime)
    print(f"Using patch bundle:   {latest}")
    return latest


def rebuild_patches() -> None:
    """Run ./gradlew buildAndroid to rebuild the patch bundle."""
    print("=" * 60)
    print("Rebuilding patch bundle (./gradlew buildAndroid) ...")
    print("=" * 60)
    result = subprocess.run(
        ["./gradlew", "buildAndroid"],
        cwd=PROJECT_DIR,
    )
    if result.returncode != 0:
        print("ERROR: Gradle build failed", file=sys.stderr)
        sys.exit(1)
    print()


def patch_apk(jar: Path, patches: Path, apk: Path) -> subprocess.CompletedProcess:
    """Run morphe-cli patch on a single APK."""
    with open(TEST_DIR / "config.json", "r") as f:
        config = json.load(f)
    cmd = [
        "java", "-jar", str(jar),
        "patch",
        "--options-file=" + str(TEST_DIR / "config.json"),
        f"--patches={patches}",
        str(apk),
    ]
    print(f"  Command: {' '.join(cmd)}")
    return subprocess.run(cmd, cwd=OUTPUT_DIR)


def main() -> None:
    # 1. Rebuild patches
    rebuild_patches()

    # 2. Resolve paths
    jar = download_latest_cli_jar()
    patches = find_latest_patch_bundle(PATCHES_LIBS)
    print()

    # 3. Collect APKs
    apks = sorted(TEST_DIR.glob("*.apk"))
    if not apks:
        print(f"No APK files found in {TEST_DIR}", file=sys.stderr)
        sys.exit(1)

    print(f"Found {len(apks)} APK(s) in {TEST_DIR}:\n")
    for apk in apks:
        print(f"  - {apk.name}")
    print()

    # 4. Patch each APK
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    results: dict[str, bool] = {}
    for i, apk in enumerate(apks, 1):
        print("=" * 60)
        print(f"[{i}/{len(apks)}] Patching: {apk.name}")
        print("=" * 60)
        proc = patch_apk(jar, patches, apk)
        results[apk.name] = proc.returncode == 0
        print()

    # 5. Summary
    print("=" * 60)
    print("SUMMARY")
    print("=" * 60)
    for name, success in results.items():
        status = "✓ OK" if success else "✗ FAILED"
        print(f"  [{status}] {name}")

    failed = sum(1 for s in results.values() if not s)
    if failed:
        print(f"\n{failed}/{len(results)} APK(s) failed.")
        sys.exit(1)
    else:
        print(f"\nAll {len(results)} APK(s) patched successfully.")


if __name__ == "__main__":
    main()
