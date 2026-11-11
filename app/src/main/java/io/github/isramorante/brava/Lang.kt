package io.github.isramorante.brava

import java.util.Locale

/** Picks the Spanish text when the phone is set to Spanish, English otherwise. */
fun tr(en: String, es: String): String = if (Locale.getDefault().language == "es") es else en

fun fmtDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

fun fmtGb(bytes: Long): String = String.format(Locale.US, "%.1f", bytes / 1073741824.0)
