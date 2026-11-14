package com.dantech.skimbrowser.extractor.youtube

import com.dantech.skimbrowser.domain.video.VideoExtractException
import com.dantech.skimbrowser.domain.video.VideoExtractFailure
import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException
import org.schabi.newpipe.extractor.exceptions.PaidContentException
import org.schabi.newpipe.extractor.exceptions.PrivateContentException
import org.schabi.newpipe.extractor.exceptions.YoutubeMusicPremiumContentException

internal object YoutubeFailures {
    fun explain(error: Throwable): VideoExtractException {
        val failure = when (error) {
            is AgeRestrictedContentException -> VideoExtractFailure.AGE_RESTRICTED
            is YoutubeMusicPremiumContentException -> VideoExtractFailure.PREMIUM
            is PaidContentException ->
                if (error.message.orEmpty().contains("member", ignoreCase = true)) {
                    VideoExtractFailure.MEMBERS_ONLY
                } else {
                    VideoExtractFailure.PAID
                }
            is PrivateContentException -> VideoExtractFailure.PRIVATE
            else -> when {
                textOf(error).contains("drm", ignoreCase = true) -> VideoExtractFailure.DRM
                textOf(error).contains("age-restricted", ignoreCase = true) -> VideoExtractFailure.AGE_RESTRICTED
                textOf(error).contains("age restricted", ignoreCase = true) -> VideoExtractFailure.AGE_RESTRICTED
                textOf(error).contains("member", ignoreCase = true) -> VideoExtractFailure.MEMBERS_ONLY
                else -> VideoExtractFailure.UNAVAILABLE
            }
        }
        return VideoExtractException(failure, error)
    }

    private fun textOf(error: Throwable): String =
        generateSequence(error) { it.cause }.mapNotNull { it.message }.joinToString(" ")
}
