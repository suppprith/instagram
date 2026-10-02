/*
 * cage.js: injected at document start into https://www.instagram.com only.
 *
 * Keeps Instagram's mobile website to messages:
 *   1. bounces feed URLs (home, Reels, Explore, a profile's scrolling tabs) out of the page,
 *   2. hides the doorways to them inside allowed pages,
 *   3. reports the route, readiness and unread count to the app over the "dms" bridge.
 *
 * Rules come from window.__DMS_RULES__, which the app inlines ahead of this file from
 * assets/cage/rules.json plus the validated remote patch. Rules are data; this file is the
 * only code. Every entry point is wrapped in try/catch: a failure here may show something we
 * meant to hide, but must never stop navigation or blank the page.
 */
(function () {
  'use strict';

  if (window.__dmsCage) return;
  if (window.top !== window) return;

  var DEFAULT_RULES = {
    block: ['^/$', '^/reels(/|$)', '^/reel/?$', '^/explore(/|$)', '^/[A-Za-z0-9._]+/(reels|tagged|saved)(/|$)'],
    hide: [],
    css: '',
    redirect: '/direct/inbox/'
  };
  var IG_APP_ID = '936619743392459';
  var BADGE_URL = '/api/v1/direct_v2/get_badge_count/?no_raven=1';
  var BADGE_INTERVAL_MS = 30000;
  var ENFORCE_INTERVAL_MS = 800;
  var READY_TIMEOUT_MS = 6000;
  var STYLE_ID = 'dms-cage-style';
  var HIDDEN_ATTR = 'data-dms-hidden';
  var SINGLE_REEL_CLASS = 'dms-single-reel';
  // Links that make up Instagram's own bottom tab bar. Matching on href is language-proof.
  var NAV_HREFS = ['/', '/explore/', '/reels/', '/direct/inbox/', '/create/select/'];

  var rules = null;
  var blockPatterns = [];
  var lastPath = null;
  var lastThreadPath = readSession('dms.lastThread');
  var reelHome = null;
  var readySent = false;
  var bounceCount = 0;
  var bounceWindowStart = 0;

  // ---------------------------------------------------------------- bridge

  function post(message) {
    try {
      var bridge = window.dms;
      if (bridge && typeof bridge.postMessage === 'function') bridge.postMessage(JSON.stringify(message));
    } catch (e) { /* the bridge is optional */ }
  }

  function reportError(where, e) {
    post({ type: 'error', where: where, message: String((e && e.message) || e).slice(0, 300) });
  }

  function guard(where, fn) {
    return function () {
      try { return fn.apply(this, arguments); } catch (e) { reportError(where, e); }
    };
  }

  // Navigation goes through one function so tests can observe it (jsdom cannot navigate).
  function go(url) {
    if (typeof window.__dmsGo === 'function') window.__dmsGo(url);
    else location.replace(url);
  }

  // A bounce reloads the page, so the thread to return to is kept for the tab's lifetime.
  function readSession(key) {
    try { return sessionStorage.getItem(key); } catch (e) { return null; }
  }

  function writeSession(key, value) {
    try {
      if (value) sessionStorage.setItem(key, value);
      else sessionStorage.removeItem(key);
    } catch (e) { /* storage may be blocked */ }
  }

  // ---------------------------------------------------------------- rules

  function setRules(next) {
    var r = next || {};
    rules = {
      block: Array.isArray(r.block) ? r.block : DEFAULT_RULES.block,
      hide: Array.isArray(r.hide) ? r.hide : [],
      css: typeof r.css === 'string' ? r.css : '',
      redirect: typeof r.redirect === 'string' && r.redirect.charAt(0) === '/' ? r.redirect : DEFAULT_RULES.redirect
    };
    blockPatterns = [];
    for (var i = 0; i < rules.block.length; i++) {
      try { blockPatterns.push(new RegExp(rules.block[i])); } catch (e) { reportError('rule', e); }
    }
  }

  function isBlocked(path) {
    var p = path || '/';
    for (var i = 0; i < blockPatterns.length; i++) {
      if (blockPatterns[i].test(p)) return true;
    }
    return false;
  }

  // ---------------------------------------------------------------- styles

  function isValidSelector(selector) {
    try {
      document.createDocumentFragment().querySelector(selector);
      return true;
    } catch (e) {
      return false;
    }
  }

  function buildCss() {
    var out = '[' + HIDDEN_ATTR + ']{display:none!important}\n';
    for (var i = 0; i < rules.hide.length; i++) {
      var selector = rules.hide[i];
      if (typeof selector === 'string' && isValidSelector(selector)) {
        out += selector + '{display:none!important}\n';
      }
    }
    out += 'html,body{overscroll-behavior-y:none}\n';
    // A shared reel plays alone: no vertical panning into the next one. Comment sheets still scroll.
    out += 'html.' + SINGLE_REEL_CLASS + ',html.' + SINGLE_REEL_CLASS + ' body,html.' + SINGLE_REEL_CLASS +
      ' body *{touch-action:pan-x pinch-zoom!important;overscroll-behavior:none!important}\n';
    out += 'html.' + SINGLE_REEL_CLASS + ' [role="dialog"],html.' + SINGLE_REEL_CLASS +
      ' [role="dialog"] *{touch-action:auto!important}\n';
    return out + (rules.css || '');
  }

  function applyStyle() {
    var root = document.head || document.documentElement;
    if (!root) return;
    var style = document.getElementById(STYLE_ID);
    if (!style) {
      style = document.createElement('style');
      style.id = STYLE_ID;
      root.appendChild(style);
    } else if (!style.isConnected) {
      root.appendChild(style);
    }
    var css = buildCss();
    if (style.textContent !== css) style.textContent = css;
  }

  // ---------------------------------------------------------------- the gate

  function isThreadPath(path) {
    return /^\/direct\/t\/[^/]+\/?$/.test(path);
  }

  /** Where a bounced page goes: back to the last thread if we just came from one, else the inbox. */
  function bounceTarget() {
    if (lastThreadPath && !isBlocked(lastThreadPath)) return lastThreadPath;
    return rules.redirect;
  }

  function enforce() {
    var path = location.pathname;
    if (!isBlocked(path)) return false;

    // Never loop: more than 5 bounces in 10 s means something is wrong; fall back to the inbox.
    var now = Date.now();
    if (now - bounceWindowStart > 10000) { bounceWindowStart = now; bounceCount = 0; }
    bounceCount++;
    var target = bounceCount > 5 ? rules.redirect : bounceTarget();
    if (target === path || isBlocked(target)) target = DEFAULT_RULES.redirect;

    post({ type: 'blocked', path: path });
    go(target);
    return true;
  }

  function reelCode(path) {
    var m = /^\/reels?\/([^/]+)\/?$/.exec(path || '');
    return m ? m[1] : null;
  }

  /**
   * Backstop for a shared reel: if the page moves on to a different reel anyway (Instagram's
   * feed below a permalink), go back to the one that was sent.
   */
  function enforceSingleReel(path) {
    var code = reelCode(path);
    var previous = reelCode(lastPath);
    if (code && previous && code !== previous && reelHome) {
      post({ type: 'blocked', path: path });
      go(reelHome);
      return true;
    }
    if (code && !previous) reelHome = path;
    if (!code) reelHome = null;
    return false;
  }

  function onRoute() {
    if (enforce()) return;
    var path = location.pathname;
    if (path === lastPath) return;
    if (enforceSingleReel(path)) return;
    lastPath = path;
    if (isThreadPath(path)) lastThreadPath = path;
    else if (path.indexOf('/direct/inbox') === 0) lastThreadPath = null;
    writeSession('dms.lastThread', lastThreadPath);
    var root = document.documentElement;
    if (root && root.classList) root.classList.toggle(SINGLE_REEL_CLASS, !!reelCode(path));
    post({ type: 'route', path: path });
    scheduleScan();
  }

  function wrapHistory() {
    ['pushState', 'replaceState'].forEach(function (name) {
      var original = history[name];
      if (typeof original !== 'function') return;
      history[name] = function () {
        var result = original.apply(this, arguments);
        try { onRoute(); } catch (e) { reportError(name, e); }
        return result;
      };
    });
    window.addEventListener('popstate', guard('popstate', onRoute));
  }

  // ---------------------------------------------------------------- page scan

  var scanQueued = false;

  function scheduleScan() {
    if (scanQueued) return;
    scanQueued = true;
    setTimeout(function () {
      scanQueued = false;
      scan();
    }, 120);
  }

  function hrefOf(a) {
    return a.getAttribute('href') || '';
  }

  function isFixedOrSticky(el) {
    try {
      var position = window.getComputedStyle(el).position;
      return position === 'fixed' || position === 'sticky';
    } catch (e) {
      return false;
    }
  }

  function countNavLinks(el) {
    var links = el.querySelectorAll('a[href]');
    var seen = {};
    var count = 0;
    for (var i = 0; i < links.length; i++) {
      var href = hrefOf(links[i]);
      if (NAV_HREFS.indexOf(href) >= 0 && !seen[href]) { seen[href] = true; count++; }
    }
    return count;
  }

  /**
   * Instagram's own tab bar: a fixed or sticky container holding at least two of the feed tab
   * links. The app has no feed tabs, so this one is hidden.
   */
  function hideInstagramTabBar() {
    // Start from the feed tabs only, so a top header holding just the logo and inbox link stays.
    var anchors = document.querySelectorAll('a[href="/explore/"], a[href="/reels/"]');
    for (var i = 0; i < anchors.length; i++) {
      var el = anchors[i].parentElement;
      for (var depth = 0; el && el !== document.body && depth < 8; depth++, el = el.parentElement) {
        if (el.tagName === 'NAV' || isFixedOrSticky(el)) {
          if (countNavLinks(el) >= 2) {
            if (!el.hasAttribute(HIDDEN_ATTR)) el.setAttribute(HIDDEN_ATTR, 'tabbar');
          }
          break;
        }
      }
    }
  }

  function checkReady() {
    if (readySent) return;
    var found =
      document.querySelector('a[href^="/direct/t/"]') ||
      document.querySelector('[role="textbox"], textarea') ||
      document.querySelector('input[name="username"], input[type="password"]') ||
      document.querySelector('main, [role="main"]');
    if (found) sendReady();
  }

  function sendReady() {
    if (readySent) return;
    readySent = true;
    post({ type: 'ready', path: location.pathname });
  }

  function scan() {
    try {
      applyStyle();
      hideInstagramTabBar();
      checkReady();
    } catch (e) {
      reportError('scan', e);
    }
  }

  function observe() {
    if (typeof MutationObserver !== 'function') return;
    var observer = new MutationObserver(scheduleScan);
    var start = function () {
      observer.observe(document.documentElement, { childList: true, subtree: true });
    };
    if (document.documentElement) start();
  }

  // ---------------------------------------------------------------- reel swipe guard

  /** On a single shared reel, vertical swipes would move on to the next reel. Stop them. */
  function isSingleReel() {
    return /^\/reels?\/[^/]+\/?$/.test(location.pathname);
  }

  function inScrollableDialog(target) {
    return !!(target && target.closest && target.closest('[role="dialog"]'));
  }

  /**
   * Vertical gestures on a shared reel are swallowed from their first movement, before the browser
   * starts scrolling (after which a scroll can no longer be cancelled) and before Instagram's own
   * swipe handlers see them. Horizontal gestures, taps and the comment sheet are untouched.
   */
  function installReelGuard() {
    var startX = 0;
    var startY = 0;
    var swallow = function (e, x, y) {
      if (!isSingleReel() || inScrollableDialog(e.target)) return;
      if (Math.abs(y - startY) >= Math.abs(x - startX)) {
        if (e.cancelable) e.preventDefault();
        e.stopImmediatePropagation();
      }
    };
    window.addEventListener('touchstart', guard('touchstart', function (e) {
      if (!e.touches || !e.touches.length) return;
      startX = e.touches[0].clientX;
      startY = e.touches[0].clientY;
    }), { capture: true, passive: true });
    window.addEventListener('touchmove', guard('touchmove', function (e) {
      if (e.touches && e.touches.length === 1) swallow(e, e.touches[0].clientX, e.touches[0].clientY);
    }), { capture: true, passive: false });
    window.addEventListener('pointerdown', guard('pointerdown', function (e) {
      startX = e.clientX;
      startY = e.clientY;
    }), { capture: true, passive: true });
    window.addEventListener('pointermove', guard('pointermove', function (e) {
      if (e.pointerType !== 'mouse' && e.buttons) swallow(e, e.clientX, e.clientY);
    }), { capture: true, passive: false });
    window.addEventListener('wheel', guard('wheel', function (e) {
      if (isSingleReel() && !inScrollableDialog(e.target)) {
        if (e.cancelable) e.preventDefault();
        e.stopImmediatePropagation();
      }
    }), { capture: true, passive: false });
  }

  // ---------------------------------------------------------------- send haptic

  /** A message was sent when the composer had text and is empty right after Enter or a click. */
  function composerText() {
    var box = document.querySelector('[role="textbox"][contenteditable="true"], textarea');
    if (!box) return '';
    return (box.value !== undefined && box.tagName === 'TEXTAREA' ? box.value : box.textContent) || '';
  }

  function installSendHaptic() {
    var check = function () {
      if (!isThreadPath(location.pathname)) return;
      var before = composerText().trim();
      if (!before) return;
      setTimeout(guard('haptic', function () {
        if (!composerText().trim()) post({ type: 'haptic' });
      }), 250);
    };
    document.addEventListener('keydown', guard('keydown', function (e) {
      if (e.key === 'Enter' && !e.shiftKey) check();
    }), true);
    document.addEventListener('click', guard('click', check), true);
  }

  // ---------------------------------------------------------------- unread badge

  function cookie(name) {
    var parts = ('; ' + document.cookie).split('; ' + name + '=');
    return parts.length < 2 ? null : parts.pop().split(';').shift();
  }

  function pollBadge() {
    if (document.visibilityState === 'hidden' || !cookie('ds_user_id') || typeof fetch !== 'function') return;
    fetch(BADGE_URL, {
      credentials: 'include',
      headers: { 'X-IG-App-ID': IG_APP_ID, 'X-CSRFToken': cookie('csrftoken') || '', 'X-Requested-With': 'XMLHttpRequest' }
    }).then(function (response) {
      return response.ok ? response.json() : null;
    }).then(function (body) {
      if (body && typeof body.badge_count === 'number') post({ type: 'badge', count: body.badge_count });
    }).catch(function () { /* offline or rate limited; try again next round */ });
  }

  // ---------------------------------------------------------------- messages from the app

  function clickLink(path) {
    var link = document.querySelector('a[href="' + path.replace(/"/g, '') + '"]');
    if (link) { link.click(); return true; }
    return false;
  }

  function navigateTo(path) {
    if (typeof path !== 'string' || path.charAt(0) !== '/' || isBlocked(path)) return;
    if (location.pathname === path) return;
    if (!clickLink(path)) go(path);
  }

  function scrollToTop() {
    window.scrollTo(0, 0);
    var all = document.querySelectorAll('div');
    for (var i = 0; i < all.length; i++) {
      var el = all[i];
      if (el.scrollTop > 0 && el.scrollHeight > el.clientHeight) el.scrollTop = 0;
    }
  }

  function onAppMessage(event) {
    var data = event && event.data;
    var message = typeof data === 'string' ? JSON.parse(data) : data;
    if (!message || typeof message.type !== 'string') return;
    switch (message.type) {
      case 'navigate':
        navigateTo(message.path);
        break;
      case 'rules':
        setRules(message.rules);
        applyStyle();
        enforce();
        break;
      case 'scrollTop':
        scrollToTop();
        break;
      case 'badge':
        pollBadge();
        break;
    }
  }

  function listen() {
    var bridge = window.dms;
    if (!bridge) return;
    var handler = guard('message', onAppMessage);
    if (typeof bridge.addEventListener === 'function') bridge.addEventListener('message', handler);
    else bridge.onmessage = handler;
  }

  // ---------------------------------------------------------------- boot

  function boot() {
    setRules(window.__DMS_RULES__ || DEFAULT_RULES);
    window.__dmsCage = { isBlocked: isBlocked, enforce: enforce, rules: function () { return rules; } };

    // A blocked page is left before Instagram renders anything.
    if (enforce()) return;

    post({ type: 'hello', path: location.pathname });
    listen();
    applyStyle();
    wrapHistory();
    onRoute();
    observe();
    installReelGuard();
    installSendHaptic();

    document.addEventListener('DOMContentLoaded', guard('domcontentloaded', function () {
      onRoute();
      scan();
      setTimeout(sendReady, READY_TIMEOUT_MS);
    }));
    window.addEventListener('load', guard('load', scan));
    document.addEventListener('visibilitychange', guard('visibility', function () {
      if (document.visibilityState === 'visible') pollBadge();
    }));

    setInterval(guard('interval', function () {
      onRoute();
      scan();
    }), ENFORCE_INTERVAL_MS);
    setInterval(guard('badge', pollBadge), BADGE_INTERVAL_MS);
    setTimeout(guard('badge', pollBadge), 2000);
  }

  try {
    boot();
  } catch (e) {
    reportError('boot', e);
  }
})();
