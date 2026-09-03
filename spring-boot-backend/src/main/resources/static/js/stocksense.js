/* ============================================================================
   StockSense shared JS
   - Theme toggle + persistence (localStorage key: stocksense-theme)
   - cssVar() / withAlpha() helpers for Chart.js runtime theming
   - window.stocksenseCharts registry + rebuild-on-theme-change
   - Notification dropdown (open/close, outside click, Escape)
   - User menu dropdown (open/close, outside click, Escape)
   - Mobile sidebar toggle
   - Bootstrap-icon -> inline SVG shim (keeps existing <i class="bi bi-*">
     markup working across untouched templates while satisfying the
     "inline SVG only, no icon fonts" requirement)
   ========================================================================= */
(function () {
  'use strict';

  // ── CSRF for fetch() ──────────────────────────────────────────────────
  // Spring Security rejects any POST/PUT/PATCH/DELETE without a CSRF token.
  // Forms get theirs from Thymeleaf; fetch() calls get it here, once, by
  // wrapping window.fetch - so the ten call sites scattered across the POS,
  // OCR and forecasting pages did not have to be touched individually.
  //
  // Same-origin only: the token must never be attached to a cross-origin
  // request, which would leak it to whatever host was called.
  (function () {
    function meta(name) {
      var el = document.querySelector('meta[name="' + name + '"]');
      return el ? el.getAttribute('content') : '';
    }
    var token = meta('_csrf');
    var header = meta('_csrf_header') || 'X-CSRF-TOKEN';
    if (!token || !window.fetch) return;

    var nativeFetch = window.fetch.bind(window);
    var SAFE = /^(GET|HEAD|OPTIONS|TRACE)$/i;

    window.fetch = function (input, init) {
      init = init || {};
      var method = init.method || (typeof input === 'object' && input.method) || 'GET';
      if (SAFE.test(method)) return nativeFetch(input, init);

      var url = typeof input === 'string' ? input : (input && input.url) || '';
      var sameOrigin = !/^https?:\/\//i.test(url) || url.indexOf(window.location.origin) === 0;
      if (!sameOrigin) return nativeFetch(input, init);

      var headers = new Headers(init.headers || (typeof input === 'object' ? input.headers : undefined));
      if (!headers.has(header)) headers.set(header, token);
      init.headers = headers;
      if (!init.credentials) init.credentials = 'same-origin';
      return nativeFetch(input, init);
    };
  })();

  // ── Theme ────────────────────────────────────────────────────────────
  var THEME_KEY = 'stocksense-theme';

  function getStoredTheme() {
    try { return localStorage.getItem(THEME_KEY); } catch (e) { return null; }
  }
  function storeTheme(t) {
    try { localStorage.setItem(THEME_KEY, t); } catch (e) { /* ignore */ }
  }
  function applyTheme(theme) {
    document.documentElement.setAttribute('data-theme', theme);
    updateThemeToggleUI(theme);
  }
  function updateThemeToggleUI(theme) {
    document.querySelectorAll('.theme-toggle button').forEach(function (btn) {
      var isActive = btn.getAttribute('data-theme-choice') === theme;
      btn.classList.toggle('active', isActive);
    });
  }
  window.setStocksenseTheme = function (theme) {
    applyTheme(theme);
    storeTheme(theme);
    rebuildAllCharts();
  };

  var initialTheme = getStoredTheme() || 'light';
  applyTheme(initialTheme);

  // ── cssVar / withAlpha helpers (for Chart.js runtime theming) ─────────
  window.cssVar = function (name) {
    return getComputedStyle(document.documentElement).getPropertyValue(name).trim();
  };
  window.withAlpha = function (color, alpha) {
    color = (color || '').trim();
    if (!color) return 'rgba(0,0,0,' + alpha + ')';
    if (color[0] === '#') {
      var hex = color.slice(1);
      if (hex.length === 3) hex = hex.split('').map(function (c) { return c + c; }).join('');
      var r = parseInt(hex.substring(0, 2), 16);
      var g = parseInt(hex.substring(2, 4), 16);
      var b = parseInt(hex.substring(4, 6), 16);
      return 'rgba(' + r + ',' + g + ',' + b + ',' + alpha + ')';
    }
    if (color.indexOf('rgba') === 0) {
      return color.replace(/[\d.]+\)$/, alpha + ')');
    }
    if (color.indexOf('rgb(') === 0) {
      return color.replace('rgb(', 'rgba(').replace(')', ',' + alpha + ')');
    }
    return color;
  };

  // ── Chart registry ──────────────────────────────────────────────────
  window.stocksenseCharts = window.stocksenseCharts || {};
  window.stocksenseChartBuilders = window.stocksenseChartBuilders || [];

  window.registerChartBuilder = function (fn) {
    window.stocksenseChartBuilders.push(fn);
    fn(); // build once immediately
  };

  function rebuildAllCharts() {
    Object.keys(window.stocksenseCharts).forEach(function (key) {
      var chart = window.stocksenseCharts[key];
      if (chart && typeof chart.destroy === 'function') chart.destroy();
      delete window.stocksenseCharts[key];
    });
    window.stocksenseChartBuilders.forEach(function (fn) {
      try { fn(); } catch (e) { console.error('[StockSense] chart rebuild failed', e); }
    });
  }

  if (window.Chart) {
    try {
      Chart.defaults.font.family = "'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif";
      Chart.defaults.color = window.cssVar('--text-2');
    } catch (e) { /* Chart may not be loaded yet on some pages */ }
  }

  // ── Notification dropdown ───────────────────────────────────────────
  function closeAllPanels(except) {
    document.querySelectorAll('.notif-panel.open, .user-dropdown.open, .ai-status-panel.open').forEach(function (el) {
      if (el !== except) el.classList.remove('open');
    });
  }

  window.toggleNotifPanel = function (e) {
    if (e) { e.stopPropagation(); e.preventDefault(); }
    var panel = document.getElementById('notifPanel');
    if (!panel) return;
    var willOpen = !panel.classList.contains('open');
    closeAllPanels(willOpen ? panel : null);
    panel.classList.toggle('open', willOpen);
    if (willOpen && window.loadNotifItems) window.loadNotifItems();
  };

  window.toggleUserMenu = function (e) {
    if (e) { e.stopPropagation(); e.preventDefault(); }
    var panel = document.getElementById('userDropdown');
    if (!panel) return;
    var willOpen = !panel.classList.contains('open');
    closeAllPanels(willOpen ? panel : null);
    panel.classList.toggle('open', willOpen);
  };

  document.addEventListener('click', function (e) {
    document.querySelectorAll('.notif-panel.open, .user-dropdown.open, .ai-status-panel.open').forEach(function (panel) {
      var trigger = panel.previousElementSibling;
      if (!panel.contains(e.target) && (!trigger || !trigger.contains(e.target))) {
        panel.classList.remove('open');
        if (panel.id === 'aiStatusPanel' && window.stopAiPollingGlobal) window.stopAiPollingGlobal();
      }
    });
  });

  // ── Global AI service status check + start/stop controls (topbar) ───
  var aiPollTimer = null;

  function stopAiPolling() {
    if (aiPollTimer) { clearInterval(aiPollTimer); aiPollTimer = null; }
  }
  window.stopAiPollingGlobal = stopAiPolling;


  function setAiButtonState(online) {
    var btn = document.getElementById('aiStatusBtn');
    if (!btn) return;
    btn.classList.remove('checking', 'result-ok', 'result-warn');
    btn.classList.add(online ? 'result-ok' : 'result-warn');
    btn.title = online ? 'AI service is online' : 'AI service is offline';
  }

  function renderAiResult(online, message, opts) {
    opts = opts || {};
    var panel = document.getElementById('aiStatusPanel');
    setAiButtonState(online);
    if (!panel) return;
    var actionBtn = online
      ? '<button type="button" class="ai-action-btn stop" onclick="stopAIServiceGlobal(event)"' + (opts.stopDisabled ? ' disabled' : '') + '><i class="bi bi-pause-circle"></i>Stop service</button>'
      : '<button type="button" class="ai-action-btn start" onclick="startAIServiceGlobal(event)"><i class="bi bi-play-circle"></i>Start service</button>';
    panel.innerHTML =
      '<div class="result-card">' +
        '<span class="result-dot-wrap"><span class="result-dot ' + (online ? 'ok' : 'warn') + '"><i class="bi ' + (online ? 'bi-check-lg' : 'bi-exclamation-circle') + '"></i></span></span>' +
        '<span class="result-text">' +
          '<div class="result-title ' + (online ? 'ok' : 'warn') + '">AI service is ' + (online ? 'online' : 'offline') + '</div>' +
          '<div class="result-sub">' + message + '</div>' +
        '</span>' +
      '</div>' +
      '<div class="ai-action-row">' + actionBtn + '</div>';
    if (window.stocksenseRenderIcons) window.stocksenseRenderIcons(panel);
  }

  function renderAiLoading(text) {
    var panel = document.getElementById('aiStatusPanel');
    var btn = document.getElementById('aiStatusBtn');
    if (btn) { btn.classList.remove('result-ok', 'result-warn'); btn.classList.add('checking'); }
    if (panel) panel.innerHTML = '<div class="loading-card"><span class="loading-spinner"></span>' + text + '</div>';
  }

  function refreshAiStatus() {
    return fetch('/forecasting/api/service/status', { credentials: 'same-origin' })
      .then(function (r) { return r.json(); })
      .then(function (res) {
        var data = (res && res.data) || {};
        var online = !!data.healthy;
        setSystemBadge(online ? 'online' : 'offline');
        renderAiResult(
          online,
          online ? 'FastAPI AI/OCR service is responding normally.' : 'Using fallback forecasting. Core inventory features remain available.',
          { stopDisabled: online && !data.managedByApp }
        );
        return online;
      })
      .catch(function () {
        setSystemBadge('offline');
        renderAiResult(false, 'Couldn\'t reach the AI/OCR service. Core inventory features remain available.', {});
        return false;
      });
  }

  // ── Topbar Online/Offline badge ─────────────────────────────────────
  // Reflects the real AI/OCR service health from /forecasting/api/service/status,
  // the same endpoint the AI status panel uses. It starts in a neutral "Checking"
  // state rather than claiming "Online" before anything has been verified.
  var SYS_BADGE_POLL_MS = 30000;
  var sysBadgeTimer = null;

  function setSystemBadge(state) {
    var badge = document.getElementById('systemLiveBadge');
    var text = document.getElementById('systemLiveText');
    if (!badge || !text) return;
    badge.classList.remove('is-online', 'is-offline', 'is-checking');
    if (state === 'online') {
      badge.classList.add('is-online');
      text.textContent = 'Online';
      badge.title = 'AI/OCR service is responding normally';
    } else if (state === 'offline') {
      badge.classList.add('is-offline');
      text.textContent = 'Offline';
      badge.title = 'AI/OCR service is not responding - fallback forecasting in use';
    } else {
      badge.classList.add('is-checking');
      text.textContent = 'Checking';
      badge.title = 'Checking AI service\u2026';
    }
  }
  window.setSystemBadge = setSystemBadge;

  function pollSystemBadge() {
    return fetch('/forecasting/api/service/status', { credentials: 'same-origin' })
      .then(function (r) { return r.json(); })
      .then(function (res) {
        setSystemBadge(((res && res.data) || {}).healthy ? 'online' : 'offline');
      })
      .catch(function () { setSystemBadge('offline'); });
  }

  if (document.getElementById('systemLiveBadge')) {
    pollSystemBadge();
    sysBadgeTimer = setInterval(pollSystemBadge, SYS_BADGE_POLL_MS);
    // Don't keep polling a tab nobody is looking at; re-check on return so the
    // badge is never showing a stale status the moment the user comes back.
    document.addEventListener('visibilitychange', function () {
      if (document.hidden) {
        clearInterval(sysBadgeTimer); sysBadgeTimer = null;
      } else if (!sysBadgeTimer) {
        pollSystemBadge();
        sysBadgeTimer = setInterval(pollSystemBadge, SYS_BADGE_POLL_MS);
      }
    });
  }

  window.checkAIServiceGlobal = function (e) {
    if (e) { e.stopPropagation(); e.preventDefault(); }
    var panel = document.getElementById('aiStatusPanel');
    var btn = document.getElementById('aiStatusBtn');
    if (!panel) return;
    var willOpen = !panel.classList.contains('open');
    closeAllPanels(willOpen ? panel : null);
    panel.classList.toggle('open', willOpen);
    if (!willOpen) {
      if (btn) btn.classList.remove('checking', 'result-ok', 'result-warn');
      stopAiPolling();
      return;
    }
    renderAiLoading('Checking AI/OCR service&hellip;');
    refreshAiStatus();
  };

  window.startAIServiceGlobal = function (e) {
    if (e) { e.stopPropagation(); e.preventDefault(); }
    renderAiLoading('Starting AI service&hellip; this can take a couple of minutes on first run.');
    fetch('/forecasting/api/service/start', { method: 'POST', credentials: 'same-origin' })
      .then(function () {
        stopAiPolling();
        var attempts = 0;
        aiPollTimer = setInterval(function () {
          attempts++;
          refreshAiStatus().then(function (online) {
            if (online || attempts >= 40) stopAiPolling(); // ~2 min at 3s intervals
          });
        }, 3000);
      })
      .catch(function () {
        renderAiResult(false, 'Could not start the AI service. Check the server logs.', {});
      });
  };

  window.stopAIServiceGlobal = function (e) {
    if (e) { e.stopPropagation(); e.preventDefault(); }
    renderAiLoading('Stopping AI service&hellip;');
    stopAiPolling();
    fetch('/forecasting/api/service/stop', { method: 'POST', credentials: 'same-origin' })
      .then(function () { return refreshAiStatus(); })
      .catch(function () {
        renderAiResult(false, 'Could not confirm the service stopped. Check the server logs.', {});
      });
  };
  document.addEventListener('keydown', function (e) {
    if (e.key === 'Escape') closeAllPanels();
  });

  // ── Mobile sidebar toggle ───────────────────────────────────────────
  window.toggleSidebar = function () {
    var sidebar = document.getElementById('sidebar');
    var overlay = document.getElementById('sidebarOverlay');
    if (sidebar) sidebar.classList.toggle('open');
    if (overlay) overlay.classList.toggle('open');
  };

  document.addEventListener('DOMContentLoaded', function () {
    var overlay = document.getElementById('sidebarOverlay');
    if (overlay) {
      overlay.addEventListener('click', function () {
        document.getElementById('sidebar').classList.remove('open');
        overlay.classList.remove('open');
      });
    }
    document.querySelectorAll('.nav-item').forEach(function (link) {
      link.addEventListener('click', function () {
        if (window.innerWidth <= 900) {
          var sb = document.getElementById('sidebar');
          var ov = document.getElementById('sidebarOverlay');
          if (sb) sb.classList.remove('open');
          if (ov) ov.classList.remove('open');
        }
      });
    });

    document.querySelectorAll('.theme-toggle button').forEach(function (btn) {
      btn.addEventListener('click', function () {
        window.setStocksenseTheme(btn.getAttribute('data-theme-choice'));
      });
    });

    updateThemeToggleUI(document.documentElement.getAttribute('data-theme') || 'light');
  });


  // ── Minimal Bootstrap-Modal-compatible shim ─────────────────────────
  // Supports the handful of Bootstrap 5 modal/alert-dismiss data-attributes
  // still used by legacy page markup (e.g. products/categories.html),
  // without pulling in the full Bootstrap CSS/JS bundle.
  function showModalEl(el) {
    if (!el) return;
    el.classList.add('ss-modal-open');
    el.style.display = 'block';
    document.body.style.overflow = 'hidden';
    var backdrop = document.createElement('div');
    backdrop.className = 'ss-modal-backdrop';
    backdrop.setAttribute('data-ss-backdrop-for', el.id || '');
    document.body.appendChild(backdrop);
    backdrop.addEventListener('click', function () { hideModalEl(el); });
  }
  function hideModalEl(el) {
    if (!el) return;
    el.classList.remove('ss-modal-open');
    el.style.display = 'none';
    document.body.style.overflow = '';
    document.querySelectorAll('.ss-modal-backdrop').forEach(function (b) { b.remove(); });
  }
  window.bootstrap = window.bootstrap || {};
  window.bootstrap.Modal = function (el) {
    this.el = el;
    this.show = function () { showModalEl(this.el); };
    this.hide = function () { hideModalEl(this.el); };
  };
  window.bootstrap.Modal.getOrCreateInstance = function (el) { return new window.bootstrap.Modal(el); };

  document.addEventListener('click', function (e) {
    var toggle = e.target.closest('[data-bs-toggle="modal"]');
    if (toggle) {
      var sel = toggle.getAttribute('data-bs-target');
      if (sel) showModalEl(document.querySelector(sel));
    }
    var dismiss = e.target.closest('[data-bs-dismiss="modal"]');
    if (dismiss) {
      var modalEl = dismiss.closest('.modal');
      if (modalEl) hideModalEl(modalEl);
    }
    var alertDismiss = e.target.closest('[data-bs-dismiss="alert"]');
    if (alertDismiss) {
      var alertEl = alertDismiss.closest('.alert');
      if (alertEl) alertEl.remove();
    }
  });
  document.addEventListener('keydown', function (e) {
    if (e.key === 'Escape') {
      document.querySelectorAll('.modal.ss-modal-open').forEach(function (m) { hideModalEl(m); });
    }
  });

  if (!document.getElementById('ss-modal-css')) {
    var style = document.createElement('style');
    style.id = 'ss-modal-css';
    style.textContent =
      '.modal{position:fixed;inset:0;z-index:1000;display:none;overflow-y:auto;padding:24px;}' +
      '.modal.ss-modal-open{display:flex;align-items:flex-start;justify-content:center;}' +
      '.modal .modal-dialog{width:100%;max-width:520px;margin:24px auto;}' +
      '.modal .modal-content{padding:0;}' +
      '.modal .modal-header,.modal .modal-body,.modal .modal-footer{padding:16px 20px;}' +
      '.modal .modal-header{display:flex;align-items:center;justify-content:space-between;border-bottom:1px solid var(--border);}' +
      '.modal .modal-footer{display:flex;justify-content:flex-end;gap:8px;border-top:1px solid var(--border);}' +
      '.ss-modal-backdrop{position:fixed;inset:0;background:rgba(10,10,18,.5);z-index:999;}';
    document.head.appendChild(style);
  }

})();
