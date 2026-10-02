// jsdom tests for the cage. Run with `npm test` in this folder.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { JSDOM } from 'jsdom';

const root = new URL('../app/src/', import.meta.url);
const CAGE = readFileSync(new URL('main/assets/cage/cage.js', root), 'utf8');
const RULES = JSON.parse(readFileSync(new URL('main/assets/cage/rules.json', root), 'utf8'));
const PATHS = JSON.parse(readFileSync(new URL('test/resources/cage/paths.json', root), 'utf8'));

const EMPTY = '<!doctype html><html><head></head><body></body></html>';
const wait = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

/** Boots cage.js in a fresh page the way the app does: bridge object, then rules, then the script. */
function boot(t, path, { html = EMPTY, rules = RULES, cookie, before } = {}) {
  const dom = new JSDOM(html, { url: 'https://www.instagram.com' + path, runScripts: 'outside-only', pretendToBeVisual: true });
  const w = dom.window;
  const messages = [];
  const gone = [];
  let handler = null;
  w.dms = {
    postMessage: (text) => messages.push(JSON.parse(text)),
    addEventListener: (type, fn) => { if (type === 'message') handler = fn; },
  };
  w.__dmsGo = (url) => gone.push(url);
  if (rules) w.__DMS_RULES__ = rules;
  if (cookie) w.document.cookie = cookie;
  if (before) before(w);
  w.eval(CAGE);
  t.after(() => w.close());
  return {
    w,
    messages,
    gone,
    of: (type) => messages.filter((m) => m.type === type),
    send: (message) => handler({ data: JSON.stringify(message) }),
  };
}

test('every blocked path in the shared fixture bounces at document start', (t) => {
  for (const path of PATHS.blocked) {
    const page = boot(t, path);
    assert.deepEqual(page.gone, ['/direct/inbox/'], path);
    assert.equal(page.of('blocked')[0].path, path.split('?')[0], path);
  }
});

test('every allowed path in the shared fixture passes through', (t) => {
  for (const path of PATHS.allowed) {
    const page = boot(t, path);
    assert.deepEqual(page.gone, [], path);
    assert.equal(page.of('route')[0].path, path, path);
  }
});

test('built-in defaults match rules.json when no rules are inlined', (t) => {
  const page = boot(t, '/direct/inbox/', { rules: null });
  assert.deepEqual(JSON.parse(JSON.stringify(page.w.__dmsCage.rules().block)), RULES.block);
  assert.equal(page.w.__dmsCage.rules().redirect, RULES.redirect);
});

test('pushState into a feed bounces', (t) => {
  const page = boot(t, '/direct/inbox/');
  page.w.history.pushState({}, '', '/reels/');
  assert.deepEqual(page.gone, ['/direct/inbox/']);
  assert.equal(page.of('blocked').length, 1);
});

test('replaceState into a feed bounces', (t) => {
  const page = boot(t, '/someone/');
  page.w.history.replaceState({}, '', '/someone/tagged/');
  assert.deepEqual(page.gone, ['/direct/inbox/']);
});

test('pushState within allowed pages reports routes and does not bounce', (t) => {
  const page = boot(t, '/direct/inbox/');
  page.w.history.pushState({}, '', '/direct/t/42/');
  page.w.history.pushState({}, '', '/p/abc/');
  assert.deepEqual(page.gone, []);
  assert.deepEqual(page.of('route').map((m) => m.path), ['/direct/inbox/', '/direct/t/42/', '/p/abc/']);
});

test('popstate back onto a feed page bounces', async (t) => {
  const page = boot(t, '/direct/inbox/');
  page.w.history.pushState({}, '', '/explore/');
  page.w.history.pushState({}, '', '/direct/t/1/');
  assert.equal(page.gone.length, 1);
  page.w.history.back();
  await wait(50);
  assert.equal(page.w.location.pathname, '/explore/');
  assert.equal(page.gone.length, 2);
});

test('leaving a shared story for the feed returns to the thread it came from', (t) => {
  const page = boot(t, '/direct/t/123/');
  page.w.history.pushState({}, '', '/stories/friend/999/');
  page.w.history.pushState({}, '', '/');
  assert.deepEqual(page.gone, ['/direct/t/123/']);
});

test('after visiting the inbox, bounces go to the inbox again', (t) => {
  const page = boot(t, '/direct/t/123/');
  page.w.history.pushState({}, '', '/direct/inbox/');
  page.w.history.pushState({}, '', '/explore/');
  assert.deepEqual(page.gone, ['/direct/inbox/']);
});

test('repeated bounces fall back to the inbox instead of looping', (t) => {
  const page = boot(t, '/direct/t/5/');
  for (let i = 0; i < 7; i++) page.w.history.pushState({}, '', '/');
  assert.equal(page.gone.at(0), '/direct/t/5/');
  assert.equal(page.gone.at(-1), '/direct/inbox/');
});

