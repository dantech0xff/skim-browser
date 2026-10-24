package com.dantech.skimbrowser.browser

/**
 * Simple tab models live in `domain.tab`. These aliases keep existing `browser` imports compiling.
 * Types with nested classes (`LoadError`, `HitTarget`, `PageEvent`, `GroupLayout`, `RestoreResult`)
 * are imported from `domain.tab` directly, because a typealias hides nested classes.
 */
typealias Tab = com.dantech.skimbrowser.domain.tab.Tab
typealias TabGroup = com.dantech.skimbrowser.domain.tab.TabGroup
typealias TabsState = com.dantech.skimbrowser.domain.tab.TabsState
typealias TabRuntime = com.dantech.skimbrowser.domain.tab.TabRuntime
typealias FindState = com.dantech.skimbrowser.domain.tab.FindState
typealias CloseToken = com.dantech.skimbrowser.domain.tab.CloseToken

/** The current layout of the normal tabs. The rule lives in domain. */
fun TabsState.layout(): com.dantech.skimbrowser.domain.tab.GroupLayout =
    com.dantech.skimbrowser.domain.tab.layoutOf(this)
