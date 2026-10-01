# How Konvo works

Research notes on [Konvo: DMs Only](https://konvoinstall.com), the iOS and Mac app this project is modelled on. Konvo is open source under the MIT license at [matthewcycmb/konvo](https://github.com/matthewcycmb/konvo). These notes are based on the `shipaton-2026-v1.9.0` tag (iPhone build 130, submitted 25 Sep 2026) and the App Store screenshots.

## Summary

Konvo is not an Instagram client. It does not call Instagram's API on the user's behalf and has no backend that sees messages. It is **Instagram's own mobile website (`instagram.com`) running inside a locked-down web view**, with a JavaScript file injected at document start that:

1. bounces the user away from feed pages (home, Reels, Explore) back to `/direct/inbox/`,
2. hides the buttons inside allowed pages that lead to those feeds,
3. draws Konvo's own UI (onboarding, paywall, lock sheet) on top of the page.

Everything the user sees in the inbox and threads is Instagram's real web UI. That is why the screenshots look exactly like Instagram: same notes row, same "Messages" header, same thread bubbles.

## Components

| Part | Tech | Job |
| --- | --- | --- |
| Host app | Tauri 2 (Rust) wrapping `WKWebView` | Loads `instagram.com`, injects the cage script, sets the user agent |
| `cage.js` | ~300 KB plain JavaScript | The product: URL blocking, CSS hides, onboarding wall, paywall, analytics bridge |
| `KonvoStore.swift` | Swift native bridge | Purchases (RevenueCat), Screen Time lock, cookie snapshot, notifications, share sheet |
| Screen Time extensions | Swift (`ShieldConfig`, `ShieldAction`, `ActivityMonitor`) | The optional lock on the real Instagram app |
| Website | Next.js on Vercel | Landing page, privacy policy, invite API, push heartbeat, remote patch file |
| Chrome extension | Manifest V3, static redirect rules | Same cage for desktop browsers |

## The cage

### 1. URL block-list

A block-list, not an allow-list. An allow-list stranded users in Meta's login chain (two-factor, challenge pages, reCAPTCHA), so the rule became "bounce the feed surfaces, leave everything else alone". The five rules:

```
/^\/$/                                           home feed
/^\/reels(\/|$)/                                 Reels tab
/^\/reel\/?$/                                    bare /reel
/^\/explore(\/|$)/                               Explore
/^\/[A-Za-z0-9._]+\/(reels|tagged|saved)(\/|$)/  a profile's scrolling tabs
```

Any match redirects to `/direct/inbox/`.

Deliberately left open, because they are "conversation material" and not an algorithmic feed:

- `/p/<code>` single posts (someone shares a post in a DM)
- `/reel/<code>/` single reel permalink (a friend sent you a reel). The plural `/reels/` feed is blocked; the two differ by one letter on purpose.
- `/stories/` (a friend's story; exiting it goes to `/`, which bounces)
- profiles (find someone and tap Message)
- the notifications heart

### 2. Enforcement inside a single-page app

Instagram is a React SPA, so most navigation never reloads the page. Konvo catches route changes four ways:

- patches `history.pushState` and `history.replaceState` to run `enforce()` after every call
- listens to `popstate`
- runs on `DOMContentLoaded`
- a `setInterval(enforce, 800)` safety net, because some route changes skip the history API

### 3. CSS hides

About 39 selectors hide doorways that Instagram draws inside allowed pages: the inbox back chevron, the New post and Create entries, Message Requests in some places, and language-proof variants (selectors that do not depend on English `aria-label` text, verified on a French phone).

### 4. Remote patch channel

Instagram changes its markup on its own schedule and App Review takes days. Konvo fetches `https://konvoinstall.com/cage-patch.json` on every document start and caches the last good copy. The patch is data only, never code:

```json
{ "hide": ["css selectors"], "css": "raw css", "block": ["regex sources"] }
```

It is strictly additive to the rules baked into the binary. Remote JavaScript is avoided because Apple rejects downloaded code (App Review guideline 2.5.2). A fix goes live within about a minute with no app release.

Known gap: one Instagram A/B variant ships a Content Security Policy that blocks this `fetch`. A native-side fetch would avoid that (relevant for our design).

## Login and session

- The user signs in on Instagram's own login page inside the web view. Konvo never reads the password (it only checks the field is non-empty to count login attempts for analytics).
- The user agent is set to real Safari (mobile Safari on iPhone) so Instagram serves the mobile web layout and does not reject the login as an unsupported in-app browser.
- All navigation is allowed at the host level (`http`, `https`, `about`, `data`, `blob`) so Meta's login and device-verification chain is never cut off.
- iOS writes cookies to disk lazily, so a force-quit could log users out. Konvo snapshots the session cookies to Application Support and restores them on launch.

## Unread notifications

Instagram's website polls `GET /api/v1/direct_v2/get_badge_count/` (header `X-IG-App-ID: 936619743392459`, the web app id) for its tab badge. Konvo calls the same endpoint with the user's own session:

- **Mac:** the app keeps running when hidden, so it polls every 30 s and notifies when the count goes up.
- **iPhone:** a background refresh task reads the cookie snapshot and calls the endpoint. Because iOS background refresh is unreliable, the website runs a "heartbeat": a GitHub Actions cron hits `/api/push/tick` every 15 minutes, which sends a silent APNs push to every registered phone, and each phone checks its own inbox locally. The server never sees the Instagram session.

Notifications only say "N unread conversations", not message content.

## The optional lock ("Post when you need to")

Konvo can also lock the real Instagram app so the user cannot escape to the feed:

- Uses Apple's Screen Time API: `FamilyActivityPicker` to pick Instagram (exactly one app, no categories or domains), `ManagedSettings` to shield it, `DeviceActivity` to relock.
- The shield shows "Why do you want to unlock Instagram?" with reasons: Post a story, Call someone, Post a picture or Reel, Something else.
- **Passes:** a fixed number of short unlocks per day. In the v1.9.0 code `PassPolicy.lengthsMinutes = [5, 5]` (two 5-minute passes). The App Store screenshot shows an earlier policy of "5 mins & 1 min". The whole policy is one array; everything else derives from it.
- Each pass is a separately named monitoring session, because stopping one session fired the previous session's end callback and relocked the app one second after unlocking.
- A relock backstop runs on next launch (pass length + 1 minute) in case the monitor callback never fires.
- The lock is opt-in from the inbox. Arming it at purchase time did not reduce trial cancellations.

Instagram itself is still needed for posting and calls; that is what passes are for.

## Business model

- Free download, paid subscription required to use the DMs view: yearly with a 7-day trial, plus monthly. RevenueCat handles purchases and entitlements.
- Pre-login onboarding quiz (screen-time "years lost" calculation, privacy pages, a "pact" screen) leading to a paywall. Localised into English, French, Traditional Chinese and Korean.
- Invite loop: a buyer's link gives a friend 3 free days via a RevenueCat promotional entitlement, up to 3 friends per link.
- Analytics: PostHog events and RevenueCat webhooks joined on the same anonymous id.

## Data that leaves the phone

- Never: Instagram password, messages, contacts.
- PostHog: named events, build, platform, language, Instagram numeric user id (once).
- RevenueCat: purchase data.
- Konvo site: Instagram username only if the user sends an invite; push token if notifications are allowed.
- On device only: session cookie snapshot.

## Lessons worth copying

1. **Web view over private API.** No password handling, no reverse-engineered endpoints for messaging, nothing for Meta to ban as a third-party client, and every Instagram DM feature (voice notes, reactions, replies, media, vanish mode) works on day one.
2. **Block-list over allow-list** for URLs, so login and challenge flows never break.
3. **Ship rules as data** and keep a remote patch channel; selectors break, URLs rarely do.
4. **Language-proof selectors** (icon SVG paths, structure) instead of English `aria-label` text.
5. **Never freeze navigation** on broken markup: every wait has a timeout fallback.
6. **One source of truth** for the pass policy and for shared identifiers.
7. Test the cage against fixtures in jsdom, but verify every selector on a real phone before shipping.

## What does not carry over to Android

| iOS mechanism | Android equivalent |
| --- | --- |
| Tauri + `WKWebView` | Native Kotlin app with `android.webkit.WebView` (no cross-platform layer needed) |
| `WKUserScript` at document start | `WebViewCompat.addDocumentStartJavaScript` (androidx.webkit) |
| `webkit.messageHandlers` bridge | `WebViewCompat.addWebMessageListener`, restricted to the Instagram origin |
| Cookie snapshot workaround | Not needed; `CookieManager` persists, call `flush()` on pause |
| BGAppRefresh + silent push heartbeat | `WorkManager` periodic work (15 min minimum), no server needed |
| Screen Time shield | `AccessibilityService` that detects the Instagram app in the foreground and shows a lock screen |
| RevenueCat on StoreKit | RevenueCat or Google Play Billing directly |
| App Review rule against remote code | Play allows JavaScript run in a WebView, but we still keep the patch as data only |