test('hide selectors are injected as a stylesheet, invalid ones skipped', (t) => {
  const rules = { ...RULES, hide: [...RULES.hide, 'div[[[bad', '.from-patch'], css: '.raw{color:red}' };
  const page = boot(t, '/direct/inbox/', { rules });
  const css = page.w.document.getElementById('dms-cage-style').textContent;
  assert.match(css, /a\[href='\/explore\/'\]|a\[href\^='\/explore\/'\]/);
  assert.match(css, /\.from-patch\{display:none!important\}/);
  assert.match(css, /\.raw\{color:red\}/);
  assert.doesNotMatch(css, /bad/);
});

test('rules from the app apply live: new hides and new blocks', (t) => {
  const page = boot(t, '/threads/');
  assert.deepEqual(page.gone, []);
  page.send({ type: 'rules', rules: { ...RULES, block: [...RULES.block, '^/threads(/|$)'], hide: ['.new-doorway'] } });
  assert.deepEqual(page.gone, ['/direct/inbox/']);
  assert.match(page.w.document.getElementById('dms-cage-style').textContent, /\.new-doorway/);
});

test('a bad block regex is reported and the others still work', (t) => {
  const page = boot(t, '/explore/', { rules: { ...RULES, block: ['([', ...RULES.block] } });
  assert.deepEqual(page.gone, ['/direct/inbox/']);
  assert.equal(page.of('error').length, 1);
});

test("Instagram's own tab bar is hidden", async (t) => {
  const html = `<!doctype html><html><head></head><body>
    <main><a href="/direct/t/1/">thread</a></main>
    <div id="bar" style="position:fixed;bottom:0">
      <div><a href="/">h</a><a href="/explore/">e</a><a href="/reels/">r</a><a href="/direct/inbox/">d</a><a href="/me.user/"><img src="https://scontent.cdninstagram.com/me.jpg"></a></div>
    </div></body></html>`;
  const page = boot(t, '/direct/inbox/', { html });
  await wait(250);
  assert.equal(page.w.document.getElementById('bar').getAttribute('data-dms-hidden'), 'tabbar');
});

test('a fixed header with only the logo and inbox link is left alone', async (t) => {
  const html = `<!doctype html><html><head></head><body>
    <div id="header" style="position:fixed;top:0"><a href="/">logo</a><a href="/direct/inbox/">dm</a></div>
    </body></html>`;
  const page = boot(t, '/someone/', { html });
  await wait(250);
  assert.equal(page.w.document.getElementById('header').hasAttribute('data-dms-hidden'), false);
});

test('ready is sent once the inbox list renders', async (t) => {
  const html = '<!doctype html><html><head></head><body><a href="/direct/t/1/">x</a><a href="/direct/t/2/">y</a></body></html>';
  const page = boot(t, '/direct/inbox/', { html });
  await wait(250);
  assert.equal(page.of('ready').length, 1);
});

test('ready is sent on the login page so sign-in is not hidden behind the splash', async (t) => {
  const html = '<!doctype html><html><head></head><body><form><input name="username"><input type="password"></form></body></html>';
  const page = boot(t, '/accounts/login/', { html });
  await wait(250);
  assert.equal(page.of('ready').length, 1);
});

test('navigate clicks an existing link so Instagram routes without a reload', (t) => {
  const html = '<!doctype html><html><head></head><body><a id="n" href="/notifications/">n</a></body></html>';
  const page = boot(t, '/direct/inbox/', { html });
  let clicked = false;
  page.w.document.getElementById('n').addEventListener('click', (e) => { clicked = true; e.preventDefault(); });
  page.send({ type: 'navigate', path: '/notifications/' });
  assert.equal(clicked, true);
  assert.deepEqual(page.gone, []);
});

test('navigate loads the path when no link exists, and refuses blocked paths', (t) => {
  const page = boot(t, '/direct/inbox/');
  page.send({ type: 'navigate', path: '/explore/' });
  page.send({ type: 'navigate', path: 'https://evil.example/' });
  assert.deepEqual(page.gone, []);
  page.send({ type: 'navigate', path: '/notifications/' });
  assert.deepEqual(page.gone, ['/notifications/']);
});

test('badge poll reports the unread count when signed in', async (t) => {
  const requests = [];
  const page = boot(t, '/direct/inbox/', {
    cookie: 'ds_user_id=42',
    before: (w) => {
      w.document.cookie = 'csrftoken=tok';
      w.fetch = async (url, init) => { requests.push({ url, init }); return { ok: true, json: async () => ({ badge_count: 3 }) }; };
    },
  });
  page.send({ type: 'badge' });
  await wait(20);
  assert.deepEqual(page.of('badge').map((m) => m.count), [3]);
  assert.equal(requests[0].url, '/api/v1/direct_v2/get_badge_count/?no_raven=1');
  assert.equal(requests[0].init.headers['X-IG-App-ID'], '936619743392459');
  assert.equal(requests[0].init.headers['X-CSRFToken'], 'tok');
});

test('badge poll does nothing when signed out', async (t) => {
  let called = false;
  const page = boot(t, '/accounts/login/', { before: (w) => { w.fetch = async () => { called = true; }; } });
  page.send({ type: 'badge' });
  await wait(20);
  assert.equal(called, false);
});

