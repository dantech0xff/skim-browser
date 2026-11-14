package com.dantech.skimbrowser.extractor.youtube

import com.dantech.skimbrowser.domain.video.VideoExtractException
import com.dantech.skimbrowser.domain.video.VideoExtractFailure
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
            VideoExtractFailure.AGE_RESTRICTED,
            YoutubeFailures.explain(AgeRestrictedContentException("anonymous")).failure,
        )
        assertEquals(
            VideoExtractFailure.MEMBERS_ONLY,
            YoutubeFailures.explain(PaidContentException("This video is only available for members of the channel")).failure,
        )
        assertEquals(
            VideoExtractFailure.DRM,
            YoutubeFailures.explain(ContentNotAvailableException("playback uses DRM")).failure,
        )
        assertEquals(
            VideoExtractFailure.PREMIUM,
            YoutubeFailures.explain(YoutubeMusicPremiumContentException()).failure,
        )
    }

    @Test
    fun theRefusalIsTheExceptionACallerCanShow() {
        val failure = YoutubeFailures.explain(AgeRestrictedContentException("anonymous"))

        assertEquals(VideoExtractException::class.java, failure.javaClass)
    }
}
