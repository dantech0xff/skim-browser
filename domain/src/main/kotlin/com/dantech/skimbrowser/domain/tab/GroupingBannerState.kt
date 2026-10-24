package com.dantech.skimbrowser.domain.tab

/** The "Skim grouped your tabs" banner. Null hides it. Undo stays enabled only while that layout is current. */
data class GroupingBannerState(val undoEnabled: Boolean)
