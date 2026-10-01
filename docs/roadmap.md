# Roadmap

Build order for the APK. Each task is one commit. Each phase ends with a checkable outcome. The build runs in GitHub Actions, so no local Android Studio is required.

Read first: [konvo-teardown.md](konvo-teardown.md), [architecture.md](architecture.md), [design.md](design.md).

## Phase 0: Project and CI

1. Gradle project, Kotlin DSL, version catalog. `applicationId com.suppprith.dms`, label "Instagram", `minSdk 26`, latest stable `targetSdk`.
2. Compose + Material 3 theme with the tokens from design.md. Light and dark.
3. Adaptive and themed icon.
4. `.github/workflows/ci.yml`: on push, run `lint`, `testDebugUnitTest`, `assembleDebug`; upload the debug APK as a workflow artifact.
5. `.github/workflows/release.yml`: on `v*` tag, decode the keystore secret, `assembleRelease`, attach `instagram-dms-<version>.apk` and `.sha256` to a GitHub Release.
6. `NOTICE` crediting Konvo (MIT) for the cage rules and approach.

**Done when:** a push produces a downloadable debug APK that installs and shows an empty themed screen.

## Phase 1: Web view shell

1. `CageWebView` with the settings from architecture.md, loading `/direct/inbox/`.
2. Chrome-mobile user agent without the `wv` token.
3. `CageClient`: Instagram and Meta login hosts stay in-app; everything else opens in a Custom Tab; `l.instagram.com` links unwrapped.
4. Back button handling.
5. `CageChromeClient`: file chooser (gallery + camera), mic and camera permission bridging, full-screen video, `window.open` handling.
6. Download listener for user-saved media.
7. Cookie `flush()` on pause; "Sign out of this device".
8. Launch screen held until first render, 8 s timeout. WebView background set to `bg`.
9. Offline screen with Retry and auto-retry on reconnect.

**Done when:** sign in (including two-factor) works, and text, photo and voice note send and receive. The session survives force-stop and reboot.

## Phase 2: The cage

1. `assets/cage/rules.json` with the five block rules and the starting hide list.
2. `Rules.kt` matcher + JUnit tests for every path in the testing table.
3. `cage.js`: history wrapping, popstate, interval safety net, `enforce()`, hide stylesheet, Instagram tab bar removal, reel swipe guard. All wrapped in try/catch.
4. Document-start injection via `addDocumentStartJavaScript`, origin-restricted, rules inlined. Fallback injection in `onPageStarted`.
5. Native gate in `doUpdateVisitedHistory`.
6. Bridge via `addWebMessageListener`: `ready`, `route`, `blocked`, `haptic`, `error` in; `navigate`, `rules` out.
7. jsdom test suite for `cage.js`, run in CI.
8. Verify and extend hide selectors on a real Android device in light, dark, and a non-English language. Prefer icon SVG paths and structure over `aria-label` text.

**Done when:** every item in the device checklist's "Cage" section passes.

## Phase 3: Native frame

1. Bottom bar (Messages, Activity, Profile), hidden in threads, media and with the keyboard open.
2. Unread dot from the page's badge poll.
3. Gear on Profile opening the Settings sheet.
4. Thread open and close transitions.
5. Haptic tick on send.
6. First-run screen and sign-in hand-off.

**Done when:** the app matches design.md screens 1 to 6 in light and dark, at 100% and 200% font scale.

## Phase 4: Remote patch

1. Patch schema + validator (version, regex compile, size cap, additive only) with JUnit tests.
2. `PatchRepository`: fetch on start and hourly, cache last good, merge with bundled rules.
3. Push live rule updates into the page with the `rules` message.
4. Host `rules-patch.json` on a public static URL (see open decisions).

**Done when:** adding a selector to the hosted patch hides that element on a running phone within one app restart, with no new APK.

## Phase 5: Notifications

1. Notification channel "Messages".
2. `POST_NOTIFICATIONS` request screen after first sign-in.
3. `UnreadWorker` every 15 min: badge count with WebView cookies, notify on increase, never when the app is in the foreground.
4. Tapping the notification opens the inbox.
5. In-app 30 s badge poll while in the foreground.

**Done when:** a message sent from another account produces a notification within 15 minutes with the app closed, and none while it is open.

## Phase 6: Lock and passes

