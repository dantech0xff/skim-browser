package com.dantech.skimbrowser.domain.video

/** Why an extractor refused a page. The UI maps this to a string resource. */
enum class VideoExtractFailure {
    AGE_RESTRICTED,
    MEMBERS_ONLY,
    DRM,
    PREMIUM,
    PAID,
    PRIVATE,
    UNAVAILABLE,
}

/** A page matched an extractor and that extractor refused it or found nothing to download. */
class VideoExtractException(
    val failure: VideoExtractFailure,
    cause: Throwable? = null,
) : Exception(failure.name, cause)
