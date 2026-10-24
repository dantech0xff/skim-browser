// Scrolls to a cited block and flashes it. Called with one argument, the JSON object
// {id, cited, url}: the block's data-skim-i number, the start of the cited text (a fallback when the
// page renumbered its blocks) and the URL the citation was read from. Does nothing unless the page
// is still at that URL, fragment aside. Evaluates to true when it found the block.
function (args) {
  'use strict';

  var ID = 'data-skim-i';
  var ID_SELECTOR = '[data-skim-i]';
  var MAX_NEEDLE = 120;
  var CONTROLS = /[\u0000-\u001F\u007F-\u009F\u2028\u2029]/g;
  var INVISIBLE =
    /[\u00AD\u061C\u180E\u200B-\u200F\u202A-\u202E\u2060-\u2064\u2066-\u2069\uFEFF\uFFF9-\uFFFB]|\uDB40[\uDC00-\uDC7F]/g;
  var SPACES = /\s+/g;

  function normalize(text) {
    return String(text == null ? '' : text)
      .replace(CONTROLS, ' ')
      .replace(INVISIBLE, '')
      .replace(SPACES, ' ')
      .trim();
  }

  function withoutFragment(url) {
    var at = url.indexOf('#');
    return at === -1 ? url : url.slice(0, at);
  }

  if (!args || typeof args.id !== 'string' || typeof args.url !== 'string') return false;
  if (withoutFragment(String(location.href)) !== withoutFragment(args.url)) return false;

  // Ids are compared as values, never put into a selector.
  var nodes = document.querySelectorAll(ID_SELECTOR);
  var target = null;
  var i;
  for (i = 0; i < nodes.length; i++) {
    if (nodes[i].getAttribute(ID) === args.id) {
      target = nodes[i];
      break;
    }
  }
  if (!target && typeof args.cited === 'string') {
    var needle = normalize(args.cited).slice(0, MAX_NEEDLE);
    if (needle.length > 1) {
      for (i = 0; i < nodes.length; i++) {
        if (nodes[i].querySelector(ID_SELECTOR)) continue;
        if (normalize(nodes[i].textContent).indexOf(needle) !== -1) {
          target = nodes[i];
          break;
        }
      }
    }
  }
  if (!target) return false;

  target.scrollIntoView({ behavior: 'smooth', block: 'center' });
  if (typeof target.animate === 'function') {
    target.animate(
      [{ backgroundColor: 'rgba(255, 214, 0, 0.45)' }, { backgroundColor: 'rgba(255, 214, 0, 0)' }],
      { duration: 1600, easing: 'ease-out' }
    );
  }
  return true;
}
