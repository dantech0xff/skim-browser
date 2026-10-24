// Reads the page as Reader view shows it and Skim reads it: the leaf text blocks of the article
// (Readability, when the page looks like one) or of the whole page, in document order. A div that
// holds only text and inline elements counts as a block too, as pages without paragraph elements
// put their text there. Runs after Readability-readerable.js and Readability.js inside one function
// scope, so nothing it declares reaches the page. Evaluates to a JSON string:
// {title, byline, siteName, lang, readerable, blocks: [{id, tag, text}]}.
(function () {
  'use strict';

  var BLOCKS = 'p,li,h1,h2,h3,h4,h5,h6,blockquote,pre,figcaption';
  // A div with none of these inside holds a run of text of its own; one with any only wraps blocks.
  var BLOCK_INSIDE =
    'address,article,aside,blockquote,details,dialog,div,dl,fieldset,figure,footer,form,h1,h2,h3,h4,h5,h6,' +
    'header,hr,li,main,nav,ol,p,pre,section,table,ul';
  var ID = 'data-skim-i';
  var ID_SELECTOR = '[data-skim-i]';
  var ID_FORMAT = /^\d{1,9}$/;
  var MAX_ID = 999999999;
  var MAX_BLOCKS = 400;
  var MAX_CHARS = 100000;
  var MAX_DEPTH = 64;
  // Control characters and line or paragraph separators read as spaces.
  var CONTROLS = /[\u0000-\u001F\u007F-\u009F\u2028\u2029]/g;
  // Characters a reader cannot see, or that reorder text: soft hyphen, zero-width characters,
  // bidi marks, embeddings, overrides and isolates, invisible operators, interlinear annotations
  // and the Unicode tag characters (a surrogate pair each), which can spell out hidden text.
  var INVISIBLE =
    /[\u00AD\u061C\u180E\u200B-\u200F\u202A-\u202E\u2060-\u2064\u2066-\u2069\uFEFF\uFFF9-\uFFFB]|\uDB40[\uDC00-\uDC7F]/g;
  var SPACES = /\s+/g;
  // Text inside these is never page text.
  var SKIPPED = { SCRIPT: true, STYLE: true, NOSCRIPT: true, TEMPLATE: true, SVG: true, svg: true };
  // Elements that end a run of text: words on either side of them must not run together.
  var BREAKS = {
    ADDRESS: true, ARTICLE: true, ASIDE: true, BLOCKQUOTE: true, DD: true, DETAILS: true, DIV: true, DL: true,
    DT: true, FIELDSET: true, FIGCAPTION: true, FIGURE: true, FOOTER: true, FORM: true, H1: true, H2: true,
    H3: true, H4: true, H5: true, H6: true, HEADER: true, HR: true, LI: true, MAIN: true, NAV: true, OL: true,
    P: true, PRE: true, SECTION: true, SUMMARY: true, TABLE: true, TD: true, TH: true, TR: true, UL: true
  };

  var view = document.defaultView || window;
  var hiddenCache = new Map();

  function clean(text) {
    return String(text == null ? '' : text)
      .replace(CONTROLS, ' ')
      .replace(INVISIBLE, '')
      .replace(SPACES, ' ')
      .trim();
  }

  // Hidden from the reader: by the hidden or aria-hidden attribute on it or an ancestor, by
  // display, visibility, opacity or a font size of 0, or by having (almost) no size. Pages hide
  // text this way to show a model what a reader never sees.
  function isHidden(element) {
    var known = hiddenCache.get(element);
    if (known !== undefined) return known;
    var hidden = computeHidden(element);
    hiddenCache.set(element, hidden);
    return hidden;
  }

  function computeHidden(element) {
    if (element.closest('[hidden],[aria-hidden="true"]')) return true;
    if (typeof element.checkVisibility === 'function') {
      var visible = element.checkVisibility({
        checkOpacity: true,
        checkVisibilityCSS: true,
        opacityProperty: true,
        visibilityProperty: true
      });
      if (!visible) return true;
    } else {
      if (element.getClientRects().length === 0) return true;
      for (var node = element; node; node = node.parentElement) {
        var nodeStyle = view.getComputedStyle(node);
        if (nodeStyle && parseFloat(nodeStyle.opacity) === 0) return true;
      }
    }
    var style = view.getComputedStyle(element);
    // Visibility and font size are inherited: the element's own values account for its ancestors.
    if (style && (style.visibility !== 'visible' || parseFloat(style.fontSize) === 0)) return true;
    var rect = element.getBoundingClientRect();
    return rect.width < 2 || rect.height < 2;
  }

  function collect(node, parts, depth) {
    if (depth > MAX_DEPTH) return;
    for (var child = node.firstChild; child; child = child.nextSibling) {
      if (child.nodeType === 3) {
        parts.push(child.nodeValue);
      } else if (child.nodeType === 1) {
        var tag = child.tagName;
        if (tag === 'BR') {
          parts.push(' ');
        } else if (!SKIPPED[tag] && !isHidden(child)) {
          var breaks = BREAKS[tag] === true;
          if (breaks) parts.push(' ');
          collect(child, parts, depth + 1);
          if (breaks) parts.push(' ');
        }
      }
    }
  }

  // The text of a block as it shows on screen: hidden descendants leave nothing behind.
  function visibleText(block) {
    var parts = [];
    collect(block, parts, 0);
    return clean(parts.join(''));
  }

  // A div that is a block of text: no block inside it, none around it that is read in its place,
  // and some text.
  function isTextDiv(element) {
    return (
      !element.querySelector(BLOCK_INSIDE) && !element.closest(BLOCKS) && element.textContent.trim().length > 1
    );
  }

  // Numbers the blocks of the live page. Numbers given by an earlier run stay, so a citation keeps
  // pointing at its block; new blocks, and a number the page copied onto a second element, get
  // fresh ones. A number on an element that is no block any more (a div that has since gained
  // blocks) is removed, so no two elements share one.
  function numberBlocks(root) {
    var found = root.querySelectorAll(BLOCKS + ',div');
    var nodes = [];
    var i;
    var value;
    for (i = 0; i < found.length; i++) {
      if (found[i].tagName !== 'DIV' || isTextDiv(found[i])) nodes.push(found[i]);
    }
    var kept = new Set(nodes);
    var numbered = root.querySelectorAll(ID_SELECTOR);
    var next = 0;
    for (i = 0; i < numbered.length; i++) {
      if (!kept.has(numbered[i])) {
        numbered[i].removeAttribute(ID);
        continue;
      }
      value = numbered[i].getAttribute(ID);
      if (ID_FORMAT.test(value)) next = Math.max(next, Number(value) + 1);
    }
    var renumber = next > MAX_ID - nodes.length;
    if (renumber) next = 0;
    var seen = new Set();
    var byId = new Map();
    for (i = 0; i < nodes.length; i++) {
      value = nodes[i].getAttribute(ID);
      if (renumber || value === null || !ID_FORMAT.test(value) || seen.has(value)) {
        value = String(next++);
        nodes[i].setAttribute(ID, value);
      }
      seen.add(value);
      byId.set(value, nodes[i]);
    }
    return { nodes: nodes, byId: byId };
  }

  // Readability turns the text of a div into a paragraph that keeps nothing of the div; a paragraph
  // it leaves as it is, so the copy it reads has its text divs as paragraphs, attributes and all.
  function asParagraph(div, doc) {
    var paragraph = doc.createElement('p');
    for (var i = 0; i < div.attributes.length; i++) paragraph.setAttributeNode(div.attributes[i].cloneNode(true));
    while (div.firstChild) paragraph.appendChild(div.firstChild);
    div.parentNode.replaceChild(paragraph, div);
  }

  function readArticle() {
    try {
      if (!isProbablyReaderable(document)) return null;
      // Readability changes the document it reads, so it reads a copy, which keeps the numbers.
      var copy = document.cloneNode(true);
      var divs = copy.querySelectorAll('div[' + ID + ']');
      for (var d = 0; d < divs.length; d++) asParagraph(divs[d], copy);
      var article = new Readability(copy, {
        serializer: function (element) {
          return element;
        }
      }).parse();
      return article && article.content && typeof article.content.querySelectorAll === 'function' ? article : null;
    } catch (e) {
      return null;
    }
  }

  function cut(text, max) {
    if (text.length <= max) return text;
    var end = max;
    var code = text.charCodeAt(end - 1);
    if (code >= 0xd800 && code <= 0xdbff) end--;
    return text.slice(0, end);
  }

  var root = document.body || document.documentElement;
  var numbered = numberBlocks(root);
  var article = readArticle();
  var candidates = article ? article.content.querySelectorAll(ID_SELECTOR) : numbered.nodes;
  var blocks = [];
  var emitted = new Set();
  var used = 0;
  for (var i = 0; i < candidates.length && blocks.length < MAX_BLOCKS && used < MAX_CHARS; i++) {
    var candidate = candidates[i];
    // Leaf blocks only: a list item that holds paragraphs is read through its paragraphs.
    if (candidate.querySelector(ID_SELECTOR)) continue;
    var id = candidate.getAttribute(ID);
    if (id === null || !ID_FORMAT.test(id) || emitted.has(id)) continue;
    // Visibility only exists on the live page; the copy Readability read has no layout.
    var live = numbered.byId.get(id);
    if (!live || isHidden(live)) continue;
    var text = cut(visibleText(live), MAX_CHARS - used);
    if (text.length < 2) continue;
    emitted.add(id);
    blocks.push({ id: id, tag: live.tagName === 'DIV' ? 'p' : live.tagName.toLowerCase(), text: text });
    used += text.length;
  }

  return JSON.stringify({
    title: clean((article && article.title) || document.title),
    byline: article && article.byline ? clean(article.byline) : null,
    siteName: article && article.siteName ? clean(article.siteName) : null,
    lang: (article && article.lang) || document.documentElement.getAttribute('lang') || null,
    readerable: article !== null,
    blocks: blocks
  });
})()
