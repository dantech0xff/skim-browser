package com.dantech.skimbrowser.ai

import com.anthropic.models.messages.MessageCreateParams
import com.dantech.skimbrowser.browser.page.PageBlock
import com.dantech.skimbrowser.browser.page.PageContent

internal fun provider(baseUrl: String, apiKey: String? = "test-key") =
    ClaudeClientProvider(baseUrl, apiKey) { "install-id" }

internal fun probeParams(): MessageCreateParams =
    MessageCreateParams.builder()
        .model(AiConfig.MODEL_ID)
        .maxTokens(32)
        .addUserMessage("Hello")
        .build()

internal fun page(vararg paragraphs: String, url: String = PAGE_URL): PageContent = PageContent(
    url = url,
    loadNonce = 7,
    title = "Why heat pumps are winning in cold climates",
    byline = "Marta Lindqvist",
    siteName = "Daylight Journal",
    lang = "en",
    readerable = true,
    blocks = paragraphs.mapIndexed { index, text -> PageBlock(index.toString(), "p", text) },
)

internal fun articlePage(): PageContent = page(
    "For decades the advice was simple: if your winters drop well below freezing, a heat pump is not for you. " +
        "That advice is now out of date, and the numbers coming out of Norway, Finland and the northern United States show how quickly it has aged.",
    "Cold-climate models keep delivering useful heat at minus 25 C. A home switching from oil saves 300 to 900 dollars a year.",
)

internal const val PAGE_URL = "https://daylightjournal.com/cold"
internal const val PAGE_HOST = "daylightjournal.com"