1. `PassPolicy` + `PassRepository` (DataStore) with JUnit tests: rollover at midnight, exhaustion, expiry backstop, clock changes.
2. `LockAccessibilityService` limited to `com.instagram.android` window state events.
3. Lock sheet activity per design.md, including the "no passes left" state.
4. Pass start: store end time, schedule relock, ongoing countdown notification with "Lock now".
5. Relock: alarm fires, user sent home if Instagram is in front; backstop check on every event.
6. Lock setup checklist with restricted-settings guidance and live detection of the service state.
7. Settings row and "lock is off" banner when the service gets disabled.

**Done when:** every item in the device checklist's "Lock" section passes on Android 13 or newer with a sideloaded build.

## Phase 7: Release

1. Generate the release keystore, store it as GitHub secrets, back it up offline.
2. In-app update check against GitHub Releases; Settings row + one-time inbox banner.
3. Local debug log with export from Settings.
4. Copy pass: every string checked against design.md copy rules.
5. Run the full device checklist on at least two devices (one Pixel or stock Android, one Samsung or Xiaomi).
6. Tag `v1.0.0`.

**Done when:** the GitHub Release has a signed APK that installs over a previous build without losing the session.

## Device checklist

Run before every release and after any visible Instagram change.

### Session

- [ ] Fresh install, sign in with username and password
- [ ] Sign in with two-factor (SMS or app)
- [ ] Session survives force-stop, reboot and an app update
- [ ] Sign out of this device clears the session

### Messaging

- [ ] Send and receive text, photo, video, voice note
- [ ] React, reply, unsend
- [ ] Shared post opens in place and back returns to the thread
- [ ] Shared reel plays; swiping does not move to another reel
- [ ] Shared story opens; closing it returns to the thread, not the feed
- [ ] New message, search, message requests
- [ ] Group chats

### Cage

- [ ] `/` redirects to the inbox
- [ ] `/reels/`, `/reel/`, `/explore/` redirect
- [ ] A profile's Reels, Tagged and Saved tabs are hidden and their URLs redirect
- [ ] No Instagram tab bar, no Create button, no "Suggested for you" anywhere
- [ ] Back from the inbox exits the app; back never lands on a feed page
- [ ] Hides hold in light, dark and one non-English language

### Notifications

- [ ] Background notification within 15 min of a new message
- [ ] No notification while the app is open
- [ ] Text never includes message content

### Lock

- [ ] Opening the Instagram app shows the lock sheet
- [ ] A pass opens Instagram for its exact length; countdown notification visible
- [ ] "Lock now" ends the pass immediately
- [ ] Relock happens even if the app was swiped away
- [ ] Passes reset at local midnight
- [ ] "No passes left" state shows and links to the inbox
- [ ] Disabling the service shows the banner in the app

### Look

- [ ] Light and dark, no white flash on launch or thread open
- [ ] 200% font scale on all native screens
- [ ] TalkBack reads every native control

## Open decisions

The build ships with the default in the last column; each is one setting to change.

| Decision | Options | Needed by | Current default |
| --- | --- | --- | --- |
| Pass policy default | 2 × 5 min (Konvo v1.9.0), or 5 + 1 min (earlier Konvo build) | Phase 6 | 2 × 5 min (`PassPolicy.Default`); the user can pick 1 × 5, 3 × 5, 5 + 1 or 2 × 10 in lock setup |
| Patch file host | GitHub Pages on a small public repo, or raw file in a public gist | Phase 4 | `patch/rules-patch.json` in this repo via raw GitHub (`dms.patchUrl` in `gradle.properties`). Works only once the repo, or a copy of the file, is public; until then the fetch fails silently and the bundled rules apply |
| Update channel | Public releases repo (auto-update works), or private repo with manual installs | Phase 7 | GitHub Releases API on this repo (`dms.releasesRepo`). A private repo returns 404 and the app shows no update |
| Icon | Own bubble mark (planned), or something closer to Instagram's glyph for personal use | Phase 0 | Instagram's Direct glyph, and Instagram's glyphs throughout the native frame |

## Risks

| Risk | Effect | Mitigation |
| --- | --- | --- |
| Instagram changes markup | Feed doorways reappear | URL rules are the real gate; selectors patched remotely |
| Instagram changes routes | A new feed URL slips through | Native gate + new block rule via patch |
| Instagram flags the WebView | Login fails or extra checkpoints | Chrome-mobile UA; no automation beyond the badge poll the website itself makes |
| Badge endpoint changes | No background notifications | Fail silently; notifications are a bonus, the inbox still works |
| OEM battery killers | Late notifications, missed relock | Backstop relock on every accessibility event; battery settings link |
| Restricted settings confusion | Users cannot enable the lock | Guided setup with screenshots and live detection |
| Name and icon | Using "Instagram" is fine on your own phone; not for public distribution | Keep the APK personal; rename before any public release |
