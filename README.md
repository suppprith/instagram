# Instagram, messages only

An Android app that opens straight to your Instagram messages. No feed, Reels or Explore.

It runs Instagram's own mobile website in a locked-down WebView. You sign in on Instagram's page; the app never sees your password or messages. An optional lock keeps the official Instagram app closed except for short daily passes to post or call.

Ships as a signed APK on GitHub Releases.

## Docs

- [How Konvo works](docs/konvo-teardown.md): the iOS app this is based on
- [Architecture](docs/architecture.md): web view, cage, notifications, lock, distribution
- [Design](docs/design.md): principles, copy rules, visual system, every screen
- [Roadmap](docs/roadmap.md): build phases, device checklist, open decisions

## Layout

```
app/src/main/assets/cage/   cage.js and rules.json, injected into instagram.com
app/src/main/java/.../      cage (rules, patch), web (WebView), ui, notify, lock, update
cage-test/                  jsdom tests for cage.js
patch/rules-patch.json      remote rules patch, picked up by installed apps within an hour
```

## Build

CI builds every push: lint, unit tests, the jsdom cage tests, and a debug APK attached to the workflow run as `instagram-dms-debug`.

Locally, with JDK 17 and the Android SDK:

```
./gradlew lintDebug testDebugUnitTest assembleDebug
cd cage-test && npm ci && npm test
```

The debug build installs next to a release build (`com.suppprith.dms.debug`).

## Fixing the cage without a release

Instagram changes its markup often. Add selectors or block rules to `patch/rules-patch.json`:

```json
{ "schema": 1, "block": ["^/new_feed(/|$)"], "hide": ["a[href^='/new_feed/']"], "css": "" }
```

The patch is additive only. The app rejects a patch that removes rules, blocks the inbox or sign-in, has a regex that does not compile, or is over 64 KB. The URL is `dms.patchUrl` in `gradle.properties`. A raw GitHub URL only works while the repository is public; see the open decisions in the roadmap.

## Releasing

One-time setup:

1. Create a keystore and keep a backup offline. Losing it means users must uninstall to update.
   ```
   keytool -genkeypair -v -keystore release.jks -alias dms -keyalg RSA -keysize 4096 -validity 10000
   ```
2. Add repository secrets: `RELEASE_KEYSTORE_BASE64` (`base64 -w0 release.jks`), `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`.

Each release: run the [device checklist](docs/roadmap.md#device-checklist), then push a tag such as `v1.0.0`. The release workflow builds, signs, and attaches `instagram-dms-1.0.0.apk` and its SHA-256 to a GitHub Release. The app checks for new releases once a day.

## Install

Download the APK from Releases and open it. Android asks to allow installs from your browser or file manager the first time.

To use the lock on Android 13 or newer, allow restricted settings first: App info, the menu at the top, Allow restricted settings. The app walks through this in Settings, Lock the Instagram app.

## Status

All roadmap phases are implemented. What still needs a real phone: the device checklist, and checking the hide selectors against Instagram's live Android layout in light, dark and a non-English language.
