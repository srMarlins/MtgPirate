package util

fun formatPrice(cents: Int): String = String.format("%.2f", cents / 100.0)

