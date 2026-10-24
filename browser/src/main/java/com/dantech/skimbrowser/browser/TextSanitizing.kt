package com.dantech.skimbrowser.browser

import com.dantech.skimbrowser.domain.text.cutTo as domainCutTo
import com.dantech.skimbrowser.domain.text.toVisibleText as domainToVisibleText

/** [this] cut to at most [max] UTF-16 units. The rule lives in domain. */
internal fun String.cutTo(max: Int): String = domainCutTo(max)

/** [this] as plain visible text. The rule lives in domain. */
internal fun String.toVisibleText(): String = domainToVisibleText()
