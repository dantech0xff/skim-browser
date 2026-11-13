// The map hangs off the document element so a later call finds the same nodes.
// A node longer than MAX_NODE is left alone: a cut translation would drop the rest of the sentence.
function (args) {
  'use strict';

  var KEY = 'skimTr';
  var MAX_VIEW = 80;
  var MAX_REST = 240;
  var MAX_NODE = 1800;
  var SKIP = { SCRIPT: 1, STYLE: 1, NOSCRIPT: 1, TEXTAREA: 1, INPUT: 1, SELECT: 1, OPTION: 1 };

  function bagOf() {
    var root = document.documentElement;
    if (!root) return null;
    var bag = root[KEY];
    if (bag) return bag;
    bag = { weak: new WeakMap(), nodes: {}, originals: {}, translations: {}, next: 1, showing: 'page' };
    Object.defineProperty(root, KEY, { value: bag });
    return bag;
  }

  function hidden(el) {
    var node = el;
    var steps = 0;
    while (node && steps < 8) {
      var style = getComputedStyle(node);
      if (style.display === 'none' || style.visibility === 'hidden') return true;
      node = node.parentElement;
      steps++;
    }
    return false;
  }

  function seen(el) {
    var rect = el.getBoundingClientRect();
    var height = innerHeight || 0;
    var width = innerWidth || 0;
    return rect.bottom > 0 && rect.top < height && rect.right > 0 && rect.left < width;
  }

  function squash(text) {
    return String(text == null ? '' : text).replace(/\s+/g, ' ').trim();
  }

  var op = args && args.op;
  var bag = bagOf();
  if (!bag) return op === 'collect' ? [] : false;

  if (op === 'collect') {
    if (!document.body) return [];
    bag.showing = 'page';
    var view = [];
    var rest = [];
    var walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, null);
    var node;
    while ((node = walker.nextNode())) {
      if (view.length >= MAX_VIEW && rest.length >= MAX_REST) break;
      var parent = node.parentElement;
      if (!parent || SKIP[parent.tagName] || parent.isContentEditable || hidden(parent)) continue;
      var known = bag.weak.get(node);
      var raw = known != null && bag.originals[known] != null ? bag.originals[known] : node.nodeValue;
      var text = squash(raw);
      if (text.length < 2 || text.length > MAX_NODE) continue;
      var inView = seen(parent);
      var intoView = inView && view.length < MAX_VIEW;
      if (!intoView && rest.length >= MAX_REST) continue;
      var id = known;
      if (id == null) {
        id = bag.next++;
        bag.weak.set(node, id);
        bag.nodes[id] = node;
        bag.originals[id] = node.nodeValue;
      }
      var row = { id: id, text: text, v: inView };
      if (intoView) view.push(row);
      else rest.push(row);
    }
    return view.concat(rest);
  }

  if (op === 'apply') {
    var rows = (args && args.rows) || [];
    var i;
    var wrote = false;
    for (i = 0; i < rows.length; i++) {
      var row = rows[i];
      if (!row || typeof row.text !== 'string' || row.text.length === 0) continue;
      var target = bag.nodes[row.id];
      if (!target || !target.parentNode) continue;
      bag.translations[row.id] = row.text;
      if (bag.showing !== 'original') target.nodeValue = row.text;
      wrote = true;
    }
    if (wrote && bag.showing !== 'original') bag.showing = 'translated';
    return wrote;
  }

  if (op === 'original' || op === 'translation') {
    bag.showing = op;
    var source = op === 'original' ? bag.originals : bag.translations;
    var key;
    var wrote = false;
    for (key in source) {
      if (!Object.prototype.hasOwnProperty.call(source, key)) continue;
      var live = bag.nodes[key];
      if (live && live.parentNode) {
        live.nodeValue = source[key];
        wrote = true;
      }
    }
    return wrote;
  }

  return false;
}
