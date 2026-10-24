// Tells whether the page looks like an article Reader view can show, without running Readability
// itself. Runs after Readability-readerable.js inside one function scope, so nothing it declares
// reaches the page. Evaluates to true or false.
(function () {
  'use strict';

  try {
    return isProbablyReaderable(document) === true;
  } catch (e) {
    return false;
  }
})()
