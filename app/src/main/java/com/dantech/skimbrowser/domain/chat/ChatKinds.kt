package com.dantech.skimbrowser.domain.chat

enum class ChatKind {
    /** About one page (06). */
    PAGE,

    /** About several open tabs (06b). */
    TABS,
}

enum class MessageRole { USER, ASSISTANT }

enum class MessageKind {
    QUESTION,
    SUMMARY,
    ANSWER,

    /** The overview of several tabs. [SavedMessage.text] holds its JSON. */
    TABS_OVERVIEW,
}

enum class MessageFeedback { NONE, GOOD, POOR }

enum class MessageStatus {
    COMPLETE,
    STOPPED,
    REFUSED,
    ERROR,
}
