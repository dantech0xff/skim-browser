package com.dantech.skimbrowser.ai

import org.junit.Assert.assertTrue
import org.junit.Test

class PromptsTest {
    @Test
    fun aSummaryIsExactlyThreePoints() {
        val instruction = Prompts.summaryInstruction("vi")
        assertTrue(instruction.contains("exactly 3 points"))
        assertTrue(instruction.contains("vi"))
    }
}
