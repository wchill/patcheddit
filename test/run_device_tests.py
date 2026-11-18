#!/usr/bin/env python3
"""Run native fixture suites inside installed, Morphe-signed Android apps."""

import argparse
import os
from pathlib import Path
import subprocess
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parent
ANDROID = "http://schemas.android.com/apk/res/android"
INSTRUMENTATION = "app.patcheddit.fixture"
ET.register_namespace("android", ANDROID)


def run(*command: str) -> str:
    result = subprocess.run(command, check=True, text=True, capture_output=True)
    return result.stdout + result.stderr


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--sdk", type=Path, required=True)
    parser.add_argument("--build-tools", default="36.1.0")
    parser.add_argument("--platform", default="android-36")
    parser.add_argument("--serial", required=True)
    parser.add_argument("--package", action="append", required=True)
    parser.add_argument("--suite", required=True)
    parser.add_argument("--source", type=Path, action="append", required=True)
    parser.add_argument("--keystore", type=Path,
                        help="PKCS12 test key (Morphe password); defaults to .cache/device-signers/PACKAGE.p12")
    parser.add_argument("--arg", action="append", default=[], metavar="KEY=VALUE")
    args = parser.parse_args()
    tools = args.sdk / "build-tools" / args.build_tools
    android = args.sdk / "platforms" / args.platform / "android.jar"
    libraries = [android]
    mock = android.parent / "optional" / "android.test.mock.jar"
    if mock.is_file():
        libraries.append(mock)
    output = ROOT / "output" / "device-tests"
    classes = output / args.suite / "classes"
    dex = output / args.suite / "dex"
    classes.mkdir(parents=True, exist_ok=True)
    dex.mkdir(parents=True, exist_ok=True)
    sources = [ROOT / "device" / "PatchedditInstrumentation.java", *args.source]
    run("javac", "--release", "17", "-cp", os.pathsep.join(map(str, libraries)), "-d", str(classes),
        *(str(path) for path in sources))
    run(str(tools / "d8"), *(argument for library in libraries for argument in ("--lib", str(library))),
        "--output", str(dex),
        *(str(path) for path in classes.rglob("*.class")))
    adb = ["adb", "-s", args.serial]
    run(*adb, "shell", "svc", "power", "stayon", "true")
    run(*adb, "shell", "input", "keyevent", "KEYCODE_WAKEUP")
    extras = []
    for argument in args.arg:
        key, value = argument.split("=", 1)
        extras.extend(("-e", key, value))
    installed = False
    try:
        for package in args.package:
            manifest = ET.Element("manifest", {"package": INSTRUMENTATION})
            ET.SubElement(manifest, "uses-sdk", {
                f"{{{ANDROID}}}minSdkVersion": "23",
                f"{{{ANDROID}}}targetSdkVersion": "36",
            })
            application = ET.SubElement(manifest, "application", {
                f"{{{ANDROID}}}label": "Patcheddit fixtures",
                f"{{{ANDROID}}}debuggable": "true",
            })
            ET.SubElement(application, "uses-library", {
                f"{{{ANDROID}}}name": "android.test.mock",
                f"{{{ANDROID}}}required": "false",
            })
            ET.SubElement(manifest, "instrumentation", {
                f"{{{ANDROID}}}name": f"{INSTRUMENTATION}.PatchedditInstrumentation",
                f"{{{ANDROID}}}targetPackage": package,
            })
            manifest_path = output / "AndroidManifest.xml"
            ET.ElementTree(manifest).write(manifest_path, encoding="utf-8", xml_declaration=True)
            unsigned = output / "fixture-unsigned.apk"
            signed = output / "fixture.apk"
            run(str(tools / "aapt2"), "link", "-I", str(android),
                "--manifest", str(manifest_path), "-o", str(unsigned))
            with zipfile.ZipFile(unsigned, "a") as apk:
                apk.write(dex / "classes.dex", "classes.dex")
            run(str(tools / "zipalign"), "-f", "-p", "4", str(unsigned), str(signed))
            run(str(tools / "apksigner"), "sign", "--ks",
                str(args.keystore or ROOT / ".cache" / "device-signers" / f"{package}.p12"),
                "--ks-pass", "pass:Morphe", "--key-pass", "pass:Morphe", str(signed))
            if installed:
                run(*adb, "uninstall", INSTRUMENTATION)
                installed = False
            run(*adb, "install", "--no-incremental", "-r", str(signed))
            installed = True
            result = run(*adb, "shell", "am", "instrument", "-r", "-w",
                         "-e", "suite", args.suite, *extras,
                         f"{INSTRUMENTATION}/.PatchedditInstrumentation")
            print(f"{package}: {result}", flush=True)
            if "INSTRUMENTATION_CODE: -1" not in result or "PASS " + package not in result:
                raise RuntimeError(f"Device fixture failed for {package}")
    finally:
        if installed:
            print(run(*adb, "uninstall", INSTRUMENTATION), end="")


if __name__ == "__main__":
    try:
        main()
    except subprocess.CalledProcessError as error:
        print(error.stdout or "", end="")
        print(error.stderr or "", end="")
        raise
