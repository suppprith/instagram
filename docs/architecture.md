# Architecture

This app (launcher name **Instagram**, package `com.suppprith.dms`) is an Android-only app that shows Instagram's direct messages and nothing else. Like Konvo (see [konvo-teardown.md](konvo-teardown.md)), it runs Instagram's own mobile website in a caged `WebView`. It is not an API client.

## Principles

1. **Instagram's website does the messaging.** We never handle the password, never call private messaging endpoints, never store messages.
2. **Block the feed, not the login.** URL block-list with a redirect to the inbox. Everything else (login, 2FA, challenges, profiles, single posts) passes through.
3. **Enforce twice.** In the page (injected JS) and natively (`WebViewClient` callbacks). Either one alone must be enough to stop the feed.
4. **Rules are data.** URL patterns and hide selectors live in a JSON rules file, bundled in the APK and patchable remotely.
5. **No backend.** Everything runs on the phone. The only network calls outside Instagram are the patch file and the update check, both static files.
6. **Never trap the user.** Every wait on Instagram's markup has a timeout. A broken selector should at worst show something we meant to hide, never a blank screen.

## Stack

| Concern | Choice |
| --- | --- |
| Language | Kotlin 2.x |
| UI (native parts) | Jetpack Compose + Material 3 |
| Web content | `android.webkit.WebView` + `androidx.webkit` |
| Background work | WorkManager |
| Settings and pass state | DataStore (Preferences) |
| JSON | kotlinx.serialization |
| Build | Gradle (Kotlin DSL), version catalog, AGP 8.x |
| SDK levels | `minSdk 26` (Android 8.0), `targetSdk` and `compileSdk` latest stable |
| CI | GitHub Actions: build, lint, unit tests, JS cage tests, signed release APK on tag |
| Deliverable | Signed universal APK attached to a GitHub Release |
| JS tests | Node + jsdom (same approach as Konvo's `test_cage.js`) |

No Hilt, no Room, no networking library at the start. Plain constructor injection and `HttpURLConnection` or OkHttp for the two small requests we make.

## High-level diagram

```
+-------------------------------------------------------------+
| MainActivity (Compose)                                      |
|                                                             |
|  +-------------------------------------------------------+  |
|  | CageWebView  (instagram.com/direct/inbox/)            |  |
|  |   document-start script: cage.js + rules.json         |  |
|  |   WebMessageListener "dms" <--> JsBridge              |  |
|  +-------------------------------------------------------+  |
|  | BottomBar: Messages | Activity | Profile            |  |
|  +-------------------------------------------------------+  |
+-------------------------------------------------------------+
        |                     |                       |
   CageClient            PatchRepository         PassRepository
 (WebViewClient:          (bundled rules +        (DataStore: passes
  URL gate, external       cached remote patch)    left, active pass)
  links, errors)                                         |
        |                                                |
   UnreadWorker (WorkManager, every 15 min)     LockAccessibilityService
   GET /api/v1/direct_v2/get_badge_count/        watches com.instagram.android
   with the WebView's cookies                    -> LockActivity (reason + pass)
```

## Package layout

Single `:app` module to start. Split later only if build times demand it.

```
app/src/main/
  assets/
    cage/cage.js            injected at document start
    cage/rules.json         bundled baseline rules
  java/com/suppprith/dms/
    App.kt                  AppGraph: plain constructor injection
    MainActivity.kt         splash, permissions, intents, implements ui/Actions
    web/
      WebHost.kt            owns the WebView: settings, injection, gate, navigation, session
      CageClient.kt         WebViewClient: native URL gate, external links, errors
      CageChromeClient.kt   file chooser, mic permission, full-screen video, popups
      JsBridge.kt           WebMessageListener, typed messages in and out
      UrlPolicy.kt          in-app vs Custom Tab vs ignore (pure, unit tested)
      UserAgent*.kt         Chrome-mobile UA without the "wv" token
      FilePicker.kt, Downloads.kt, Links.kt
    cage/
      Rules.kt              data classes + matcher shared by native gate
      RulesPatch.kt         patch validator
      PatchRepository.kt    loads bundled rules, fetches and caches remote patch
    ui/
      AppScreen.kt          WebView + bottom bar + banners + overlays
      Routes.kt             tab and bottom bar rules per path (pure, unit tested)
      BottomBar.kt, Onboarding.kt, SettingsSheet.kt, LockSetupScreen.kt, DebugLogScreen.kt
      theme/
    notify/
      UnreadWorker.kt
      Notifications.kt      channels, posting, tap intent
      BadgeCount.kt         endpoint, parsing, notify rule (pure, unit tested)
    lock/
      PassPolicy.kt         single source of truth for pass rules
      PassState.kt          day rollover, expiry, clock changes (pure, unit tested)
      PassRepository.kt     DataStore-backed state
      LockAccessibilityService.kt
      LockActivity.kt       "What do you need Instagram for?"
      RelockScheduler.kt    exact alarm + service timer for pass expiry
      RelockReceiver.kt     alarm and "Lock now"
    update/                 GitHub Releases check
    util/                   settings store, debug log, HTTP
  res/xml/
    lock_accessibility_service.xml
```

## The web view

### Loading

- Start URL: `https://www.instagram.com/direct/inbox/`.
- Show a native splash (app logo on the theme background) until the inbox has rendered, detected by the bridge sending `ready` once the inbox list or composer exists. Timeout after 8 s and show whatever is there.
- Set the WebView background colour to match the theme to avoid a white flash in dark mode.

### Settings

```kotlin
settings.javaScriptEnabled = true
settings.domStorageEnabled = true
settings.mediaPlaybackRequiresUserGesture = false   // voice notes, videos in threads
settings.allowFileAccess = false
settings.allowContentAccess = false
settings.setSupportMultipleWindows(true)            // handle window.open ourselves
settings.userAgentString = UserAgent.chromeMobile(context)
CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true) // Meta login chain
```

Safe Browsing stays on. Dark mode: target SDK 33+ makes the WebView follow the app theme via `prefers-color-scheme`; Instagram already supports it, so no algorithmic darkening.

### User agent

Android's default WebView UA contains `; wv)` and `Version/4.0`, which marks it as an embedded browser. Some Meta flows (Continue with Facebook) and Google refuse embedded browsers. Build the UA from `WebSettings.getDefaultUserAgent()` and strip those two tokens so it reads as Chrome for Android. Same idea as Konvo presenting itself as Safari.

### Injection

- Use `WebViewCompat.addDocumentStartJavaScript(webView, script, setOf("https://www.instagram.com"))` when `WebViewFeature.DOCUMENT_START_SCRIPT` is supported (any updated WebView). This runs before Instagram's own code, the same as `WKUserScript` at document start.
- Fallback for very old WebViews: `evaluateJavascript` in `onPageStarted`. Slightly late but the native gate covers the gap.
- The script is `cage.js` with the merged rules inlined as `window.__DMS_RULES__ = {...}` ahead of it, so the page never needs to fetch the patch itself. This avoids Konvo's CSP problem where one Instagram variant blocked the patch fetch.
- Origin-restricted: the script is never injected into Meta login domains, reCAPTCHA or anything else.

### Bridge

`WebViewCompat.addWebMessageListener(webView, "dms", setOf("https://www.instagram.com"), listener)`. Unlike `addJavascriptInterface`, this exposes nothing to other origins or iframes.

Messages are small JSON objects with a `type`:

| Direction | Type | Purpose |
| --- | --- | --- |
| page -> app | `hello` | New document booted; gives the app a reply channel for this page |
| page -> app | `ready` | Inbox, thread or sign-in rendered; hide splash |
| page -> app | `route` | Current path, so the bottom bar can highlight and hide itself inside threads |
| page -> app | `blocked` | A feed URL was bounced (local counter only) |
| page -> app | `haptic` | Message sent; short vibration |
| page -> app | `error` | Cage exception caught, for local debug log |
| page -> app | `badge` | Unread count from the in-page 30 s poll; drives the unread dot |
| page -> app | `user` | Signed-in username, read from Instagram's own (hidden) tab bar |
| app -> page | `navigate` | Bottom bar taps: go to inbox, notifications, own profile |
| app -> page | `rules` | Updated rules after a patch download, applied live |
| app -> page | `scrollTop` | Tapping the active tab |
| app -> page | `badge` | Poll the unread count now (app returned to the foreground) |

## The cage

### Rules file

```json
{
  "version": 1,
  "block": [
    "^/$",
    "^/reels(/|$)",
    "^/reel/?$",
    "^/explore(/|$)",
    "^/[A-Za-z0-9._]+/(reels|tagged|saved)(/|$)"
  ],
  "hide": ["...css selectors for feed doorways inside allowed pages..."],
  "css": "",
  "redirect": "/direct/inbox/"
}
```

`Rules.kt` and `cage.js` read the same file, so native and JS can never disagree. The starting rule set is Konvo's (MIT licensed, credited in `NOTICE`), then verified on Android Chrome's mobile layout, which can differ from iOS Safari's.

### In-page enforcement (`cage.js`)

1. Wrap `history.pushState` and `history.replaceState`; listen to `popstate`; run on `DOMContentLoaded`; plus an 800 ms interval as a safety net.
2. `enforce()`: if `location.pathname` matches a block rule, `location.replace(redirect)`.
3. Inject a `<style>` with `display:none !important` for every hide selector plus raw `css`.
4. Rewrite Instagram's own bottom tab bar out of existence (we draw our own natively).
5. Inside `/reel/<code>/`, stop vertical swipe into the next reel (Konvo does the same).
6. Report route changes and `ready` over the bridge.
7. Wrap everything in try/catch; an exception never stops navigation.

### Native enforcement (`CageClient`)

- `doUpdateVisitedHistory(view, url, isReload)` fires on every SPA history change. If the path matches a block rule and the host is `www.instagram.com`, `loadUrl(inbox)`. This is the second line of defence if Instagram changes how it routes or the script fails.
- `shouldOverrideUrlLoading`:
  - `instagram.com` and Meta login/verification hosts (`facebook.com`, `accountscenter`, `fbcdn`, reCAPTCHA): stay in the WebView.
  - `l.instagram.com/?u=...` outbound link wrappers and every other host: open in a Chrome Custom Tab.
  - `intent://` and `instagram://` schemes: ignore, or offer to open the Instagram app if a pass is active.
- `onReceivedError` for the main frame: show the native offline screen with Retry.

### Bottom bar

Native Compose bar, hidden while a thread or full-screen media is open (based on the `route` message):

- **Messages** -> `/direct/inbox/`
- **Activity** -> `/notifications/` (likes and follow requests, no feed)
- **Profile** -> own profile, read from the `ds_user_id` cookie

Settings open from a native gear on the Profile tab. The lock has no tab; it appears when the user opens the official Instagram app. Visual spec in [design.md](design.md).

### Back button

`OnBackPressedCallback`: leave full-screen video first; then, if the current path is not the inbox, go back to the nearest history entry that is not a blocked page (skipping them avoids a bounce flicker), or load the inbox if there is none. At the inbox, back goes to the system, which finishes the activity on Android 11 and older and moves the task to the back on 12 and newer, so reopening is instant.

## Media and permissions

| Feature | Implementation |
| --- | --- |
| Send photos and videos | `WebChromeClient.onShowFileChooser` -> `ActivityResultContracts.GetMultipleContents`, plus a camera capture option |
| Voice notes | `onPermissionRequest` grants `RESOURCE_AUDIO_CAPTURE` after the app holds `RECORD_AUDIO` |
| Camera | The file chooser offers the camera app (`ACTION_IMAGE_CAPTURE` / `ACTION_VIDEO_CAPTURE` into a cache file shared with `FileProvider`), so the app needs no `CAMERA` permission. In-page `RESOURCE_VIDEO_CAPTURE` is denied; Instagram's mobile web DMs do not use it |
| Save media | `setDownloadListener` -> `DownloadManager` (only for user-initiated downloads) |
| Popups (`window.open`) | `onCreateWindow` -> load in a dialog WebView, or route to Custom Tabs if off-site |
| Full-screen video | `onShowCustomView` / `onHideCustomView` |
| Share into the app | Optional: `ACTION_SEND` text intent opens the inbox so the user can paste |

Runtime permissions are asked at the moment of use, never during onboarding.

## Session

`CookieManager` persists cookies to disk. Call `CookieManager.getInstance().flush()` in `onPause` so a swipe-away never loses the session. No snapshot workaround needed.

Logout: Instagram's own logout from the profile menu works. Settings also offers "Sign out of this device", which clears cookies and WebStorage.

## Unread notifications

`UnreadWorker` (WorkManager `PeriodicWorkRequest`, 15 minutes, network required):

1. Read cookies with `CookieManager.getInstance().getCookie("https://www.instagram.com")`. Skip if no `sessionid`.
2. `GET https://www.instagram.com/api/v1/direct_v2/get_badge_count/` with the cookie header, `X-IG-App-ID: 936619743392459`, `X-CSRFToken` from `csrftoken`, and the same user agent as the WebView.
3. If `badge_count` is higher than the last stored value and the app is not in the foreground, post a notification: "N unread conversations". No message content, ever.
4. Store the new count. Opening the app resets it.

While the app is in the foreground, the page polls every 30 s instead (cheap, same origin).

Android 13+: ask for `POST_NOTIFICATIONS` during onboarding with an explanation screen. Battery optimisation may delay work on some OEMs; document it and offer a link to the battery settings, but do not demand exemption.

## The lock (optional feature)

The user can lock the official Instagram app (`com.instagram.android`) so the only easy way into Instagram is this app.

### Detection

`LockAccessibilityService`, configured for `typeWindowStateChanged` events only, with `canRetrieveWindowContent="false"`. It never sees what is on screen.

It does not set `packageNames`. With the filter, the service would only hear about Instagram, so it could not tell when the user has left Instagram, and a pass ending would send the user home from whatever app they had switched to. Instead it compares each event's package name with `com.instagram.android` and keeps a single boolean, "Instagram is in front". Nothing about other apps is stored or logged. System UI and keyboard windows are ignored because they sit on top of the current app.

When Instagram comes to the foreground and no pass is active:

1. Launch `LockActivity` (`FLAG_ACTIVITY_NEW_TASK`), drawn on top of Instagram.
2. If the user leaves without a pass, `performGlobalAction(GLOBAL_ACTION_HOME)`.

Accessibility is chosen over a `UsageStatsManager` polling foreground service because it is instant, uses no battery while idle and needs no persistent notification. Fallback if the service is disabled: show a banner in the app saying the lock is off.

### Lock screen

Same structure as Konvo's sheet (reason, then a timed pass). Exact layout and copy are in [design.md](design.md#8-lock-sheet).

The chosen reason is stored locally (for the user's own weekly stats), never uploaded.

### Pass policy

One file, one source of truth:

```kotlin
object PassPolicy {
    val lengthsMinutes = listOf(5, 5)      // the whole policy
    val perDay get() = lengthsMinutes.size
    fun minutesAfterUsed(used: Int): Int? = lengthsMinutes.getOrNull(used)
}
```

- Day resets at local midnight (compare stored day key `yyyy-MM-dd`).
- Starting a pass stores `passEndsAt`. `RelockScheduler` sets an exact alarm (or `setAndAllowWhileIdle` if exact alarms are not granted) for that moment; when it fires, if Instagram is in the foreground the service sends the user home and shows the lock.
- While a pass runs, an ongoing notification shows a countdown (`setUsesChronometer` + `setChronometerCountDown`) with a "Lock now" action that ends the pass early.
- Backstop: every accessibility event re-checks `now > passEndsAt`, so a missed alarm can never extend a pass.
- Android has no 15-minute minimum like Apple's DeviceActivity, so passes can be any length.

### Escape hatches

The user can always disable the lock in the app's settings or by turning off the accessibility service. This is a commitment device, not parental control. No device admin, no uninstall protection.

## Remote patch channel

- Patch file: a static `rules-patch.json` served from GitHub Pages or a raw file in a public repo.
- `PatchRepository` fetches it on app start and at most once per hour, validates it (schema version, regexes compile, size under 64 KB), caches the last good copy in app storage.
- Patch format: `{ "schema": 1, "block": [...], "hide": [...], "css": "...", "note": "..." }`. Any other key (for example `unblock` or `redirect`) rejects the whole patch. Block rules must be anchored at `^/` and must not match the inbox, a thread, or the sign-in and challenge pages, so a bad patch can never lock the user out.
- Effective rules = bundled rules + cached patch (additive only: more blocks, more hides, more CSS; a patch can never unblock a baseline rule).
- New rules are pushed into a running page with the `rules` bridge message and used for the next document-start injection.
- Data only. Google Play does allow JavaScript inside a WebView to be fetched remotely, but keeping it data-only keeps review and security simple.

## Distribution: a signed APK

The deliverable is a sideloaded APK, not a Play Store listing. This sidesteps Play's policy against apps that are mainly a WebView of a site the developer does not own, and its Accessibility API declaration review.

- **Build:** `./gradlew assembleRelease` produces `app-release.apk`, R8-minified, signed with our own release keystore (APK signature scheme v2 + v3).
- **Keystore:** created once, kept outside the repo. CI gets it as a base64 GitHub Actions secret plus passwords. Losing it means users must uninstall to update, so back it up.
- **Release flow:** pushing a `v*` tag runs a workflow that builds, signs, and attaches `instagram-dms-<version>.apk` and its SHA-256 to a GitHub Release.
- **Updates:** the app checks the GitHub Releases API once a day (`/repos/suppprith/instagram/releases/latest`), compares `versionCode`, and shows "Update available" linking to the APK. Installing uses the system installer (`REQUEST_INSTALL_PACKAGES` is not needed if we just open the download in the browser). Users of [Obtainium](https://github.com/ImranR98/Obtainium) can track the repo directly. The repo is private, so this needs a decision: make releases public (a separate public releases repo works) or skip auto-update.
- **ABI:** no native code, so one universal APK.

### Sideloading and the lock

Android 13+ applies **restricted settings** to sideloaded apps: the accessibility service toggle is greyed out until the user opens App info, taps the three-dot menu, and chooses **Allow restricted settings**. Onboarding for the lock must walk through this step with screenshots, and detect whether the service is actually enabled afterwards.

## Privacy

- Nothing about the user's Instagram content leaves the phone.
- No analytics in v1. If added later: opt-in, event counts only, no Instagram ids.
- No crash reporter in v1; a local debug log the user can export from settings.
- Network calls outside Instagram: the patch file fetch and the update check only.

## Testing

| Layer | Tool | What |
| --- | --- | --- |
| Rules matcher | JUnit | Every block rule against allowed and blocked paths (`/`, `/reels/`, `/reel/`, `/reel/abc/`, `/explore/`, `/user/reels/`, `/p/abc/`, `/stories/user/123/`, `/direct/t/123/`) |
| Pass policy | JUnit | Day rollover, passes exhausted, backstop expiry, clock changes |
| `cage.js` | Node + jsdom | Boot a page at each path, assert bounce or pass-through; pushState and popstate handling; hide styles injected |
| Patch validation | JUnit | Bad JSON, bad regex, oversized, unblocking attempts rejected |
| WebView integration | Manual on device | Login incl. 2FA, send text/photo/voice, open shared post and reel, try every feed entry point |
| Lock | Manual on device | Instagram blocked, pass grants access, relock at expiry, midnight reset |

Instagram's live markup cannot be tested in CI. Keep a manual checklist ([roadmap.md](roadmap.md#device-checklist)) and run it before every release and whenever Instagram ships a visible change.
