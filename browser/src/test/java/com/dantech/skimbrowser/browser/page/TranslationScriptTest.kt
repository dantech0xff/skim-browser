package com.dantech.skimbrowser.browser.page

import com.dantech.skimbrowser.domain.translate.PageTextNode
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class TranslationScriptTest {

    @Test
    fun nodesKeepTheirIdTextAndViewportAndATamperedRowIsDropped() {
        val json = """[{"id":2,"text":"Hello","v":true},{"id":-1,"text":"no","v":false},{"text":"missing"}]"""

        assertEquals(listOf(PageTextNode(2, "Hello", true)), parseTranslationNodes(json))
    }

    @Test
    fun aStringWrappedArrayIsReadAndJunkIsEmpty() {
        val wrapped = JsonPrimitive("""[{"id":4,"text":"Later","v":false}]""").toString()

        assertEquals(listOf(PageTextNode(4, "Later", false)), parseTranslationNodes(wrapped))
        assertEquals(emptyList<PageTextNode>(), parseTranslationNodes("nope"))
        assertEquals(emptyList<PageTextNode>(), parseTranslationNodes("null"))
    }
}