test('sending a message produces one haptic', async (t) => {
  const html = '<!doctype html><html><head></head><body><textarea id="c"></textarea></body></html>';
  const page = boot(t, '/direct/t/1/', { html });
  const box = page.w.document.getElementById('c');
  box.value = 'hello';
  box.dispatchEvent(new page.w.KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
  box.value = '';
  await wait(300);
  assert.equal(page.of('haptic').length, 1);
});

test('Enter on an empty composer or shift+Enter does not buzz', async (t) => {
  const html = '<!doctype html><html><head></head><body><textarea id="c"></textarea></body></html>';
  const page = boot(t, '/direct/t/1/', { html });
  const box = page.w.document.getElementById('c');
  box.dispatchEvent(new page.w.KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
  box.value = 'line';
  box.dispatchEvent(new page.w.KeyboardEvent('keydown', { key: 'Enter', shiftKey: true, bubbles: true }));
  await wait(300);
  assert.equal(page.of('haptic').length, 0);
});

function touch(w, type, x, y, target) {
  const event = new w.Event(type, { bubbles: true, cancelable: true });
  event.touches = [{ clientX: x, clientY: y }];
  (target || w.document.body).dispatchEvent(event);
  return event;
}

test('a shared reel blocks vertical swipes from the first pixel', (t) => {
  const reel = boot(t, '/reel/abc/');
  let instagramSaw = 0;
  reel.w.document.body.addEventListener('touchmove', () => instagramSaw++);
  touch(reel.w, 'touchstart', 100, 500);
  assert.equal(touch(reel.w, 'touchmove', 100, 498).defaultPrevented, true);
  assert.equal(touch(reel.w, 'touchmove', 100, 300).defaultPrevented, true);
  assert.equal(instagramSaw, 0, "Instagram's swipe handler never sees the gesture");
});

test('horizontal swipes and other pages are left alone', (t) => {
  const reel = boot(t, '/reel/abc/');
  touch(reel.w, 'touchstart', 100, 500);
  assert.equal(touch(reel.w, 'touchmove', 300, 505).defaultPrevented, false);

  const thread = boot(t, '/direct/t/1/');
  touch(thread.w, 'touchstart', 100, 500);
  assert.equal(touch(thread.w, 'touchmove', 100, 300).defaultPrevented, false);
});

test('the comment sheet on a reel still scrolls', (t) => {
  const html = '<!doctype html><html><head></head><body><div role="dialog"><ul id="c"><li>x</li></ul></div></body></html>';
  const reel = boot(t, '/reel/abc/', { html });
  touch(reel.w, 'touchstart', 100, 500);
  const list = reel.w.document.getElementById('c');
  assert.equal(touch(reel.w, 'touchmove', 100, 300, list).defaultPrevented, false);
});

test('a shared reel turns off vertical panning in CSS', (t) => {
  const reel = boot(t, '/reel/abc/');
  assert.equal(reel.w.document.documentElement.classList.contains('dms-single-reel'), true);
  const css = reel.w.document.getElementById('dms-cage-style').textContent;
  assert.match(css, /html\.dms-single-reel body \*\{touch-action:pan-x pinch-zoom!important/);
  assert.match(css, /\[role="dialog"\] \*\{touch-action:auto!important\}/);

  reel.w.history.pushState({}, '', '/direct/t/1/');
  assert.equal(reel.w.document.documentElement.classList.contains('dms-single-reel'), false);
});

test('moving on to another reel snaps back to the one that was sent', (t) => {
  const page = boot(t, '/direct/t/7/');
  page.w.history.pushState({}, '', '/reel/sent/');
  page.w.history.replaceState({}, '', '/reel/next/');
  assert.deepEqual(page.gone, ['/reel/sent/']);
  page.w.history.replaceState({}, '', '/reels/next/');
  assert.deepEqual(page.gone, ['/reel/sent/', '/direct/t/7/']);
});

test('after a reload, leaving a reel for the feed still returns to the thread', (t) => {
  const page = boot(t, '/reel/abc/', { before: (w) => w.sessionStorage.setItem('dms.lastThread', '/direct/t/9/') });
  page.w.history.pushState({}, '', '/');
  assert.deepEqual(page.gone, ['/direct/t/9/']);
});

test('the cage boots only once per document', (t) => {
  const page = boot(t, '/direct/inbox/');
  page.w.eval(CAGE);
  assert.equal(page.of('hello').length, 1);
});

test('without a bridge the cage still enforces', (t) => {
  const dom = new JSDOM(EMPTY, { url: 'https://www.instagram.com/explore/', runScripts: 'outside-only' });
  t.after(() => dom.window.close());
  const gone = [];
  dom.window.__dmsGo = (url) => gone.push(url);
  dom.window.__DMS_RULES__ = RULES;
  dom.window.eval(CAGE);
  assert.deepEqual(gone, ['/direct/inbox/']);
});
