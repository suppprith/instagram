# Product design

The app is named **Instagram** on the home screen. It replaces the Instagram icon for everyday use. The official app stays installed, locked, for posting and calls.

## Principles

1. **The inbox is the app.** It opens to messages. There is no home, no landing page, no dashboard.
2. **Nothing to scroll that is not a person.** If a surface ranks content, it does not exist here.
3. **One job per screen.** One primary action, one decision.
4. **Instagram's UI is the content, ours is the frame.** Native chrome is quiet so the messages look exactly like Instagram.
5. **No guilt, no gamification.** No streaks, no "time saved", no screen-time quiz, no badges. The absence of the feed is the feature.
6. **Fast is a feature.** Cold start to a usable inbox in under 2 s on a mid-range phone.

## Copy rules

Applies to every string in the app, the README and release notes.

- Literal and short. Say what happens.
- Sentence case. No exclamation marks, no emoji, no em dashes.
- Buttons are verbs: "Sign in", "Allow", "Retry", "Lock now".
- No filler words: simply, just, seamless, effortless, journey, unlock your, supercharge, boost, magic, delightful.
- No apologies or cheerleading: no "Oops", "Uh oh", "You're all set!", "Great job".
- Name things once and keep the name. The other app is always "the Instagram app"; ours is never called anything in its own UI.

| Instead of | Write |
| --- | --- |
| Welcome to your distraction-free Instagram experience! | Instagram messages. Nothing else. |
| Oops! Something went wrong. | No connection. |
| Unlock your Instagram for 5 minutes | Open for 5 min |
| You have 2 unlocks remaining today | 2 left today |
| Stay in the loop with notifications | Get notified about new messages |

## Visual system

The web content is Instagram's own mobile site, which uses the system font and pure black or white backgrounds. Native parts match it so there is no visible seam.

### Color

Follows the system light or dark setting. No in-app theme switch.

| Token | Light | Dark | Use |
| --- | --- | --- | --- |
| `bg` | `#FFFFFF` | `#000000` | Screen and WebView background |
| `surface` | `#F2F2F2` | `#121212` | Sheets, list rows |
| `text` | `#0A0A0A` | `#F5F5F5` | Primary text |
| `text-muted` | `#737373` | `#A8A8A8` | Secondary text |
| `divider` | `#DBDBDB` | `#262626` | Hairlines |
| `accent` | `#0095F6` | `#0095F6` | Primary button, active state. Instagram's own blue, so native and web agree |
| `danger` | `#ED4956` | `#ED4956` | Destructive actions only |

One accent, used only for the single primary action on a screen and the active tab. Everything else is greyscale.

### Type

System font (Roboto on most devices). Four sizes only:

| Style | Size / line | Weight | Use |
| --- | --- | --- | --- |
| Title | 24 / 30 sp | 700 | One per screen, top left |
| Body | 16 / 22 sp | 400 | Default |
| Label | 14 / 20 sp | 600 | Buttons, row titles |
| Caption | 12 / 16 sp | 400 | Secondary lines |

### Space, shape, icons

- 4 dp grid. Screen padding 20 dp. Gaps 8, 12, 16, 24.
- Radius: 12 dp for buttons and rows, 20 dp for sheet tops.
- Hairline dividers (1 px), no shadows, no gradients, no illustrations.
- Icons: outlined, 24 dp, 1.75 px stroke, to match Instagram's web icons. Filled variant only for the active tab.
- Touch targets at least 48 dp.

### Motion

- 200 ms, standard easing, for sheets and screen changes. No bounce, no spring overshoot.
- Opening a thread: shared-axis horizontal slide, so it feels native rather than a page load.
- Respect the system "Remove animations" setting: all motion becomes instant.

### App icon

- Adaptive icon: a single outlined message bubble, white on the accent blue.
- Android 13+ themed (monochrome) icon provided.
- Label: "Instagram".

## Navigation

```
Inbox ─┬─ Thread
       ├─ Activity
       └─ Profile ── Settings (sheet)

Instagram app (official) ── Lock sheet ── Pass running
```

### Bottom bar

- Three icons, no labels: Messages, Activity (heart), Profile (avatar).
- 56 dp tall, `bg` color, hairline top divider.
- Hidden inside a thread, in full-screen media and while the keyboard is open.
- Tapping the active tab scrolls the inbox to the top.
- Unread dot on Messages when the badge count is above zero.

