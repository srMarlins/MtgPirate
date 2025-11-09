package app

import deck.DecklistParser

fun main() {
    val text = """
4 Brainstorm (MMQ 123)
2 Lightning Bolt (LEB)
1 Sol Ring
SIDEBOARD:
1 Juzám Djinn (ARN 45)

1 Kraum, Ludevic's Opus
""".trimIndent()
    val entries = DecklistParser.parse(text, includeSideboard = true, includeCommanders = true)
    for (e in entries) {
        println("${e.qty} ${e.cardName} sec=${e.section} include=${e.include} set=${e.setCodeHint} num=${e.collectorNumberHint}")
    }
}

