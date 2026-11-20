# 👋 Contribution guidelines

This document describes how to contribute to Morphe Patches template.

## 📖 Resources to help you get started

* [Issues](https://github.com/wchill/patcheddit/issues) are where we keep track of bugs and feature requests

## 🙏 Submitting a feature request

Features can be requested by opening an issue using the
[Feature request issue template](https://github.com/wchill/patcheddit/issues/new?labels=Feature+request&template=feature_request.yml&title=feat%3A+).

## 🐞 Submitting a bug report

If you encounter a bug while using Patcheddit, open an issue using the
[Bug report issue template](https://github.com/wchill/patcheddit/issues/new?labels=Bug+report&template=bug_report.yml&title=bug%3A+).

## 📝 How to contribute

1. Before contributing, it is recommended to open an issue to discuss your change
with the maintainers of Patcheddit.
2. Development happens on the `dev` branch. Fork the repository and create your branch from `dev`
3. Commit your changes
4. Submit a pull request to the `dev` branch of the repository and reference issues
that your pull request closes in the description of your pull request
5. Our team will review your pull request and provide feedback. Once your pull request is approved,
it will be merged into the `dev` branch and will be included in the next release of Patcheddit.

## Updating Boost's library mapping

`scripts/update_boost_mapping.py` transfers the previous vetted `mapping.txt`
to a new stock Boost APK. It requires Python, the repository's Gradle/JDK setup
and dependencies, and Android SDK Build Tools (`aapt2`). Set `ANDROID_HOME` or
supply `--aapt2`; Gradle still needs its normal Android SDK configuration.
APKs are only parsed, never installed or executed.

```sh
python3 scripts/update_boost_mapping.py \
  --old-apk /path/to/boost-old.apk \
  --old-mapping /path/to/mapping-old.txt \
  --new-apk /path/to/boost-new.apk \
  --report build/boost-mapping-review.json \
  --output-mapping build/boost-mapping-proposed.txt
```

The script caches DEX inventories by APK checksum, extraction schema, and
extractor/dependency version under `build/boost-fingerprints`. Override this with
`--cache-dir`. It uses normalized instructions, registers, resources, control
flow and exception handlers, then propagates matching call/field references.
Preserved original API names and constant values provide additional anchors.
`--no-source-metadata` disables source-file-name hints.

The JSON report lists matches and their evidence, unresolved symbols with
review-only similarity candidates, conflicting correspondences, and matched
methods whose normalized bodies changed. Inlining, merging, missing
declarations, and ambiguous getters require review; similarity candidates are
never promoted automatically, and their scores are not confidence probabilities.
A match identifies a symbol, not unchanged
behavior. APK resources, reflection, native code, and patch behavior still need
their usual audits.

Exit status `0` means the mapping is complete with no body differences; `2`
means review is required; `1` means an input/extraction error or a mismatch
against an optional benchmark. An incomplete/conflicting mapping is not
emitted, and existing output files are left untouched. A complete proposed
mapping may still require review of body differences before release.

Use `--expected-mapping /path/to/reviewed-new-map.txt` to measure recovery
against a known release. That file is read only after matching and never used
to choose targets. This does not update `bindings.json`, patch compatibility,
or generated release metadata.

Run the focused tests with:

```sh
python3 -B -m unittest discover -s test -p test_update_boost_mapping.py
```

Set `BOOST_MAPPING_OLD_APK`, `BOOST_MAPPING_OLD_MAP`, `BOOST_APK`, and
`BOOST_MAPPING_EXPECTED` to the old APK/map and new APK/reviewed map for the
releases you want to compare. The recovery test checks every recovered target
against the supplied new mapping and derives counts and recovery rates from
those mappings.
Both complete and review-required results are supported.
Its report is saved to `build/boost-mapping-tests/recovery.json`.

## RIF inline-media device tests

Install the patched free and Platinum APKs, signed with the matching test keys.
The fixture exercises native body layouts, synthetic images/albums/video, gestures,
cache expiry, failure recovery, and player teardown. It restores changed preferences.

```sh
python3 -B test/run_device_tests.py \
  --sdk "$ANDROID_HOME" --serial "$ANDROID_SERIAL" \
  --package com.andrewshu.android.reddit \
  --package com.andrewshu.android.redditdonation \
  --suite app.patcheddit.fixture.RifInlineMediaDeviceTest \
  --source test/device/RifInlineMediaDeviceTest.java \
  --asset test/device/media/inline-video.mp4 \
  --asset test/device/media/inline-audio.m4a \
  --asset test/device/media/inline-animation.gif
```

By default, matching PKCS12 test keys are read from
`test/.cache/device-signers/PACKAGE.p12`; use `--keystore` to override.
These fixtures do not establish live-host availability or older Android decoder behavior.

## Boost inline-media device tests

With the patched Boost 1.14.4 APK installed and its matching test key available:

```sh
python3 -B test/run_device_tests.py \
  --sdk "$ANDROID_HOME" --serial "$ANDROID_SERIAL" \
  --package com.rubenmayayo.reddit \
  --suite app.patcheddit.fixture.BoostInlineMediaDeviceTest \
  --source test/device/BoostInlineMediaDeviceTest.java \
  --asset test/device/media/inline-animation.gif
adb -s "$ANDROID_SERIAL" shell am force-stop com.rubenmayayo.reddit
```

The fixture uses native Glide with synthetic responses and restores its test
preferences, including recovery after an interrupted run. Force-stop clears the
temporary transport before returning to normal use.

## Boost navigation device tests

On Android 11 or newer, install Boost 1.14.4 with the standard bottom-navigation
and navigation-overlap patches. Run this fixture in both gesture and three-button
navigation modes. It checks that stale system-bar surfaces cannot cover the native
controls, that surfaces follow changing and zero bottom insets, and that disabling
navigation survives later inset dispatches. Changed preferences are restored.

```sh
python3 -B test/run_device_tests.py \
  --sdk "$ANDROID_HOME" --serial "$ANDROID_SERIAL" \
  --package com.rubenmayayo.reddit \
  --suite app.patcheddit.fixture.BoostNavigationInsetsDeviceTest \
  --source test/device/BoostNavigationInsetsDeviceTest.java \
  --keystore /path/to/boost-test-key.p12
```

The test key must match the installed APK's signature.

❤️ Thank you for considering contributing to Patcheddit
