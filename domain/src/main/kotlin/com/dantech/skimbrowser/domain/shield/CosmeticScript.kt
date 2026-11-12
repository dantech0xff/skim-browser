package com.dantech.skimbrowser.domain.shield

import com.dantech.skimbrowser.domain.url.normalizeHost

/**
 * The script that installs one stylesheet. It looks up a single style element by id and sets its
 * text. It does not walk the page's nodes. A frame whose host is not [host] returns without
 * writing, so a script armed for one site does not hide another.
 */
object CosmeticScript {
    const val HIDE_STYLE_ID = "skim-hide"
    const val COLLAPSE_STYLE_ID = "skim-collapse"

    fun hide(host: String, css: String): String = install(HIDE_STYLE_ID, normalizeHost(host), css)

    fun collapse(css: String): String = install(COLLAPSE_STYLE_ID, host = null, css)

    private fun install(id: String, host: String?, css: String): String = buildString {
        append("(function(){")
        if (host != null) {
            append("var h=(location.hostname||'').toLowerCase();")
            append("if(h.indexOf('www.')===0)h=h.substring(4);")
            append("if(h!==")
            append(jsString(host))
            append(")return;")
        }
        append("var r=document.documentElement;if(!r)return;")
        append("var s=document.getElementById(")
        append(jsString(id))
        append(");")
        if (css.isEmpty()) {
            append("if(s)s.remove();")
        } else {
            append("if(!s){s=document.createElement('style');s.id=")
            append(jsString(id))
            append(";r.appendChild(s);}s.textContent=")
            append(jsString(css))
            append(";")
        }
        append("})()")
    }
}

/** A JavaScript string literal. `<` is escaped so the text cannot close a script element. */
internal fun jsString(value: String): String = buildString(value.length + 2) {
    append('"')
    for (ch in value) {
        when (ch) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '<' -> append("\\u003c")
            '\u2028' -> append("\\u2028")
            '\u2029' -> append("\\u2029")
            else -> append(ch)
        }
    }
    append('"')
}
