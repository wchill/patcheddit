## [2.5.2-dev.1](https://github.com/wchill/patcheddit/compare/v2.5.1...v2.5.2-dev.1) (2026-11-23)


### Bug Fixes

* **relay:** Hook additional comment renderers for inline media ([c21e1b1](https://github.com/wchill/patcheddit/commit/c21e1b1e5445ded665e760e4c200bfe4d6aa8cbb))
* Remove Sync for Lemmy support ([0e39e4a](https://github.com/wchill/patcheddit/commit/0e39e4a6d30a2b820151f1e5907053c052fc79fc))

## [2.5.1](https://github.com/wchill/patcheddit/compare/v2.5.0...v2.5.1) (2026-11-23)


### Bug Fixes

* **infinity:** Resolve crash due to unspecified color when opening Patcheddit settings ([f615b97](https://github.com/wchill/patcheddit/commit/f615b97d3f3870989e17a8d1cb36a3dc2d3e1d8f))
* **rif:** Resolved crashing from inline video handling ([4cf9bb0](https://github.com/wchill/patcheddit/commit/4cf9bb0772f8c65ae328f434ec9b63167f2c5b42))
* **rif:** Resolved HTTP 403 Forbidden on anonymous mode browsing ([4e8441a](https://github.com/wchill/patcheddit/commit/4e8441a2ae10f10a62e8e21a90c54c32162744d1))

## [2.5.1-dev.1](https://github.com/wchill/patcheddit/compare/v2.5.0...v2.5.1-dev.1) (2026-11-23)


### Bug Fixes

* **infinity:** Resolve crash due to unspecified color when opening Patcheddit settings ([f615b97](https://github.com/wchill/patcheddit/commit/f615b97d3f3870989e17a8d1cb36a3dc2d3e1d8f))
* **rif:** Resolved crashing from inline video handling ([4cf9bb0](https://github.com/wchill/patcheddit/commit/4cf9bb0772f8c65ae328f434ec9b63167f2c5b42))
* **rif:** Resolved HTTP 403 Forbidden on anonymous mode browsing ([4e8441a](https://github.com/wchill/patcheddit/commit/4e8441a2ae10f10a62e8e21a90c54c32162744d1))

# [2.5.0](https://github.com/wchill/patcheddit/compare/v2.4.0...v2.5.0) (2026-11-22)


### Bug Fixes

* **boost:** Make status bar opaque in subreddit search results ([4315ee9](https://github.com/wchill/patcheddit/commit/4315ee98c2d272327a42b0c50c72d86305bd6f91))
* **boost:** navigation bar no longer covered by system gesture bar ([71d8b17](https://github.com/wchill/patcheddit/commit/71d8b17297e4b884cf44b45d51c19aaef1c75314))
* **boost:** Update Google reverse image search URL ([ebe3b3b](https://github.com/wchill/patcheddit/commit/ebe3b3bf0b15edf7db213c4830d357fb8f18b07e))
* explain signed-in Reddit feed blocks in RIF ([71c1b34](https://github.com/wchill/patcheddit/commit/71c1b34adc3a0ee278a9a9344e84546429cd2712))
* preserve Markdown repair compatibility with Android 6 ([1ca00f7](https://github.com/wchill/patcheddit/commit/1ca00f7a43bbc718aa7b2a2ae15b20b5eb17cbcb))
* **relay:** Handle more filetypes in native image viewer ([9cf3f41](https://github.com/wchill/patcheddit/commit/9cf3f41887144419de1a5f38a20991d9af4898c8))
* repair relative markdown links on all Reddit clients ([073cd3d](https://github.com/wchill/patcheddit/commit/073cd3dc2f37b28651c3af3764cb7c163069c7a7))
* restore comment video streams in RIF, Boost, and Joey ([abe4047](https://github.com/wchill/patcheddit/commit/abe40475ea8e0eb5671d2030eb9bb00071fa2f95))
* restore RIF browsing history during settings import ([dbfa4f5](https://github.com/wchill/patcheddit/commit/dbfa4f55cf709807732ce1a6ddcf3bb7fd0678a8))
* **rif:** block ads and fix crash on Platinum version ([76e649a](https://github.com/wchill/patcheddit/commit/76e649a4a478247644c3e927fe2c5cd4ad30f08f))
* **sync:** Crash when tapping toolbar buttons ([ef79a18](https://github.com/wchill/patcheddit/commit/ef79a188c49acbd3ebce3db3df7968d168247e6a))


### Features

* add account-aware profile posting to RIF ([c3bd09c](https://github.com/wchill/patcheddit/commit/c3bd09cb4a5b6bb41efde02014665d6e4a2461e5))
* Add permanent NSFW filtering option ([febb0c7](https://github.com/wchill/patcheddit/commit/febb0c78435c39748290660c1c3db7fcf7122960))
* Allow deleting of saved exception/crash reports ([cc90196](https://github.com/wchill/patcheddit/commit/cc901967075806ef4df6aeba895a1873fc3bfc11))
* **boost:** Add ability to backup/restore full viewing history ([49e28c1](https://github.com/wchill/patcheddit/commit/49e28c12e1f850fc8f6626d09b13a788e62e24c9))
* **boost:** Load all inline media ([b983b21](https://github.com/wchill/patcheddit/commit/b983b21a190d730066faacae213e392c9643e40f))
* **boost:** Remember collapsed comments ([98ed133](https://github.com/wchill/patcheddit/commit/98ed133d382ce176c6d16056423c951fc3b9b195))
* **relay:** Add inline media ([855249d](https://github.com/wchill/patcheddit/commit/855249da8eb09eef82f5d23dfc6d87ef34d3a1a5))
* **rif:** Add inline media support ([220dd17](https://github.com/wchill/patcheddit/commit/220dd17f63d0cba2ab98b357a50673abeec06535))
* Use new embedded YouTube player for RIF and Sync ([6f0982a](https://github.com/wchill/patcheddit/commit/6f0982a974257e8270840604714cea2048e96071))

# [2.5.0-dev.6](https://github.com/wchill/patcheddit/compare/v2.5.0-dev.5...v2.5.0-dev.6) (2026-11-22)


### Features

* Use new embedded YouTube player for RIF and Sync ([6f0982a](https://github.com/wchill/patcheddit/commit/6f0982a974257e8270840604714cea2048e96071))

# [2.5.0-dev.5](https://github.com/wchill/patcheddit/compare/v2.5.0-dev.4...v2.5.0-dev.5) (2026-11-21)


### Bug Fixes

* **sync:** Crash when tapping toolbar buttons ([ef79a18](https://github.com/wchill/patcheddit/commit/ef79a188c49acbd3ebce3db3df7968d168247e6a))


### Features

* Add permanent NSFW filtering option ([febb0c7](https://github.com/wchill/patcheddit/commit/febb0c78435c39748290660c1c3db7fcf7122960))
* Allow deleting of saved exception/crash reports ([cc90196](https://github.com/wchill/patcheddit/commit/cc901967075806ef4df6aeba895a1873fc3bfc11))

# [2.5.0-dev.4](https://github.com/wchill/patcheddit/compare/v2.5.0-dev.3...v2.5.0-dev.4) (2026-11-21)


### Bug Fixes

* **boost:** Make status bar opaque in subreddit search results ([4315ee9](https://github.com/wchill/patcheddit/commit/4315ee98c2d272327a42b0c50c72d86305bd6f91))
* **boost:** Update Google reverse image search URL ([ebe3b3b](https://github.com/wchill/patcheddit/commit/ebe3b3bf0b15edf7db213c4830d357fb8f18b07e))


### Features

* **boost:** Add ability to backup/restore full viewing history ([49e28c1](https://github.com/wchill/patcheddit/commit/49e28c12e1f850fc8f6626d09b13a788e62e24c9))
* **boost:** Remember collapsed comments ([98ed133](https://github.com/wchill/patcheddit/commit/98ed133d382ce176c6d16056423c951fc3b9b195))

# [2.5.0-dev.3](https://github.com/wchill/patcheddit/compare/v2.5.0-dev.2...v2.5.0-dev.3) (2026-11-21)


### Bug Fixes

* **relay:** Handle more filetypes in native image viewer ([9cf3f41](https://github.com/wchill/patcheddit/commit/9cf3f41887144419de1a5f38a20991d9af4898c8))


### Features

* **relay:** Add inline media ([855249d](https://github.com/wchill/patcheddit/commit/855249da8eb09eef82f5d23dfc6d87ef34d3a1a5))

# [2.5.0-dev.2](https://github.com/wchill/patcheddit/compare/v2.5.0-dev.1...v2.5.0-dev.2) (2026-11-20)


### Bug Fixes

* **boost:** navigation bar no longer covered by system gesture bar ([71d8b17](https://github.com/wchill/patcheddit/commit/71d8b17297e4b884cf44b45d51c19aaef1c75314))


### Features

* **boost:** Load all inline media ([b983b21](https://github.com/wchill/patcheddit/commit/b983b21a190d730066faacae213e392c9643e40f))
* **rif:** Add inline media support ([220dd17](https://github.com/wchill/patcheddit/commit/220dd17f63d0cba2ab98b357a50673abeec06535))

# [2.5.0-dev.1](https://github.com/wchill/patcheddit/compare/v2.4.1-dev.1...v2.5.0-dev.1) (2026-11-18)


### Bug Fixes

* explain signed-in Reddit feed blocks in RIF ([71c1b34](https://github.com/wchill/patcheddit/commit/71c1b34adc3a0ee278a9a9344e84546429cd2712))
* restore RIF browsing history during settings import ([dbfa4f5](https://github.com/wchill/patcheddit/commit/dbfa4f55cf709807732ce1a6ddcf3bb7fd0678a8))


### Features

* add account-aware profile posting to RIF ([c3bd09c](https://github.com/wchill/patcheddit/commit/c3bd09cb4a5b6bb41efde02014665d6e4a2461e5))

## [2.4.1-dev.1](https://github.com/wchill/patcheddit/compare/v2.4.0...v2.4.1-dev.1) (2026-11-18)


### Bug Fixes

* preserve Markdown repair compatibility with Android 6 ([1ca00f7](https://github.com/wchill/patcheddit/commit/1ca00f7a43bbc718aa7b2a2ae15b20b5eb17cbcb))
* repair relative markdown links on all Reddit clients ([073cd3d](https://github.com/wchill/patcheddit/commit/073cd3dc2f37b28651c3af3764cb7c163069c7a7))
* restore comment video streams in RIF, Boost, and Joey ([abe4047](https://github.com/wchill/patcheddit/commit/abe40475ea8e0eb5671d2030eb9bb00071fa2f95))
* **rif:** block ads and fix crash on Platinum version ([76e649a](https://github.com/wchill/patcheddit/commit/76e649a4a478247644c3e927fe2c5cd4ad30f08f))

# [2.4.0](https://github.com/wchill/patcheddit/compare/v2.3.0...v2.4.0) (2026-11-17)


### Bug Fixes

* **boost:** Add backwards compatibility Spoof client patch for users not using RedReader client ID ([3566b1f](https://github.com/wchill/patcheddit/commit/3566b1fb0ddf4b8926ca0edf43baf09ddc4eb92f))
* **sync:** Crash in Sync free when opening Settings menu due to empty drawable ([6f9d17e](https://github.com/wchill/patcheddit/commit/6f9d17ed9d02a66a9935fd04eca6e2b32beb9651))


### Features

* Add About Patcheddit menu ([61284eb](https://github.com/wchill/patcheddit/commit/61284eb091a669219f70cd96fab798ddf444da4f))
* **boost:** Add ability to manually fetch comments/posts from Arctic Shift and warn when loading failed ([cc20250](https://github.com/wchill/patcheddit/commit/cc20250bfbd4dd8b08ea5fe72c4751717d774e37))
* **boost:** Make Arctic Shift URL configurable for development purposes ([342ced9](https://github.com/wchill/patcheddit/commit/342ced923dc33d2a97d9482c7850c4dff4dc0871))

# [2.4.0-dev.1](https://github.com/wchill/patcheddit/compare/v2.3.0...v2.4.0-dev.1) (2026-11-17)


### Bug Fixes

* **boost:** Add backwards compatibility Spoof client patch for users not using RedReader client ID ([3566b1f](https://github.com/wchill/patcheddit/commit/3566b1fb0ddf4b8926ca0edf43baf09ddc4eb92f))
* **sync:** Crash in Sync free when opening Settings menu due to empty drawable ([6f9d17e](https://github.com/wchill/patcheddit/commit/6f9d17ed9d02a66a9935fd04eca6e2b32beb9651))


### Features

* Add About Patcheddit menu ([61284eb](https://github.com/wchill/patcheddit/commit/61284eb091a669219f70cd96fab798ddf444da4f))
* **boost:** Add ability to manually fetch comments/posts from Arctic Shift and warn when loading failed ([cc20250](https://github.com/wchill/patcheddit/commit/cc20250bfbd4dd8b08ea5fe72c4751717d774e37))
* **boost:** Make Arctic Shift URL configurable for development purposes ([342ced9](https://github.com/wchill/patcheddit/commit/342ced923dc33d2a97d9482c7850c4dff4dc0871))

# [2.3.0](https://github.com/wchill/patcheddit/compare/v2.2.0...v2.3.0) (2026-11-16)


### Bug Fixes

* **boost:** Make colored bars/lines repeat for very deep comment chains ([42ea130](https://github.com/wchill/patcheddit/commit/42ea130971056571e79e93c46f2c521003ad12b5))
* Flatten comment requests for improved performance ([0ad786b](https://github.com/wchill/patcheddit/commit/0ad786b55655095c111c5d53ae80082cabd72c20))
* **joey:** Restore Redgifs playback through shared HTTP repairs ([de6e413](https://github.com/wchill/patcheddit/commit/de6e4133b849436757c3fc91afec048941dc2cea))
* **redgifs:** Repair legacy authentication and token refresh ([e58194e](https://github.com/wchill/patcheddit/commit/e58194e025af8d7fd936095391cc74b08a1a16ab))
* **relay:** Restore native Redgifs playback with audio ([7e674e5](https://github.com/wchill/patcheddit/commit/7e674e5f7f51e715ab0f7e5f88e111b18d628e23))
* **relay:** Restore sidebars when Reddit omits activity counters ([87a4936](https://github.com/wchill/patcheddit/commit/87a4936263c0f3666edcd485a35c751a5e9028b9))
* Repair links in comment/post bodies that were escaped by New Reddit ([b105de4](https://github.com/wchill/patcheddit/commit/b105de4a4a786520e653252c6e0c831c07cce3bc))
* Restore swipe-back transparency in Sync and Joey ([fdfba21](https://github.com/wchill/patcheddit/commit/fdfba2138fecda0616a3fb224ec541b44af8f7cf))
* **rif:** Imgur album loading for albums with 5 character IDs ([d50d802](https://github.com/wchill/patcheddit/commit/d50d8028f841bf7e8638bf8b0eed306953f2c562))
* **sync:** Normalize Redgifs URLs before extracting video IDs ([f897af7](https://github.com/wchill/patcheddit/commit/f897af7aa52c4affbedaf8c76c47e2351d1fa39f))


### Features

* **boost:** Add ability to strip query parameters when copying/sharing URLs ([4c64292](https://github.com/wchill/patcheddit/commit/4c64292d2ab2e4aebf3cbdd764e82ff6f19c6a8b))
* **boost:** Add more archiving/paywall bypass services ([c88b0cc](https://github.com/wchill/patcheddit/commit/c88b0cc4576b0418a905565b7899dd2a5a88ae45))
* Fix missing gallary images in all apps ([a125874](https://github.com/wchill/patcheddit/commit/a125874c017826a1db88b5c538a6a3b3a259af5d))
* Reorganize all Morphe settings menus to be more user friendly ([4c1e544](https://github.com/wchill/patcheddit/commit/4c1e54470ec81b7196c484955bda4d04f556bb97))

# [2.3.0-dev.4](https://github.com/wchill/patcheddit/compare/v2.3.0-dev.3...v2.3.0-dev.4) (2026-11-16)


### Bug Fixes

* Flatten comment requests for improved performance ([0ad786b](https://github.com/wchill/patcheddit/commit/0ad786b55655095c111c5d53ae80082cabd72c20))

# [2.3.0-dev.3](https://github.com/wchill/patcheddit/compare/v2.3.0-dev.2...v2.3.0-dev.3) (2026-11-16)


### Bug Fixes

* **boost:** Make colored bars/lines repeat for very deep comment chains ([e7bc5ac](https://github.com/wchill/patcheddit/commit/e7bc5ac52f73a1e20dde94b29aa3902e58c5228b))
* Repair links in comment/post bodies that were escaped by New Reddit ([6696827](https://github.com/wchill/patcheddit/commit/6696827ba60a99a91ec7ddf4f173d3af45623608))

# [2.3.0-dev.2](https://github.com/wchill/patcheddit/compare/v2.3.0-dev.1...v2.3.0-dev.2) (2026-11-16)


### Bug Fixes

* **joey:** Restore Redgifs playback through shared HTTP repairs ([668cf55](https://github.com/wchill/patcheddit/commit/668cf550943385dcbab8e6a5ad998a2505039e47))
* **redgifs:** Repair legacy authentication and token refresh ([cf47b46](https://github.com/wchill/patcheddit/commit/cf47b46d3c495a85e01e9a6add9d1da810da15bd))
* **relay:** Restore native Redgifs playback with audio ([e2fdce5](https://github.com/wchill/patcheddit/commit/e2fdce5b0f17e2a6e7404e44005672bf37378df5))
* **relay:** Restore sidebars when Reddit omits activity counters ([bbd5e26](https://github.com/wchill/patcheddit/commit/bbd5e26ee7d0a773f67087ddc6769ed4953828f1))
* Restore swipe-back transparency in Sync and Joey ([0fc793d](https://github.com/wchill/patcheddit/commit/0fc793d3b63381e585b1a6ac216e51194b462065))
* **sync:** Normalize Redgifs URLs before extracting video IDs ([975b100](https://github.com/wchill/patcheddit/commit/975b100c0722b9f9d3830c2789f71dcbc2b87a36))


### Features

* **boost:** Add ability to strip query parameters when copying/sharing URLs ([73c8a5d](https://github.com/wchill/patcheddit/commit/73c8a5d604df03d089661bc3c70ba7965cd5494f))
* **boost:** Add more archiving/paywall bypass services ([2948eeb](https://github.com/wchill/patcheddit/commit/2948eeb96dd96ad709c686da0a90c0a6bc883faf))
* Reorganize all Morphe settings menus to be more user friendly ([f4faed4](https://github.com/wchill/patcheddit/commit/f4faed430a2540f87fa337ceaf7d5481e4203a8c))

# [2.3.0-dev.1](https://github.com/wchill/patcheddit/compare/v2.2.0...v2.3.0-dev.1) (2026-11-15)


### Bug Fixes

* **rif:** Imgur album loading for albums with 5 character IDs ([d50d802](https://github.com/wchill/patcheddit/commit/d50d8028f841bf7e8638bf8b0eed306953f2c562))


### Features

* Fix missing gallary images in all apps ([a125874](https://github.com/wchill/patcheddit/commit/a125874c017826a1db88b5c538a6a3b3a259af5d))

# [2.2.0](https://github.com/wchill/patcheddit/compare/v2.1.1...v2.2.0) (2026-11-15)


### Bug Fixes

* **relay:** Relay Pro crashing on startup ([1c94b7e](https://github.com/wchill/patcheddit/commit/1c94b7ec72073e9a439c3f4cd86f8c675943aeff))
* **rif:** Imgur albums now properly load in the built-in viewer and thumbnails work ([f219ebe](https://github.com/wchill/patcheddit/commit/f219ebe17b7485e3c3d8bde3d758d53556031666))
* **rif:** Redgifs native playback ([0ef22e8](https://github.com/wchill/patcheddit/commit/0ef22e8ac7a1001a5b5009e4ae2fee0020e9ea5c))


### Features

* **boost:** Boost 1.14.4 support ([5538c39](https://github.com/wchill/patcheddit/commit/5538c39d2b32b92bf6dfb44430d6c3e03ac52dcb))

# [2.2.0-dev.1](https://github.com/wchill/patcheddit/compare/v2.1.1...v2.2.0-dev.1) (2026-11-15)


### Bug Fixes

* **relay:** Relay Pro crashing on startup ([1c94b7e](https://github.com/wchill/patcheddit/commit/1c94b7ec72073e9a439c3f4cd86f8c675943aeff))
* **rif:** Imgur albums now properly load in the built-in viewer and thumbnails work ([f219ebe](https://github.com/wchill/patcheddit/commit/f219ebe17b7485e3c3d8bde3d758d53556031666))
* **rif:** Redgifs native playback ([0ef22e8](https://github.com/wchill/patcheddit/commit/0ef22e8ac7a1001a5b5009e4ae2fee0020e9ea5c))


### Features

* **boost:** Boost 1.14.4 support ([5538c39](https://github.com/wchill/patcheddit/commit/5538c39d2b32b92bf6dfb44430d6c3e03ac52dcb))

## [2.1.1](https://github.com/wchill/patcheddit/compare/v2.1.0...v2.1.1) (2026-11-13)


### Bug Fixes

* Crash in Boost settings menu ([4457af9](https://github.com/wchill/patcheddit/commit/4457af93dcfb13c8a78cb2d8500524596f523f8b))

## [2.1.1-dev.1](https://github.com/wchill/patcheddit/compare/v2.1.0...v2.1.1-dev.1) (2026-11-11)


### Bug Fixes

* Crash in Boost settings menu ([4457af9](https://github.com/wchill/patcheddit/commit/4457af93dcfb13c8a78cb2d8500524596f523f8b))

# [2.1.0](https://github.com/wchill/patcheddit/compare/v2.0.3...v2.1.0) (2026-11-07)


### Bug Fixes

* **fix-generic-signature-rewrite:** rewrite classes whose only OkHttp references are in generic signatures ([c933ec6](https://github.com/wchill/patcheddit/commit/c933ec694feebc57a349eba40394051fcdb8f888))
* **relay-inapp-slinks:** hook the post and comment view's own link handler ([0e9665d](https://github.com/wchill/patcheddit/commit/0e9665d25f2297e707cd9efad7085bc71a0b5a01))
* **relay-inapp-slinks:** open /s/ bypass fallback in Relay's real WebView ([02341ba](https://github.com/wchill/patcheddit/commit/02341ba8c8fe8e64d26e11a9d4a0dffe5d379c9a))
* Remove redundant patches for Boost 1.14.2 ([8444cff](https://github.com/wchill/patcheddit/commit/8444cff4dd768aa0e36aa605d99c908caa2cceaf))


### Features

* Add Morphe settings menu to all supported apps ([16a7e4c](https://github.com/wchill/patcheddit/commit/16a7e4c6b5e808578cbcd4694e85fea1b9cfd1a8))
* Add patch settings toggles for every app in Morphe settings ([a0b8243](https://github.com/wchill/patcheddit/commit/a0b82432653dde38050c7277f99d7bf6e9f47074))
* Boost 1.14.2 support ([fdc340f](https://github.com/wchill/patcheddit/commit/fdc340f21748d5372f665067c5db54a24f324cb5))
* **relay-inapp-slinks:** hook Relay's in-app link handler for /s/ links ([9c36290](https://github.com/wchill/patcheddit/commit/9c36290987cbc94732466c6954139c697421d342))
* Update Boost support to 1.14.3 ([20797d6](https://github.com/wchill/patcheddit/commit/20797d6c66f0830e5781d8d5009693719ee61d54))

# [2.1.0-dev.6](https://github.com/wchill/patcheddit/compare/v2.1.0-dev.5...v2.1.0-dev.6) (2026-11-07)


### Features

* Add patch settings toggles for every app in Morphe settings ([a0b8243](https://github.com/wchill/patcheddit/commit/a0b82432653dde38050c7277f99d7bf6e9f47074))

# [2.1.0-dev.5](https://github.com/wchill/patcheddit/compare/v2.1.0-dev.4...v2.1.0-dev.5) (2026-11-06)


### Features

* Add Morphe settings menu to all supported apps ([16a7e4c](https://github.com/wchill/patcheddit/commit/16a7e4c6b5e808578cbcd4694e85fea1b9cfd1a8))

# [2.1.0-dev.4](https://github.com/wchill/patcheddit/compare/v2.1.0-dev.3...v2.1.0-dev.4) (2026-11-06)


### Bug Fixes

* Remove redundant patches for Boost 1.14.2 ([8444cff](https://github.com/wchill/patcheddit/commit/8444cff4dd768aa0e36aa605d99c908caa2cceaf))


### Features

* Update Boost support to 1.14.3 ([20797d6](https://github.com/wchill/patcheddit/commit/20797d6c66f0830e5781d8d5009693719ee61d54))

# [2.1.0-dev.3](https://github.com/wchill/patcheddit/compare/v2.1.0-dev.2...v2.1.0-dev.3) (2026-11-03)


### Bug Fixes

* **relay-inapp-slinks:** hook the post and comment view's own link handler ([0e9665d](https://github.com/wchill/patcheddit/commit/0e9665d25f2297e707cd9efad7085bc71a0b5a01))
* **relay-inapp-slinks:** open /s/ bypass fallback in Relay's real WebView ([02341ba](https://github.com/wchill/patcheddit/commit/02341ba8c8fe8e64d26e11a9d4a0dffe5d379c9a))


### Features

* **relay-inapp-slinks:** hook Relay's in-app link handler for /s/ links ([9c36290](https://github.com/wchill/patcheddit/commit/9c36290987cbc94732466c6954139c697421d342))

# [2.1.0-dev.2](https://github.com/wchill/patcheddit/compare/v2.1.0-dev.1...v2.1.0-dev.2) (2026-11-03)


### Bug Fixes

* **fix-generic-signature-rewrite:** rewrite classes whose only OkHttp references are in generic signatures ([c933ec6](https://github.com/wchill/patcheddit/commit/c933ec694feebc57a349eba40394051fcdb8f888))

# [2.1.0-dev.1](https://github.com/wchill/patcheddit/compare/v2.0.3...v2.1.0-dev.1) (2026-10-31)


### Features

* Boost 1.14.2 support ([fdc340f](https://github.com/wchill/patcheddit/commit/fdc340f21748d5372f665067c5db54a24f324cb5))

## [2.0.3](https://github.com/wchill/patcheddit/compare/v2.0.2...v2.0.3) (2026-10-30)


### Bug Fixes

* Comment loading and gallery loading for all reddit apps ([48cb357](https://github.com/wchill/patcheddit/commit/48cb357ca0bdcaaf7aa9f8a694db479e95a7c0ae))

## [2.0.3-dev.1](https://github.com/wchill/patcheddit/compare/v2.0.2...v2.0.3-dev.1) (2026-10-30)


### Bug Fixes

* Comment loading and gallery loading for all reddit apps ([48cb357](https://github.com/wchill/patcheddit/commit/48cb357ca0bdcaaf7aa9f8a694db479e95a7c0ae))

## [2.0.2](https://github.com/wchill/patcheddit/compare/v2.0.1...v2.0.2) (2026-10-30)


### Bug Fixes

* Empty responses from reddit no longer throw errors ([a7bb013](https://github.com/wchill/patcheddit/commit/a7bb0131827926c9d3b440da135cd79d917b7c49))

## [2.0.2-dev.1](https://github.com/wchill/patcheddit/compare/v2.0.1...v2.0.2-dev.1) (2026-10-30)


### Bug Fixes

* Empty responses from reddit no longer throw errors ([a7bb013](https://github.com/wchill/patcheddit/commit/a7bb0131827926c9d3b440da135cd79d917b7c49))

## [2.0.1](https://github.com/wchill/patcheddit/compare/v2.0.0...v2.0.1) (2026-10-30)


### Bug Fixes

* 403 when loading more comments in Boost ([86cb39f](https://github.com/wchill/patcheddit/commit/86cb39fde6f9aaa06a67bc4f2444b30da45149d0))
* BaconReader redirect uri losing its query parameter name ([3cbf80c](https://github.com/wchill/patcheddit/commit/3cbf80cfbc966625bd199179874ff3f8cc56aec8))
* Infinity support up to 8.3.4 ([bf1afaf](https://github.com/wchill/patcheddit/commit/bf1afaf50d8c614d7181cdf6cf4a60f67327ed5a))
* preserve descendant replies when loading more comments ([82c4d3d](https://github.com/wchill/patcheddit/commit/82c4d3d70d0880fd33aa67123df480516ae0feb2))
* Relay patching broken due to missing extension ([ad9d2c5](https://github.com/wchill/patcheddit/commit/ad9d2c579a0f0fdbbdd85e2894ae57cb1e50c71a))
* resolve Sync OAuth fingerprints ([c050118](https://github.com/wchill/patcheddit/commit/c050118f67d34c840f86e3b28f12785b111326ef))

## [2.0.1-dev.5](https://github.com/wchill/patcheddit/compare/v2.0.1-dev.4...v2.0.1-dev.5) (2026-10-30)


### Bug Fixes

* BaconReader redirect uri losing its query parameter name ([3cbf80c](https://github.com/wchill/patcheddit/commit/3cbf80cfbc966625bd199179874ff3f8cc56aec8))

## [2.0.1-dev.4](https://github.com/wchill/patcheddit/compare/v2.0.1-dev.3...v2.0.1-dev.4) (2026-10-30)


### Bug Fixes

* resolve Sync OAuth fingerprints ([c050118](https://github.com/wchill/patcheddit/commit/c050118f67d34c840f86e3b28f12785b111326ef))

## [2.0.1-dev.3](https://github.com/wchill/patcheddit/compare/v2.0.1-dev.2...v2.0.1-dev.3) (2026-10-30)


### Bug Fixes

* preserve descendant replies when loading more comments ([82c4d3d](https://github.com/wchill/patcheddit/commit/82c4d3d70d0880fd33aa67123df480516ae0feb2))

## [2.0.1-dev.2](https://github.com/wchill/patcheddit/compare/v2.0.1-dev.1...v2.0.1-dev.2) (2026-10-30)


### Bug Fixes

* 403 when loading more comments in Boost ([86cb39f](https://github.com/wchill/patcheddit/commit/86cb39fde6f9aaa06a67bc4f2444b30da45149d0))

## [2.0.1-dev.1](https://github.com/wchill/patcheddit/compare/v2.0.0...v2.0.1-dev.1) (2026-10-28)


### Bug Fixes

* Infinity support up to 8.3.4 ([bf1afaf](https://github.com/wchill/patcheddit/commit/bf1afaf50d8c614d7181cdf6cf4a60f67327ed5a))
* Relay patching broken due to missing extension ([ad9d2c5](https://github.com/wchill/patcheddit/commit/ad9d2c579a0f0fdbbdd85e2894ae57cb1e50c71a))

# [2.0.0](https://github.com/wchill/patcheddit/compare/v1.4.0...v2.0.0) (2026-10-28)


### Bug Fixes

* Clean up OAuth token import/export ([3749e21](https://github.com/wchill/patcheddit/commit/3749e214e05d04d086c99f01041b602b9ee46e54))
* Navigation patch no longer causes subreddit list to pop up twice ([da658a1](https://github.com/wchill/patcheddit/commit/da658a1a2f3966fc1c44faf8477187dc0b0505ae))
* Remove Continuum and Relay patches ([b4ac952](https://github.com/wchill/patcheddit/commit/b4ac952ea9efc06577b22db6c8b573308236b63c))
* Resolve login failure caused by non-English language ([fec2ed5](https://github.com/wchill/patcheddit/commit/fec2ed591fa42fa59fd567059cbaae111bfa6caf))
* Use shaded copy of Jackson ([5ea1b1e](https://github.com/wchill/patcheddit/commit/5ea1b1e8d2eac4abbc397a7a16875d12bbd41c79))


### Features

* Add CrashLogPatch from morphe-patches-library as a dependency and expose it in app settings ([0b2d4be](https://github.com/wchill/patcheddit/commit/0b2d4be869d632508e18476f028d9033741711ff))
* Add OAuth token export + remove reskinned settings menus ([9a7aca3](https://github.com/wchill/patcheddit/commit/9a7aca3d187441da9de80b75344fa3303ffcc22b))
* Add patch to fix scrolling on user profiles ([75e4cc9](https://github.com/wchill/patcheddit/commit/75e4cc9d318193bcdcc29a635dee8ad228f11c58))
* Add patch to log exceptions to file ([d572890](https://github.com/wchill/patcheddit/commit/d5728903682e734a985290e8bda3d1d8c5d4ef9a))
* Boost 1.14.1 support ([1b17fe0](https://github.com/wchill/patcheddit/commit/1b17fe06970465247d989d510bc837ff6788aa3e))
* **boost:** port patch changes from brealorg ([e1ad2bf](https://github.com/wchill/patcheddit/commit/e1ad2bf6bf7e45b59e8ccce8fffa6906d8859aa2))
* **boost:** sync upstream patches for Morphe 2.0 ([625f6d6](https://github.com/wchill/patcheddit/commit/625f6d66c7c093fe76b73d19ce6820604dea20d8))
* Support Boost 1.14.0 ([6ced72b](https://github.com/wchill/patcheddit/commit/6ced72b3a8adc43424ae3960eed3a85a3610cfe6))


### BREAKING CHANGES

* Uses morphe-patcher 2.x, and many obsolete patches have
been removed. Existing patches have been reworked. This version may not
be fully functional yet.

# [2.0.0-dev.9](https://github.com/wchill/patcheddit/compare/v2.0.0-dev.8...v2.0.0-dev.9) (2026-10-28)


### Bug Fixes

* Navigation patch no longer causes subreddit list to pop up twice ([da658a1](https://github.com/wchill/patcheddit/commit/da658a1a2f3966fc1c44faf8477187dc0b0505ae))

# [2.0.0-dev.8](https://github.com/wchill/patcheddit/compare/v2.0.0-dev.7...v2.0.0-dev.8) (2026-10-27)


### Features

* Add patch to fix scrolling on user profiles ([75e4cc9](https://github.com/wchill/patcheddit/commit/75e4cc9d318193bcdcc29a635dee8ad228f11c58))

# [2.0.0-dev.7](https://github.com/wchill/patcheddit/compare/v2.0.0-dev.6...v2.0.0-dev.7) (2026-10-27)


### Bug Fixes

* Clean up OAuth token import/export ([3749e21](https://github.com/wchill/patcheddit/commit/3749e214e05d04d086c99f01041b602b9ee46e54))

# [2.0.0-dev.6](https://github.com/wchill/patcheddit/compare/v2.0.0-dev.5...v2.0.0-dev.6) (2026-10-27)


### Bug Fixes

* Use shaded copy of Jackson ([5ea1b1e](https://github.com/wchill/patcheddit/commit/5ea1b1e8d2eac4abbc397a7a16875d12bbd41c79))

# [2.0.0-dev.5](https://github.com/wchill/patcheddit/compare/v2.0.0-dev.4...v2.0.0-dev.5) (2026-10-26)


### Features

* Add CrashLogPatch from morphe-patches-library as a dependency and expose it in app settings ([0b2d4be](https://github.com/wchill/patcheddit/commit/0b2d4be869d632508e18476f028d9033741711ff))

# [2.0.0-dev.4](https://github.com/wchill/patcheddit/compare/v2.0.0-dev.3...v2.0.0-dev.4) (2026-10-26)


### Features

* Add patch to log exceptions to file ([d572890](https://github.com/wchill/patcheddit/commit/d5728903682e734a985290e8bda3d1d8c5d4ef9a))

# [2.0.0-dev.3](https://github.com/wchill/patcheddit/compare/v2.0.0-dev.2...v2.0.0-dev.3) (2026-10-26)


### Features

* Add OAuth token export + remove reskinned settings menus ([9a7aca3](https://github.com/wchill/patcheddit/commit/9a7aca3d187441da9de80b75344fa3303ffcc22b))

# [2.0.0-dev.2](https://github.com/wchill/patcheddit/compare/v2.0.0-dev.1...v2.0.0-dev.2) (2026-10-26)


### Features

* Boost 1.14.1 support ([1b17fe0](https://github.com/wchill/patcheddit/commit/1b17fe06970465247d989d510bc837ff6788aa3e))

# [2.0.0-dev.1](https://github.com/wchill/patcheddit/compare/v1.5.0-dev.1...v2.0.0-dev.1) (2026-10-26)


### Features

* Support Boost 1.14.0 ([6ced72b](https://github.com/wchill/patcheddit/commit/6ced72b3a8adc43424ae3960eed3a85a3610cfe6))


### BREAKING CHANGES

* Uses morphe-patcher 2.x, and many obsolete patches have
been removed. Existing patches have been reworked. This version may not
be fully functional yet.

# [1.5.0-dev.1](https://github.com/wchill/patcheddit/compare/v1.4.1-dev.1...v1.5.0-dev.1) (2026-10-23)


### Bug Fixes

* Remove Continuum and Relay patches ([b4ac952](https://github.com/wchill/patcheddit/commit/b4ac952ea9efc06577b22db6c8b573308236b63c))


### Features

* **boost:** port patch changes from brealorg ([e1ad2bf](https://github.com/wchill/patcheddit/commit/e1ad2bf6bf7e45b59e8ccce8fffa6906d8859aa2))
* **boost:** sync upstream patches for Morphe 2.0 ([625f6d6](https://github.com/wchill/patcheddit/commit/625f6d66c7c093fe76b73d19ce6820604dea20d8))

## [1.4.1-dev.1](https://github.com/wchill/patcheddit/compare/v1.4.0...v1.4.1-dev.1) (2026-04-09)


### Bug Fixes

* Resolve login failure caused by non-English language ([fec2ed5](https://github.com/wchill/patcheddit/commit/fec2ed591fa42fa59fd567059cbaae111bfa6caf))

# [1.4.0](https://github.com/wchill/patcheddit/compare/v1.3.1...v1.4.0) (2026-04-07)


### Bug Fixes

* Don't show fake premium patch for reddit is fun golden platinum ([15d372e](https://github.com/wchill/patcheddit/commit/15d372e09cd36252483cf468a7d0a09188d2d4d1))
* Make /r/all patch available ([9740e3d](https://github.com/wchill/patcheddit/commit/9740e3d2ba1c67ae5ba7247f992f3724e8837dbe))
* reddit is fun imgur album loading ([66c73c9](https://github.com/wchill/patcheddit/commit/66c73c969acbbfa038d52cd020533eb39f95f224))


### Features

* /r/all patch for Boost ([60b8743](https://github.com/wchill/patcheddit/commit/60b8743b6fe47aea55fe10d87124b416d692c355))
* Add experimental patch to help resolve some login issues ([3a1025e](https://github.com/wchill/patcheddit/commit/3a1025e0370e9d7f5f5f0c8a5f4202e81f434979))

# [1.4.0-dev.3](https://github.com/wchill/patcheddit/compare/v1.4.0-dev.2...v1.4.0-dev.3) (2026-04-06)


### Features

* Add experimental patch to help resolve some login issues ([3a1025e](https://github.com/wchill/patcheddit/commit/3a1025e0370e9d7f5f5f0c8a5f4202e81f434979))

# [1.4.0-dev.2](https://github.com/wchill/patcheddit/compare/v1.4.0-dev.1...v1.4.0-dev.2) (2026-04-06)


### Bug Fixes

* Make /r/all patch available ([9740e3d](https://github.com/wchill/patcheddit/commit/9740e3d2ba1c67ae5ba7247f992f3724e8837dbe))

# [1.4.0-dev.1](https://github.com/wchill/patcheddit/compare/v1.3.1...v1.4.0-dev.1) (2026-04-06)


### Bug Fixes

* Don't show fake premium patch for reddit is fun golden platinum ([15d372e](https://github.com/wchill/patcheddit/commit/15d372e09cd36252483cf468a7d0a09188d2d4d1))
* reddit is fun imgur album loading ([66c73c9](https://github.com/wchill/patcheddit/commit/66c73c969acbbfa038d52cd020533eb39f95f224))


### Features

* /r/all patch for Boost ([60b8743](https://github.com/wchill/patcheddit/commit/60b8743b6fe47aea55fe10d87124b416d692c355))

## [1.3.1](https://github.com/wchill/patcheddit/compare/v1.3.0...v1.3.1) (2026-04-01)


### Bug Fixes

* Attempt to fix compatibility with Manager 1.14.0 ([174d6ad](https://github.com/wchill/patcheddit/commit/174d6ad6420ba6172f41948053592fea9097d49f))
* Remove dependency on transformInstructions patch ([ed2697d](https://github.com/wchill/patcheddit/commit/ed2697d3ab6eb796c505627b5da1d36b4b09b563))
* **Sync for reddit:** User endpoint patch failing to match ([8cab251](https://github.com/wchill/patcheddit/commit/8cab2512a8d5d9fbebcd4d392384fc9774f1d84c))
* Use replaceStringsPatch with comparison type contains for Sync spoof patch ([b966d81](https://github.com/wchill/patcheddit/commit/b966d8140f9f626bc5919ff84b519e6cce011771))

## [1.3.1-dev.4](https://github.com/wchill/patcheddit/compare/v1.3.1-dev.3...v1.3.1-dev.4) (2026-04-01)


### Bug Fixes

* Use replaceStringsPatch with comparison type contains for Sync spoof patch ([b966d81](https://github.com/wchill/patcheddit/commit/b966d8140f9f626bc5919ff84b519e6cce011771))

## [1.3.1-dev.3](https://github.com/wchill/patcheddit/compare/v1.3.1-dev.2...v1.3.1-dev.3) (2026-04-01)


### Bug Fixes

* Remove dependency on transformInstructions patch ([ed2697d](https://github.com/wchill/patcheddit/commit/ed2697d3ab6eb796c505627b5da1d36b4b09b563))

## [1.3.1-dev.2](https://github.com/wchill/patcheddit/compare/v1.3.1-dev.1...v1.3.1-dev.2) (2026-04-01)


### Bug Fixes

* Attempt to fix compatibility with Manager 1.14.0 ([174d6ad](https://github.com/wchill/patcheddit/commit/174d6ad6420ba6172f41948053592fea9097d49f))

## [1.3.1-dev.1](https://github.com/wchill/patcheddit/compare/v1.3.0...v1.3.1-dev.1) (2026-04-01)


### Bug Fixes

* **Sync for reddit:** User endpoint patch failing to match ([8cab251](https://github.com/wchill/patcheddit/commit/8cab2512a8d5d9fbebcd4d392384fc9774f1d84c))

# [1.3.0](https://github.com/wchill/patcheddit/compare/v1.2.0...v1.3.0) (2026-04-01)


### Bug Fixes

* Bump gradle plugin version ([6435231](https://github.com/wchill/patcheddit/commit/6435231016b9799e978f059d3f35f6cd69add853))
* Check for HTTP 5xx when loading posts in Boost ([511fae0](https://github.com/wchill/patcheddit/commit/511fae08a7380343a26675cfa873588ecde1d714))
* Client ID validation not working ([d6a5e50](https://github.com/wchill/patcheddit/commit/d6a5e500accb41dd001c1f3a6e9bb320c53a41c8))
* Continuum crashing when cold opening reddit links ([7848ebd](https://github.com/wchill/patcheddit/commit/7848ebdea255379b306fa570712cff0a586a8955))
* Crash in Infinity+ settings ([3ff1829](https://github.com/wchill/patcheddit/commit/3ff1829bbe0f6f6834bff32e176a1d37a979a79c))
* Fix issues loading patch bundle ([d644b35](https://github.com/wchill/patcheddit/commit/d644b3582ba7f6f6267f2239d9bee87ffa39c885))
* Improper regex for client ID ([707b2ba](https://github.com/wchill/patcheddit/commit/707b2ba95edfcf784138f4fa5fe0702978cf0d81))
* Infinity crashing when cold opening reddit links ([688f574](https://github.com/wchill/patcheddit/commit/688f5744818ef8be5386c7a7097ef14c2a9fc399))
* Sync for reddit patch errored with "Collection is empty" ([bdb71c4](https://github.com/wchill/patcheddit/commit/bdb71c4349c4e9d4b6c1ff15720814a3c94079e6))
* Use separate names for pro/dev versions of apps ([c060aef](https://github.com/wchill/patcheddit/commit/c060aefbbc6ce28f274278b8d0649bcb7cba30f6))


### Features

* Add support for Infinity+ ([9e28199](https://github.com/wchill/patcheddit/commit/9e28199844e770d3c6b56eaaab6b2417c30560a2))
* Support reddit is fun golden platinum ([a768745](https://github.com/wchill/patcheddit/commit/a768745cdc1a1d703dd5a31b8e026e61a5454cb8))
* Update to Morphe patcher 1.3.0 ([ded92dc](https://github.com/wchill/patcheddit/commit/ded92dc15a062f0defd9eb655e567c24b0373c98))
* Use sqlite-backed cache implementation for Boost ([2cfea64](https://github.com/wchill/patcheddit/commit/2cfea642704aef15793d3f0c3548d870ff021690))

# [1.3.0-dev.5](https://github.com/wchill/patcheddit/compare/v1.3.0-dev.4...v1.3.0-dev.5) (2026-04-01)


### Bug Fixes

* Bump gradle plugin version ([6435231](https://github.com/wchill/patcheddit/commit/6435231016b9799e978f059d3f35f6cd69add853))
* Client ID validation not working ([d6a5e50](https://github.com/wchill/patcheddit/commit/d6a5e500accb41dd001c1f3a6e9bb320c53a41c8))
* Continuum crashing when cold opening reddit links ([7848ebd](https://github.com/wchill/patcheddit/commit/7848ebdea255379b306fa570712cff0a586a8955))
* Crash in Infinity+ settings ([3ff1829](https://github.com/wchill/patcheddit/commit/3ff1829bbe0f6f6834bff32e176a1d37a979a79c))
* Improper regex for client ID ([707b2ba](https://github.com/wchill/patcheddit/commit/707b2ba95edfcf784138f4fa5fe0702978cf0d81))
* Infinity crashing when cold opening reddit links ([688f574](https://github.com/wchill/patcheddit/commit/688f5744818ef8be5386c7a7097ef14c2a9fc399))


### Features

* Add support for Infinity+ ([9e28199](https://github.com/wchill/patcheddit/commit/9e28199844e770d3c6b56eaaab6b2417c30560a2))

# [1.3.0-dev.9](https://github.com/wchill/patcheddit/compare/v1.3.0-dev.8...v1.3.0-dev.9) (2026-04-01)


### Bug Fixes

* Crash in Infinity+ settings ([6bfa378](https://github.com/wchill/patcheddit/commit/6bfa378f303eeb952117e5c108dcb6185c7bab48))

# [1.3.0-dev.8](https://github.com/wchill/patcheddit/compare/v1.3.0-dev.7...v1.3.0-dev.8) (2026-03-31)


### Bug Fixes

* Continuum crashing when cold opening reddit links ([36250c9](https://github.com/wchill/patcheddit/commit/36250c9eddaf382cc25fb7fecb5e3cffe29609a1))
* Infinity crashing when cold opening reddit links ([1e3ef91](https://github.com/wchill/patcheddit/commit/1e3ef919f1aa1c4371ed140def1b3f8fdc933342))

# [1.3.0-dev.7](https://github.com/wchill/patcheddit/compare/v1.3.0-dev.6...v1.3.0-dev.7) (2026-03-31)


### Bug Fixes

* Improper regex for client ID ([90b8161](https://github.com/wchill/patcheddit/commit/90b816125a3ec5c9ac99b636f5570aacdc86fa5e))

# [1.3.0-dev.6](https://github.com/wchill/patcheddit/compare/v1.3.0-dev.5...v1.3.0-dev.6) (2026-03-31)


### Bug Fixes

* Client ID validation not working ([00545a8](https://github.com/wchill/patcheddit/commit/00545a8b925b12578d2e113f6481eddaa93dfeef))

# [1.3.0-dev.5](https://github.com/wchill/patcheddit/compare/v1.3.0-dev.4...v1.3.0-dev.5) (2026-03-31)


### Features

* Add support for Infinity+ ([9c62c1c](https://github.com/wchill/patcheddit/commit/9c62c1cae2f1ffdfcb4f0f488f38dff910355f57))

# [1.3.0-dev.4](https://github.com/wchill/patcheddit/compare/v1.3.0-dev.3...v1.3.0-dev.4) (2026-03-31)


### Bug Fixes

* Sync for reddit patch errored with "Collection is empty" ([bdb71c4](https://github.com/wchill/patcheddit/commit/bdb71c4349c4e9d4b6c1ff15720814a3c94079e6))


### Features

* Support reddit is fun golden platinum ([a768745](https://github.com/wchill/patcheddit/commit/a768745cdc1a1d703dd5a31b8e026e61a5454cb8))

# [1.3.0-dev.3](https://github.com/wchill/patcheddit/compare/v1.3.0-dev.2...v1.3.0-dev.3) (2026-03-22)


### Bug Fixes

* Use separate names for pro/dev versions of apps ([c060aef](https://github.com/wchill/patcheddit/commit/c060aefbbc6ce28f274278b8d0649bcb7cba30f6))

# [1.3.0-dev.2](https://github.com/wchill/patcheddit/compare/v1.3.0-dev.1...v1.3.0-dev.2) (2026-03-22)


### Bug Fixes

* Fix issues loading patch bundle ([d644b35](https://github.com/wchill/patcheddit/commit/d644b3582ba7f6f6267f2239d9bee87ffa39c885))

# [1.3.0-dev.1](https://github.com/wchill/patcheddit/compare/v1.2.0...v1.3.0-dev.1) (2026-03-22)


### Bug Fixes

* Check for HTTP 5xx when loading posts in Boost ([511fae0](https://github.com/wchill/patcheddit/commit/511fae08a7380343a26675cfa873588ecde1d714))


### Features

* Update to Morphe patcher 1.3.0 ([ded92dc](https://github.com/wchill/patcheddit/commit/ded92dc15a062f0defd9eb655e567c24b0373c98))
* Use sqlite-backed cache implementation for Boost ([2cfea64](https://github.com/wchill/patcheddit/commit/2cfea642704aef15793d3f0c3548d870ff021690))

# [1.2.0](https://github.com/wchill/patcheddit/compare/v1.1.0...v1.2.0) (2026-01-20)


### Bug Fixes

* Fix broken Sync for Reddit ad removal patch ([673825c](https://github.com/wchill/patcheddit/commit/673825c91cca62c6596c0709a8886b9c5672385f))
* Remove Infinity for Reddit patches (use Continuum for Reddit instead) ([32e92a0](https://github.com/wchill/patcheddit/commit/32e92a07ed33696ee2c92c51eb2f916420803f48))


### Features

* Add client ID patching support for Continuum for Reddit ([#16](https://github.com/wchill/patcheddit/issues/16)) ([32e5408](https://github.com/wchill/patcheddit/commit/32e5408fadc61503d4a528828474f4aeb514fbdc))
* Add client ID patching support for Slide for Reddit fork ([#18](https://github.com/wchill/patcheddit/issues/18)) ([cf0f0f1](https://github.com/wchill/patcheddit/commit/cf0f0f18f984c9ad367f790fca7203325ed29d25))
* Add support for /s/ links in Relay ([#21](https://github.com/wchill/patcheddit/issues/21)) ([eb0ba9b](https://github.com/wchill/patcheddit/commit/eb0ba9b08b8e45ca31a03a3354e82ebc3eed9987))

# [1.2.0-dev.4](https://github.com/wchill/patcheddit/compare/v1.2.0-dev.3...v1.2.0-dev.4) (2026-01-20)


### Features

* Add support for /s/ links in Relay ([#21](https://github.com/wchill/patcheddit/issues/21)) ([eb0ba9b](https://github.com/wchill/patcheddit/commit/eb0ba9b08b8e45ca31a03a3354e82ebc3eed9987))

# [1.2.0-dev.3](https://github.com/wchill/patcheddit/compare/v1.2.0-dev.2...v1.2.0-dev.3) (2026-01-19)


### Bug Fixes

* Fix broken Sync for Reddit ad removal patch ([673825c](https://github.com/wchill/patcheddit/commit/673825c91cca62c6596c0709a8886b9c5672385f))

# [1.2.0-dev.2](https://github.com/wchill/patcheddit/compare/v1.2.0-dev.1...v1.2.0-dev.2) (2026-01-19)


### Features

* Add client ID patching support for Slide for Reddit fork ([#18](https://github.com/wchill/patcheddit/issues/18)) ([cf0f0f1](https://github.com/wchill/patcheddit/commit/cf0f0f18f984c9ad367f790fca7203325ed29d25))

# [1.2.0-dev.1](https://github.com/wchill/patcheddit/compare/v1.1.0...v1.2.0-dev.1) (2026-01-19)


### Bug Fixes

* Remove Infinity for Reddit patches (use Continuum for Reddit instead) ([32e92a0](https://github.com/wchill/patcheddit/commit/32e92a07ed33696ee2c92c51eb2f916420803f48))


### Features

* Add client ID patching support for Continuum for Reddit ([#16](https://github.com/wchill/patcheddit/issues/16)) ([32e5408](https://github.com/wchill/patcheddit/commit/32e5408fadc61503d4a528828474f4aeb514fbdc))

# [1.1.0](https://github.com/wchill/patcheddit/compare/v1.0.1...v1.1.0) (2026-01-09)


### Features

* Add imgur album patch for rif (from null-dev/revanced-patches@1dabe26) ([d7cc5a1](https://github.com/wchill/patcheddit/commit/d7cc5a1a653991c759d53171b09a6b6a69c08518))
* Add rif premium unlock patch (from null-dev/revanced-patches@1dabe26) ([aff0385](https://github.com/wchill/patcheddit/commit/aff0385b158f883b2bbd445a16b1d9c5b5175b2d))
* Add support for patching BaconReader with custom redirect URI ([a99d598](https://github.com/wchill/patcheddit/commit/a99d5980b22a25843f97d88045874c73435940dd))
* Add support for patching Joey with custom redirect URI ([bc7bc1a](https://github.com/wchill/patcheddit/commit/bc7bc1a7acf05eacf7fff8f62a217cfdeb027049))
* Add support for patching Relay with custom redirect URI ([55a1958](https://github.com/wchill/patcheddit/commit/55a1958008086651e7d97a1588c728325a08a7aa))
* Add support for patching rif with custom redirect URI (from altherat/revanced-patches@fe01d43) ([e2aa03a](https://github.com/wchill/patcheddit/commit/e2aa03ae1bee92a499166647e9d19e5492904a8d))
* Add support for patching Sync with custom redirect URI ([8822a60](https://github.com/wchill/patcheddit/commit/8822a605d4c41cd3ba49fe60f85b50531daff4d5))

# [1.1.0-dev.4](https://github.com/wchill/patcheddit/compare/v1.1.0-dev.3...v1.1.0-dev.4) (2026-01-09)


### Features

* Add support for patching Joey with custom redirect URI ([bc7bc1a](https://github.com/wchill/patcheddit/commit/bc7bc1a7acf05eacf7fff8f62a217cfdeb027049))
* Add support for patching Sync with custom redirect URI ([8822a60](https://github.com/wchill/patcheddit/commit/8822a605d4c41cd3ba49fe60f85b50531daff4d5))

# [1.1.0-dev.3](https://github.com/wchill/patcheddit/compare/v1.1.0-dev.2...v1.1.0-dev.3) (2026-01-09)


### Features

* Add support for patching BaconReader with custom redirect URI ([a99d598](https://github.com/wchill/patcheddit/commit/a99d5980b22a25843f97d88045874c73435940dd))

# [1.1.0-dev.2](https://github.com/wchill/patcheddit/compare/v1.1.0-dev.1...v1.1.0-dev.2) (2026-01-09)


### Features

* Add support for patching Relay with custom redirect URI ([55a1958](https://github.com/wchill/patcheddit/commit/55a1958008086651e7d97a1588c728325a08a7aa))

# [1.1.0-dev.1](https://github.com/wchill/patcheddit/compare/v1.0.1...v1.1.0-dev.1) (2026-01-09)


### Features

* Add imgur album patch for rif (from null-dev/revanced-patches@1dabe26) ([d7cc5a1](https://github.com/wchill/patcheddit/commit/d7cc5a1a653991c759d53171b09a6b6a69c08518))
* Add rif premium unlock patch (from null-dev/revanced-patches@1dabe26) ([aff0385](https://github.com/wchill/patcheddit/commit/aff0385b158f883b2bbd445a16b1d9c5b5175b2d))
* Add support for patching rif with custom redirect URI (from altherat/revanced-patches@fe01d43) ([e2aa03a](https://github.com/wchill/patcheddit/commit/e2aa03ae1bee92a499166647e9d19e5492904a8d))

# 1.0.0 (2026-01-08)


### Bug Fixes

* Copy all patches from previous repo and make them compile ([d6f98de](https://github.com/wchill/patcheddit/commit/d6f98de407fff1a8a58c4e89e5c11970a45e35dc))
* Fix extension class namespaces in patch code ([d14a92e](https://github.com/wchill/patcheddit/commit/d14a92ea494af721ff8c17e844492b1a0835d690))
* Refresh patches.api ([ac4a2ee](https://github.com/wchill/patcheddit/commit/ac4a2ee6a0ac39dcb51786403564e6dccd746bec))
* Update extension code to new namespace and fix build ([2c484a6](https://github.com/wchill/patcheddit/commit/2c484a614123a19880a3080250563d6657aea10a))
* use strings parameter instead of string filters ([951e1bd](https://github.com/wchill/patcheddit/commit/951e1bdee6a7b8307c226e43dbed5ef4216a54ae))


### Features

* Migrate all fingerprints to declarative API ([56374b8](https://github.com/wchill/patcheddit/commit/56374b80e1ad6ea3fe67da9ae9d871b1d94161a3))

# [1.0.0-dev.3](https://github.com/wchill/patcheddit/compare/v1.0.0-dev.2...v1.0.0-dev.3) (2026-01-03)


### Bug Fixes

* Fix extension class namespaces in patch code ([d14a92e](https://github.com/wchill/patcheddit/commit/d14a92ea494af721ff8c17e844492b1a0835d690))

# [1.0.0-dev.2](https://github.com/wchill/patcheddit/compare/v1.0.0-dev.1...v1.0.0-dev.2) (2026-01-03)


### Bug Fixes

* use strings parameter instead of string filters ([951e1bd](https://github.com/wchill/patcheddit/commit/951e1bdee6a7b8307c226e43dbed5ef4216a54ae))

# 1.0.0-dev.1 (2026-01-03)


### Bug Fixes

* Copy all patches from previous repo and make them compile ([d6f98de](https://github.com/wchill/patcheddit/commit/d6f98de407fff1a8a58c4e89e5c11970a45e35dc))
* Refresh patches.api ([ac4a2ee](https://github.com/wchill/patcheddit/commit/ac4a2ee6a0ac39dcb51786403564e6dccd746bec))
* Update extension code to new namespace and fix build ([2c484a6](https://github.com/wchill/patcheddit/commit/2c484a614123a19880a3080250563d6657aea10a))


### Features

* Migrate all fingerprints to declarative API ([56374b8](https://github.com/wchill/patcheddit/commit/56374b80e1ad6ea3fe67da9ae9d871b1d94161a3))