## Screens

### 1. Launch

App icon centred on `bg`. No text, no spinner for the first 600 ms. Fades into the inbox the moment it is ready. After 8 s, show the page as it is.

### 2. First run (one screen)

```
┌───────────────────────────────┐
│                               │
│                               │
│  Instagram messages.          │  Title
│  Nothing else.                │
│                               │
│  No feed, Reels or Explore.   │  Body, muted
│                               │
│                               │
│                               │
│  ┌─────────────────────────┐  │
│  │        Sign in          │  │  Primary
│  └─────────────────────────┘  │
│  You sign in on Instagram's   │  Caption, muted, centred
│  own page. Your password      │
│  never touches this app.      │
└───────────────────────────────┘
```

No carousel, no quiz, no account creation.

### 3. Sign in

Instagram's own login page, full screen. Two-factor, challenge and "Save login info" pages pass through untouched. The bottom bar does not appear until the inbox loads.

### 4. Notifications permission (once, after first sign-in)

```
  Get notified about new messages

  Shows how many conversations are unread.
  Never the message text.

  [ Allow ]          Not now
```

"Not now" is never asked again automatically; it lives in Settings.

### 5. Inbox, thread, activity, profile

Instagram's pages with the feed doorways removed. What the user sees:

- Inbox: your username, notes row, message list, search, new message button.
- Thread: unchanged. Text, photos, voice notes, reactions, replies, shared posts and reels open in place.
- Activity: likes, follows, requests.
- Profile: yours and others'. The grid opens single posts. The Reels and Tagged tabs are gone.

Removed: home, Explore, Reels tab, the Create button, Instagram's bottom tab bar, suggested accounts, "Suggested for you" rows.

### 6. Settings (sheet from the gear on Profile)

Plain list, grouped, no icons:

```
  Notifications            On
  Lock the Instagram app   Off
  ─────────────
  Instagram account        >   (opens /accounts/edit/)
  Sign out of this device
  ─────────────
  Check for updates        v1.0.0
  Debug log                >
```

### 7. Lock setup

A checklist, each row turns into a check when done. The app detects completion; the user never confirms manually.

```
  Lock the Instagram app

  The Instagram app opens only with a short pass.
  Use it to post or call.

  1  Allow restricted settings          >
  2  Turn on the lock service           >
  3  Passes per day                  2 × 5 min

  [ Done ]   (enabled when 1 and 2 are checked)
```

Step 1 shows two screenshots of App info > menu > Allow restricted settings, since the system screen is easy to miss. Step 1 is hidden on devices where it does not apply.

### 8. Lock sheet

Shown on top of the Instagram app when it opens without a pass.

```
┌───────────────────────────────┐
│                               │
│  What do you need             │  Title
│  Instagram for?               │
│                               │
│  ○ Post a story               │  Single-select rows
│  ○ Post a photo or Reel       │
│  ○ Call someone               │
│  ○ Something else             │
│                               │
│  ┌─────────────────────────┐  │
│  │     Open for 5 min      │  │  Primary, disabled until a row is picked
│  └─────────────────────────┘  │
│  2 left today        Go back  │  Caption / text button
└───────────────────────────────┘
```

- "Go back" returns to the home screen.
- When no passes are left: rows and button are replaced by "No passes left today. Resets at midnight." and a single "Open messages" button that opens our inbox.

### 9. Pass running

No overlay on top of Instagram. An ongoing notification:

```
  Instagram open · 4:12            Lock now
```

At zero the user is sent home and the lock sheet shows next time.

### 10. Offline

```
  No connection.
  [ Retry ]
```

Retries automatically when the network returns.

### 11. Update available

One row in Settings changes to "Update available, v1.1.0". A single dismissible banner on the inbox, once per version. No dialogs.

## States checklist

Every screen is designed for: loading, empty, error, offline, very long text, 200% font scale, dark and light.

## Accessibility

- TalkBack labels on every native control. Bottom bar icons have content descriptions even though they have no visible labels.
- Contrast AA for all text tokens on `bg` and `surface`.
- Layouts reflow at 200% font scale; nothing truncates a primary action.
- The lock sheet is fully operable with TalkBack and a switch device.

## Out of scope

Posting, stories creation, calls (use the Instagram app with a pass), multiple accounts beyond Instagram's own switcher, tablets and foldables beyond basic support, widgets, Wear OS.
