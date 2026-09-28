package com.yy.lottosim.ui

import java.util.Locale

/** 金额格式化：1234567 → "123.46万"；123456789 → "1.23亿" */
fun formatMoney(yuan: Long): String {
    val abs = kotlin.math.abs(yuan)
    val sign = if (yuan < 0) "-" else ""
    return sign + when {
        abs >= 100_000_000L -> String.format(Locale.CHINA, "%.2f亿", abs / 100_000_000.0)
        abs >= 10_000L -> String.format(Locale.CHINA, "%.1f万", abs / 10_000.0)
        else -> "${abs}元"
    }
}

fun formatMoneyExact(yuan: Long): String =
    java.text.NumberFormat.getIntegerInstance(Locale.CHINA).format(yuan) + " 元"

fun formatTime(millis: Long): String {
    if (millis <= 0) return "从未"
    return java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .format(java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault()))
}

fun formatCount(n: Long): String =
    java.text.NumberFormat.getIntegerInstance(Locale.CHINA).format(n)
