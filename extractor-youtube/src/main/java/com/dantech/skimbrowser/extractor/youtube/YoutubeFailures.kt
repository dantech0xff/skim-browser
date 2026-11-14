package com.dantech.skimbrowser.extractor.youtube

import com.dantech.skimbrowser.domain.video.VideoExtractException
import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException
import org.schabi.newpipe.extractor.exceptions.PaidContentException
import org.schabi.newpipe.extractor.exceptions.PrivateContentException
import org.schabi.newpipe.extractor.exceptions.YoutubeMusicPremiumContentException

internal object YoutubeFailures {
    const val AGE = "This video is age-restricted. Skim does not download it."
    const val MEMBERS = "This video is for channel members. Skim does not download it."
    const val DRM = "This video uses DRM. Skim does not download it."
    const val PREMIUM = "This video needs YouTube Music Premium. Skim does not download it."
    const val PAID = "This video is paid. Skim does not download it."
    const val PRIVATE = "This video is private. Skim does not download it."
    const val NONE = "Skim could not find a downloadable stream for this video."

    fun explain(error: Throwable): VideoExtractException {
        val message = when (error) {
            is AgeRestrictedContentException -> AGE
            is YoutubeMusicPremiumContentException -> PREMIUM
            is PaidContentException ->
                if (error.message.orEmpty().contains("member", ignoreCase = true)) MEMBERS else PAID
            is PrivateContentException -> PRIVATE
            else -> when {
                textOf(error).contains("drm", ignoreCase = true) -> DRM
                textOf(error).contains("age-restricted", ignoreCase = true) -> AGE
                textOf(error).contains("age restricted", ignoreCase = true) -> AGE
                textOf(error).contains("member", ignoreCase = true) -> MEMBERS
                else -> NONE
            }
        }
        return VideoExtractException(message, error)
    }

    private fun textOf(error: Throwable): String =
        generateSequence(error) { it.cause }.mapNotNull { it.message }.joinToString(" ")
}
