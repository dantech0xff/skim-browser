// Reads a blob: URL of the page for a download, one slice at a time, for Skim, which drives the
// read with separate runs of this script and checks everything it gets back. Called with one
// argument, the JSON object {op, nonce, url?, origin?, maxBytes?, chunkChars?}:
// - start (url, origin, maxBytes, chunkChars): when the page is of origin, keeps the read's state
//   in one non-enumerable property of window named after the nonce and begins the read; evaluates
//   to {status: 'started', origin, nonce}, or else to {status: 'unavailable', nonce}.
// - take: evaluates to the read's next step: waiting, start {size, mime}, chunk {index, data},
//   end {chunks}, error {reason}, or gone when the page holds no read of that nonce (it was
//   dropped, or the page moved on to another document). Every step carries the nonce.
// - drop: deletes the read's state.
// A blob larger than maxBytes is refused before any of it is read, and a slice is only read once
// Skim took the one before, so the page never holds more than one slice as base64.
function (args) {
  'use strict';

  var KEY_PREFIX = '__skimBlobRead_';
  var nonce = args && typeof args.nonce === 'string' ? args.nonce : '';
  var key = KEY_PREFIX + nonce;
  var hasOwn = Object.prototype.hasOwnProperty;

  function reply(fields) {
    fields.nonce = nonce;
    return fields;
  }

  function current() {
    return hasOwn.call(window, key) ? window[key] : null;
  }

  function finish(state, message) {
    state.done = true;
    state.next = message;
  }

  function start() {
    if (typeof args.url !== 'string' || typeof args.origin !== 'string' ||
        typeof args.maxBytes !== 'number' || typeof args.chunkChars !== 'number') {
      return reply({ status: 'unavailable' });
    }
    // Only a page of the blob's own origin reads it, and one read per nonce.
    var origin = String(location.origin);
    if (origin !== args.origin || current()) return reply({ status: 'unavailable' });
    var state = {
      next: null,
      blob: null,
      offset: 0,
      index: 0,
      done: false,
      // Whole groups of 3 bytes, so that no chunk but the last needs base64 padding.
      sliceBytes: Math.max(3, Math.floor(args.chunkChars / 4) * 3)
    };
    Object.defineProperty(window, key, { value: state, configurable: true, enumerable: false, writable: false });
    fetch(args.url)
      .then(function (response) {
        return response.blob();
      })
      .then(function (blob) {
        if (current() !== state) return;
        if (blob.size > args.maxBytes) {
          finish(state, { status: 'error', reason: 'size' });
          return;
        }
        state.blob = blob;
        state.next = { status: 'start', size: blob.size, mime: String(blob.type || '') };
      })
      .catch(function () {
        if (current() === state) finish(state, { status: 'error', reason: 'fetch' });
      });
    return reply({ status: 'started', origin: origin });
  }

  function readNext(state) {
    var blob = state.blob;
    if (state.offset >= blob.size) {
      finish(state, { status: 'end', chunks: state.index });
      return;
    }
    var end = Math.min(state.offset + state.sliceBytes, blob.size);
    var reader = new FileReader();
    reader.onload = function () {
      if (current() !== state) return;
      var dataUrl = String(reader.result);
      var comma = dataUrl.indexOf(',');
      state.next = { status: 'chunk', index: state.index, data: comma === -1 ? '' : dataUrl.slice(comma + 1) };
      state.index += 1;
      state.offset = end;
    };
    reader.onerror = function () {
      if (current() === state) finish(state, { status: 'error', reason: 'read' });
    };
    reader.readAsDataURL(blob.slice(state.offset, end));
  }

  function take() {
    var state = current();
    if (!state) return reply({ status: 'gone' });
    var message = state.next;
    if (!message) return reply({ status: 'waiting' });
    state.next = null;
    // The next slice is only read now that this step was taken.
    if (!state.done) readNext(state);
    return reply(message);
  }

  if (!nonce) return reply({ status: 'unavailable' });
  if (args.op === 'start') return start();
  if (args.op === 'take') return take();
  if (args.op === 'drop') {
    delete window[key];
    return reply({ status: 'dropped' });
  }
  return reply({ status: 'unavailable' });
}
