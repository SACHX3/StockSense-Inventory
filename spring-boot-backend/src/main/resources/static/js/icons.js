/* ============================================================================
   StockSense icon shim
   Replaces legacy Bootstrap-Icons markup (<i class="bi bi-xyz">) with inline,
   stroke-based SVGs (24x24 viewBox, 2px stroke - Lucide/Heroicons style) so
   no icon font is loaded. Runs once on DOMContentLoaded and again whenever
   dynamic content is inserted (dashboard widgets, notif dropdown, POS cart).
   Call window.stocksenseRenderIcons() after injecting new HTML containing
   "bi bi-*" classes.
   ========================================================================= */
(function () {
  'use strict';

  var P = 'stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" fill="none"';

  function svg(inner) {
    return '<svg viewBox="0 0 24 24" ' + P + '>' + inner + '</svg>';
  }

  var ICONS = {
    'arrow-clockwise': svg('<path d="M21 12a9 9 0 1 1-3-6.7"/><path d="M21 4v5h-5"/>'),
    'arrow-left': svg('<path d="M19 12H5"/><path d="M11 18l-6-6 6-6"/>'),
    'arrow-left-right': svg('<path d="M8 3 4 7l4 4"/><path d="M4 7h16"/><path d="M16 21l4-4-4-4"/><path d="M20 17H4"/>'),
    'arrow-right': svg('<path d="M5 12h14"/><path d="M13 6l6 6-6 6"/>'),
    'bar-chart': svg('<path d="M4 20V10"/><path d="M12 20V4"/><path d="M20 20v-7"/>'),
    'bar-chart-line': svg('<path d="M4 20V10"/><path d="M12 20V4"/><path d="M20 20v-7"/>'),
    'bell': svg('<path d="M6 8a6 6 0 1 1 12 0c0 7 3 9 3 9H3s3-2 3-9"/><path d="M10.3 21a1.94 1.94 0 0 0 3.4 0"/>'),
    'bell-fill': svg('<path d="M6 8a6 6 0 1 1 12 0c0 7 3 9 3 9H3s3-2 3-9"/><path d="M10.3 21a1.94 1.94 0 0 0 3.4 0"/>'),
    'box': svg('<path d="m21 8-9-5-9 5 9 5 9-5Z"/><path d="M3 8v8l9 5 9-5V8"/><path d="M12 13v8"/>'),
    'box-arrow-in-right': svg('<path d="M10 17l5-5-5-5"/><path d="M15 12H3"/><path d="M21 3v18"/>'),
    'box-arrow-left': svg('<path d="M14 7l-5 5 5 5"/><path d="M9 12h12"/><path d="M3 3v18"/>'),
    'box-seam': svg('<path d="m21 8-9-5-9 5 9 5 9-5Z"/><path d="M3 8v8l9 5 9-5V8"/><path d="M12 13v8"/>'),
    'brightness-high': svg('<circle cx="12" cy="12" r="4"/><path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4"/>'),
    'moon-stars': svg('<path d="M20 14.5A8 8 0 1 1 9.5 4a6.5 6.5 0 0 0 10.5 10.5Z"/><path d="M17 3v3M15.5 4.5h3"/>'),
    'boxes': svg('<path d="m21 8-9-5-9 5 9 5 9-5Z"/><path d="M3 8v8l9 5 9-5V8"/><path d="M12 13v8"/>'),
    'calendar-month': svg('<rect x="3" y="4" width="18" height="18" rx="2"/><path d="M16 2v4"/><path d="M8 2v4"/><path d="M3 10h18"/>'),
    'calendar3': svg('<rect x="3" y="4" width="18" height="18" rx="2"/><path d="M16 2v4"/><path d="M8 2v4"/><path d="M3 10h18"/>'),
    'camera': svg('<path d="M14.5 4h-5L7 7H4a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V9a2 2 0 0 0-2-2h-3l-2.5-3Z"/><circle cx="12" cy="13" r="3"/>'),
    'cart': svg('<circle cx="9" cy="21" r="1"/><circle cx="20" cy="21" r="1"/><path d="M1 1h4l2.7 13.4a2 2 0 0 0 2 1.6h9.7a2 2 0 0 0 2-1.6L23 6H6"/>'),
    'cart-plus': svg('<circle cx="9" cy="21" r="1"/><circle cx="20" cy="21" r="1"/><path d="M1 1h4l2.7 13.4a2 2 0 0 0 2 1.6h9.7a2 2 0 0 0 2-1.6L23 6H6"/><path d="M15 8h4M17 6v4"/>'),
    'cart-x': svg('<circle cx="9" cy="21" r="1"/><circle cx="20" cy="21" r="1"/><path d="M1 1h4l2.7 13.4a2 2 0 0 0 2 1.6h9.7a2 2 0 0 0 2-1.6L23 6H6"/><path d="M15 6l4 4M19 6l-4 4"/>'),
    'check-all': svg('<path d="M2 12l4 4L18 4"/><path d="M8 12l4 4L22 4"/>'),
    'check-circle': svg('<circle cx="12" cy="12" r="9"/><path d="m8.5 12.5 2.5 2.5 5-5"/>'),
    'check-circle-fill': svg('<circle cx="12" cy="12" r="9"/><path d="m8.5 12.5 2.5 2.5 5-5"/>'),
    'check-lg': svg('<path d="M4 12.5l5.5 5.5L20 6.5"/>'),
    'chevron-down': svg('<path d="m6 9 6 6 6-6"/>'),
    'chevron-up': svg('<path d="m18 15-6-6-6 6"/>'),
    'clipboard-data': svg('<rect x="6" y="3" width="12" height="4" rx="1"/><path d="M8 5H6a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7a2 2 0 0 0-2-2h-2"/><path d="M9 14v3M12 12v5M15 15v2"/>'),
    'clock': svg('<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 3"/>'),
    'clock-history': svg('<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 3"/>'),
    'cloud-slash': svg('<path d="M2 2l20 20"/><path d="M18.6 16H19a4 4 0 0 0 .3-8 5.5 5.5 0 0 0-10-2"/><path d="M7 8a5 5 0 0 0-1 9.9"/>'),
    'cpu': svg('<rect x="6" y="6" width="12" height="12" rx="1"/><path d="M9 2v3M15 2v3M9 19v3M15 19v3M2 9h3M2 15h3M19 9h3M19 15h3"/>'),
    'currency-exchange': svg('<circle cx="12" cy="12" r="9"/><path d="M9 9h3.5a1.8 1.8 0 1 1 0 3.5H9M9 12.5h3.5A1.8 1.8 0 1 1 12.5 16H9"/><path d="M11 7v10"/>'),
    'database': svg('<ellipse cx="12" cy="5" rx="8" ry="3"/><path d="M4 5v14c0 1.7 3.6 3 8 3s8-1.3 8-3V5"/><path d="M4 12c0 1.7 3.6 3 8 3s8-1.3 8-3"/>'),
    'database-add': svg('<ellipse cx="12" cy="5" rx="8" ry="3"/><path d="M4 5v14c0 1.7 3.6 3 8 3s8-1.3 8-3V5"/><path d="M4 12c0 1.7 3.6 3 8 3s8-1.3 8-3"/>'),
    'exclamation-circle': svg('<circle cx="12" cy="12" r="9"/><path d="M12 8v5"/><path d="M12 16h.01"/>'),
    'exclamation-triangle': svg('<path d="M10.3 3.9 1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0Z"/><path d="M12 9v4"/><path d="M12 17h.01"/>'),
    'exclamation-triangle-fill': svg('<path d="M10.3 3.9 1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0Z"/><path d="M12 9v4"/><path d="M12 17h.01"/>'),
    'eye': svg('<path d="M1 12s4-7 11-7 11 7 11 7-4 7-11 7-11-7-11-7Z"/><circle cx="12" cy="12" r="3"/>'),
    'eye-slash': svg('<path d="M17.9 17.9A10.9 10.9 0 0 1 12 19c-7 0-11-7-11-7a20.3 20.3 0 0 1 5-5.6M9.9 4.2A9.5 9.5 0 0 1 12 4c7 0 11 7 11 7a20.3 20.3 0 0 1-2.6 3.6M14.1 14.1a3 3 0 1 1-4.2-4.2"/><path d="M1 1l22 22"/>'),
    'file-earmark-bar-graph': svg('<path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8l-6-6Z"/><path d="M14 2v6h6"/><path d="M9 17v-2M12 17v-4M15 17v-6"/>'),
    'file-earmark-image-fill': svg('<path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8l-6-6Z"/><circle cx="10" cy="13" r="1.5"/><path d="m8 18 3-3 2 2 3-4 2 5"/>'),
    'file-earmark-pdf-fill': svg('<path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8l-6-6Z"/><path d="M14 2v6h6"/><path d="M8 18h1.5a1.5 1.5 0 0 0 0-3H8v4M12.5 15v4M12.5 17H14M17 15v4M17 17h1.5"/>'),
    'file-earmark-text': svg('<path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8l-6-6Z"/><path d="M14 2v6h6"/><path d="M9 13h6M9 17h6"/>'),
    'file-pdf': svg('<path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8l-6-6Z"/><path d="M14 2v6h6"/>'),
    'file-text': svg('<path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8l-6-6Z"/><path d="M14 2v6h6"/><path d="M9 13h6M9 17h6"/>'),
    'files': svg('<path d="M15 2H8a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8l-5-6Z"/><path d="M15 2v6h5"/><path d="M4 8v12a2 2 0 0 0 2 2h9"/>'),
    'funnel': svg('<path d="M22 3H2l8 9.5V19l4 2v-8.5L22 3Z"/>'),
    'graph-up': svg('<path d="M3 3v18h18"/><path d="m19 9-5 5-4-4-4 4"/>'),
    'graph-up-arrow': svg('<path d="M3 3v18h18"/><path d="m19 9-5 5-4-4-4 4"/>'),
    'heart-pulse': svg('<path d="M19 14c1.5-1.5 3-3.5 3-6a5 5 0 0 0-9-3 5 5 0 0 0-9 3c0 5 6 8.5 9 12.5 1.1-1.5 2.4-2.8 3.6-4"/><path d="M4 12h3l2 5 3-9 2 4h4"/>'),
    'hourglass-split': svg('<path d="M6 2h12M6 22h12"/><path d="M6 2c0 6 6 6 6 10s-6 4-6 10M18 2c0 6-6 6-6 10s6 4 6 10"/>'),
    'image': svg('<rect x="3" y="3" width="18" height="18" rx="2"/><circle cx="9" cy="9" r="2"/><path d="m21 15-5-5L5 21"/>'),
    'inbox': svg('<path d="M22 12h-6l-2 3h-4l-2-3H2"/><path d="M5.5 5h13l3.5 7v7a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2v-7l3.5-7Z"/>'),
    'info-circle': svg('<circle cx="12" cy="12" r="9"/><path d="M12 16v-5"/><path d="M12 8h.01"/>'),
    'layers': svg('<path d="m12 2 9 5-9 5-9-5 9-5Z"/><path d="m3 12 9 5 9-5"/><path d="m3 17 9 5 9-5"/>'),
    'lightbulb': svg('<path d="M9 18h6"/><path d="M10 22h4"/><path d="M12 2a7 7 0 0 0-4 12.7c.6.5 1 1.3 1 2.3h6c0-1 .4-1.8 1-2.3A7 7 0 0 0 12 2Z"/>'),
    'lightning': svg('<path d="M13 2 3 14h8l-1 8 10-12h-8l1-8Z"/>'),
    'link': svg('<path d="M9 17H7a5 5 0 1 1 0-10h2"/><path d="M15 7h2a5 5 0 1 1 0 10h-2"/><path d="M8 12h8"/>'),
    'list': svg('<path d="M8 6h13M8 12h13M8 18h13"/><path d="M3 6h.01M3 12h.01M3 18h.01"/>'),
    'lock': svg('<rect x="3" y="11" width="18" height="10" rx="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/>'),
    'magic': svg('<path d="m15 4 1.5 3 3 1.5-3 1.5L15 13l-1.5-3-3-1.5 3-1.5L15 4Z"/><path d="M4 20l9-9"/>'),
    'pause-circle': svg('<circle cx="12" cy="12" r="9"/><path d="M10 9v6M14 9v6"/>'),
    'pencil': svg('<path d="M17 3a2.85 2.85 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5L17 3Z"/>'),
    'people': svg('<circle cx="9" cy="8" r="3.5"/><path d="M2 21c0-4 3-6 7-6s7 2 7 6"/><circle cx="17.5" cy="9.5" r="2.5"/><path d="M17 12.5c2.5.3 4 2 4 4.5"/>'),
    'person': svg('<circle cx="12" cy="8" r="4"/><path d="M4 21c0-4.4 3.6-7 8-7s8 2.6 8 7"/>'),
    'play-circle': svg('<circle cx="12" cy="12" r="9"/><path d="m10 8 6 4-6 4V8Z"/>'),
    'plus-circle': svg('<circle cx="12" cy="12" r="9"/><path d="M12 8v8M8 12h8"/>'),
    'plus-lg': svg('<path d="M12 5v14M5 12h14"/>'),
    'printer': svg('<path d="M6 9V3h12v6"/><rect x="4" y="9" width="16" height="8" rx="1"/><path d="M6 17v4h12v-4"/>'),
    'receipt': svg('<path d="M6 2h12v20l-3-2-3 2-3-2-3 2V2Z"/><path d="M9 8h6M9 12h6"/>'),
    'robot': svg('<rect x="4" y="9" width="16" height="11" rx="2"/><path d="M12 9V5"/><circle cx="12" cy="3" r="1.5"/><path d="M8 14h.01M16 14h.01"/><path d="M2 13v3M22 13v3"/>'),
    'save': svg('<path d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2Z"/><path d="M17 21v-8H7v8"/><path d="M7 3v5h8"/>'),
    'search': svg('<circle cx="11" cy="11" r="7"/><path d="m21 21-4.35-4.35"/>'),
    'shield-check': svg('<path d="M12 2 4 6v6c0 5 3.5 8.7 8 10 4.5-1.3 8-5 8-10V6l-8-4Z"/><path d="m9 12 2 2 4-4"/>'),
    'shield-lock-fill': svg('<path d="M12 2 4 6v6c0 5 3.5 8.7 8 10 4.5-1.3 8-5 8-10V6l-8-4Z"/><rect x="9.5" y="11" width="5" height="4" rx="1"/><path d="M10.5 11V9.5a1.5 1.5 0 0 1 3 0V11"/>'),
    'shield-x': svg('<path d="M12 2 4 6v6c0 5 3.5 8.7 8 10 4.5-1.3 8-5 8-10V6l-8-4Z"/><path d="m9.5 9.5 5 5M14.5 9.5l-5 5"/>'),
    'speedometer2': svg('<circle cx="12" cy="13" r="8"/><path d="M12 13 15.5 9M12 5v1.5M4.5 13H3M21 13h-1.5M6.3 6.3 7.3 7.3M17.7 6.3l-1 1"/>'),
    'table': svg('<rect x="3" y="4" width="18" height="16" rx="1"/><path d="M3 9h18M3 15h18M9 4v16"/>'),
    'tags': svg('<path d="M12 2h6a2 2 0 0 1 2 2v6l-9 9-8-8 9-9Z"/><circle cx="15.5" cy="6.5" r="1.2"/>'),
    'trash': svg('<path d="M4 7h16"/><path d="M6 7V4a1 1 0 0 1 1-1h10a1 1 0 0 1 1 1v3"/><path d="M6 7l1 13a2 2 0 0 0 2 2h6a2 2 0 0 0 2-2l1-13"/><path d="M10 11v6M14 11v6"/>'),
    'trophy': svg('<path d="M8 21h8M12 17v4"/><path d="M7 4h10v6a5 5 0 0 1-10 0V4Z"/><path d="M7 5H4a3 3 0 0 0 3 5M17 5h3a3 3 0 0 1-3 5"/>'),
    'truck': svg('<rect x="1" y="6" width="14" height="11" rx="1"/><path d="M15 10h4l3 3v4h-7v-7Z"/><circle cx="6" cy="19" r="1.6"/><circle cx="17.5" cy="19" r="1.6"/>'),
    'upload': svg('<path d="M12 16V4"/><path d="m6 10 6-6 6 6"/><path d="M4 18v1a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-1"/>'),
    'x': svg('<path d="M18 6 6 18"/><path d="M6 6l12 12"/>'),
    'x-circle': svg('<circle cx="12" cy="12" r="9"/><path d="m9 9 6 6M15 9l-6 6"/>'),
    'x-lg': svg('<path d="M18 6 6 18"/><path d="M6 6l12 12"/>')
  };
  var FALLBACK = svg('<circle cx="12" cy="12" r="1.5"/><circle cx="19" cy="12" r="1.5"/><circle cx="5" cy="12" r="1.5"/>');

  function renderIcons(root) {
    root = root || document;
    var nodes = root.querySelectorAll('i.bi, i[class*="bi-"]');
    nodes.forEach(function (el) {
      var cls = el.className || '';
      var m = cls.match(/bi-([a-z0-9-]+)/);
      if (!m) return;
      var name = m[1];
      var html = ICONS[name] || FALLBACK;
      var span = document.createElement('span');
      span.innerHTML = html;
      var s = span.firstElementChild;
      if (!s) return;
      // Preserve sizing/spacing utility classes (me-1, ms-1, ms-2, fs-*, ...) on a wrapper
      var extra = cls.replace(/\bbi\b/g, '').replace(/bi-[a-z0-9-]+/g, '').trim();
      if (extra) s.setAttribute('class', extra);
      if (el.id) s.id = el.id;
      s.setAttribute('data-bi', name);
      s.style.display = 'inline-block';
      s.style.verticalAlign = '-3px';
      s.style.width = '1em';
      s.style.height = '1em';
      var inlineStyle = el.getAttribute('style');
      if (inlineStyle) s.setAttribute('style', s.getAttribute('style') + ';' + inlineStyle);
      el.replaceWith(s);
    });
  }

  // Swap the icon shown by an element that may already have been shimmed to
  // an inline <svg> (has data-bi) or may still be the original <i class="bi ...">.
  window.setIcon = function (el, name) {
    if (!el) return;
    var html = ICONS[name] || FALLBACK;
    var wrap = document.createElement('span');
    wrap.innerHTML = html;
    var s = wrap.firstElementChild;
    if (!s) return;
    if (el.id) s.id = el.id;
    s.setAttribute('data-bi', name);
    s.style.display = 'inline-block';
    s.style.verticalAlign = '-3px';
    s.style.width = '1em';
    s.style.height = '1em';
    var cls = el.getAttribute('class') || '';
    var extra = cls.replace(/\bbi\b/g, '').replace(/bi-[a-z0-9-]+/g, '').trim();
    if (extra) s.setAttribute('class', extra);
    el.replaceWith(s);
    return s;
  };

  window.stocksenseRenderIcons = renderIcons;

  document.addEventListener('DOMContentLoaded', function () { renderIcons(document); });

  // Re-render for dynamically injected content (POS search results, notif list, etc.)
  var mo = new MutationObserver(function (mutations) {
    var needsRun = false;
    mutations.forEach(function (m) {
      m.addedNodes.forEach(function (n) {
        if (n.nodeType === 1 && (n.matches && (n.matches('i.bi') || n.querySelector && n.querySelector('i.bi, i[class*="bi-"]')))) {
          needsRun = true;
        }
      });
    });
    if (needsRun) renderIcons(document);
  });
  document.addEventListener('DOMContentLoaded', function () {
    mo.observe(document.body, { childList: true, subtree: true });
  });
})();
