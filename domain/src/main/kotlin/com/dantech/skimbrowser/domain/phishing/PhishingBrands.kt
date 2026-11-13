package com.dantech.skimbrowser.domain.phishing

/**
 * High-value names the on-device check compares against. The list is built on first use, not at
 * process start, so opening the app does not pay for it.
 *
 * [domains] are registrable hosts Skim treats as the real site. [keys] are the names a lookalike
 * is measured against. A short or generic label (the "cash" in cash.app) is not a key.
 */
internal data class PhishingBrand(
    val name: String,
    val domains: Set<String>,
    val keys: Set<String>,
)

internal object PhishingBrands {
    val all: List<PhishingBrand> by lazy { brands() }

    val officialDomains: Set<String> by lazy { all.flatMap { it.domains }.toSet() }
}

private fun brands(): List<PhishingBrand> = listOf(
    brand("PayPal", "paypal.com", "paypal.me"),
    brand("Visa", "visa.com"),
    brand("Mastercard", "mastercard.com"),
    brand("American Express", "americanexpress.com", "amex.com"),
    brand("Chase", "chase.com"),
    brand("Bank of America", "bankofamerica.com"),
    brand("Wells Fargo", "wellsfargo.com"),
    brand("Citi", "citi.com", "citibank.com"),
    brand("HSBC", "hsbc.com"),
    brand("Barclays", "barclays.com", "barclays.co.uk"),
    brand("Revolut", "revolut.com"),
    brand("Wise", "wise.com"),
    brand("Venmo", "venmo.com"),
    brand("Skrill", "skrill.com"),
    brand("MoMo", "momo.vn"),
    brand("ZaloPay", "zalopay.vn"),
    brand("VNPay", "vnpay.vn"),
    brand("Vietcombank", "vietcombank.com.vn"),
    brand("VietinBank", "vietinbank.vn"),
    brand("BIDV", "bidv.com.vn"),
    brand("Techcombank", "techcombank.com.vn"),
    brand("MB Bank", "mbbank.com.vn"),
    brand("ACB", "acb.com.vn"),
    brand("Sacombank", "sacombank.com.vn"),
    brand("VPBank", "vpbank.com.vn"),
    brand("TPBank", "tpbank.vn"),
    brand("Agribank", "agribank.com.vn"),
    brand(
        "Google",
        "google.com",
        "google.com.vn",
        "google.co.uk",
        "google.com.au",
        "google.co.jp",
        "google.de",
        "google.fr",
        "gmail.com",
    ),
    brand("Facebook", "facebook.com", "fb.com", "facebook.com.vn"),
    brand("Instagram", "instagram.com"),
    brand("WhatsApp", "whatsapp.com"),
    brand("Apple", "apple.com", "icloud.com", "apple.co.uk"),
    brand("Microsoft", "microsoft.com", "live.com", "outlook.com", "office.com"),
    brand("Amazon", "amazon.com", "amazon.co.uk", "amazon.co.jp"),
    brand("Binance", "binance.com"),
    brand("Coinbase", "coinbase.com"),
    brand("Kraken", "kraken.com"),
    brand("MetaMask", "metamask.io"),
    brand("Bybit", "bybit.com"),
    brand("OKX", "okx.com"),
    brand("KuCoin", "kucoin.com"),
    brand("Crypto.com", "crypto.com"),
    brand("Trust Wallet", "trustwallet.com"),
)

private fun brand(name: String, vararg domains: String): PhishingBrand {
    val keys = domains.mapNotNull { domain ->
        val key = registrableLabel(domain.split('.'))
        key?.takeIf { it.length >= 3 && it !in GENERIC_KEYS }
    }.toSet()
    return PhishingBrand(name, domains.toSet(), keys)
}

/** Official domains whose registrable label is a common word, not a name to imitate. */
private val GENERIC_KEYS = setOf("live", "office", "crypto")
