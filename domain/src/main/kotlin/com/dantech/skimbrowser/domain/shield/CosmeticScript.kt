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
    const val COOKIE_STYLE_ID = "skim-cookie"

    fun hide(host: String, css: String): String = install(HIDE_STYLE_ID, normalizeHost(host), css)

    fun collapse(css: String): String = install(COLLAPSE_STYLE_ID, host = null, css)

    /**
     * Hides cookie banners on [host] and, when one of them is on screen and the page has locked
     * scrolling, lets the page scroll again. The lock check runs before the stylesheet is set,
     * because the hide would make the banner `display:none` and the check would miss it.
     */
    fun cookie(host: String, css: String): String = buildString {
        append("(function(){")
        append("var h=(location.hostname||'').toLowerCase();")
        append("if(h.indexOf('www.')===0)h=h.substring(4);")
        append("if(h!==")
        append(jsString(normalizeHost(host)))
        append(")return;")
        append("var r=document.documentElement;if(!r)return;")
        append("var b=document.body;var lock=false;try{")
        append("var hs=getComputedStyle(r);var bs=b?getComputedStyle(b):null;")
        append("var hidden=hs.overflow==='hidden'||hs.overflowY==='hidden'||")
        append("(bs&&(bs.overflow==='hidden'||bs.overflowY==='hidden'||bs.position==='fixed'));")
        append("if(hidden){try{if(document.querySelector(")
        append(jsString(COOKIE_PROBES))
        append("))lock=true;}catch(e){}}")
        append("}catch(e){}")
        append("var s=document.getElementById(")
        append(jsString(COOKIE_STYLE_ID))
        append(");")
        if (css.isEmpty()) {
            append("if(s)s.remove();")
        } else {
            append("if(!s){s=document.createElement('style');s.id=")
            append(jsString(COOKIE_STYLE_ID))
            append(";r.appendChild(s);}s.textContent=")
            append(jsString(css))
            append(";")
        }
        append("if(lock){r.style.setProperty('overflow','auto','important');")
        append("r.style.setProperty('overflow-y','auto','important');")
        append("if(b){b.style.setProperty('overflow','auto','important');")
        append("b.style.setProperty('overflow-y','auto','important');")
        append("b.style.setProperty('position','static','important');}}")
        append("})()")
    }

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

private const val COOKIE_PROBES = "#onetrust-consent-sdk,#onetrust-banner-sdk,#CybotCookiebotDialog," +
    "#CybotCookiebotDialogBodyUnderlay,#didomi-host,#usercentrics-root,#sp_message_container," +
    "#qc-cmp2-container,#tarteaucitronRoot,.osano-cm-window,.fc-consent-root,.cc-window," +
    "#cookie-law-info-bar,#hs-eu-cookie-confirmation,.cky-consent-container,#cmplz-cookiebanner-container"

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
