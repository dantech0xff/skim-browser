package com.dantech.skimbrowser.extractor.youtube

import com.dantech.skimbrowser.domain.video.VideoExtractException
import org.junit.Assert.assertEquals
import org.junit.Test
import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.PaidContentException
import org.schabi.newpipe.extractor.exceptions.YoutubeMusicPremiumContentException

class YoutubeFailuresTest {

    @Test
    fun ageMembersAndDrmFailuresNameTheReason() {
        assertEquals(
            YoutubeFailures.AGE,
            YoutubeFailures.explain(AgeRestrictedContentException("anonymous")).message,
        )
        assertEquals(
            YoutubeFailures.MEMBERS,
            YoutubeFailures.explain(PaidContentException("This video is only available for members of the channel")).message,
        )
        assertEquals(
            YoutubeFailures.DRM,
            YoutubeFailures.explain(ContentNotAvailableException("playback uses DRM")).message,
        )
        assertEquals(
            YoutubeFailures.PREMIUM,
            YoutubeFailures.explain(YoutubeMusicPremiumContentException()).message,
        )
    }

    @Test
    fun theRefusalIsTheExceptionACallerCanShow() {
        val failure = YoutubeFailures.explain(AgeRestrictedContentException("anonymous"))

        assertEquals(VideoExtractException::class.java, failure.javaClass)
    }
}
