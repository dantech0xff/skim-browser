package com.dantech.skimbrowser.domain.video

/** A page matched an extractor and that extractor refused it or found nothing to download. */
class VideoExtractException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
