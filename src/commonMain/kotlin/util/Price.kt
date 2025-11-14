package util

import platform.formatDecimal

fun formatPrice(cents: Int): String = formatDecimal(cents / 100.0, 2)

