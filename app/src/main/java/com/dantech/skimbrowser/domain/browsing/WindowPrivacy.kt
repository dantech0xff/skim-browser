package com.dantech.skimbrowser.domain.browsing

/**
 * The window's privacy switches. [setSecure] blocks screenshots while [secure] is true.
 * [setRecentsHidden] hides the Recents preview while [hidden] is true.
 */
interface WindowPrivacy {
    fun setSecure(owner: String, secure: Boolean)

    fun setRecentsHidden(owner: String, hidden: Boolean)
}
