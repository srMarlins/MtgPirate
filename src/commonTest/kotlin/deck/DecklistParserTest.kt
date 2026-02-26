package deck

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import model.Section

class DecklistParserTest {

    @Test
    fun parsesBasicQuantityAndName() {
        val result = DecklistParser.parse("4 Lightning Bolt", includeSideboard = true, includeCommanders = true)
        assertEquals(1, result.size)
        val entry = result.first()
        assertEquals(4, entry.qty)
        assertEquals("Lightning Bolt", entry.cardName)
        assertEquals(Section.MAIN, entry.section)
        assertTrue(entry.include)
    }

    @Test
    fun parsesSingleQuantity() {
        val result = DecklistParser.parse("1 Sol Ring", includeSideboard = true, includeCommanders = true)
        assertEquals(1, result.size)
        assertEquals(1, result.first().qty)
        assertEquals("Sol Ring", result.first().cardName)
    }

    @Test
    fun parsesSetCodeInParentheses() {
        val result = DecklistParser.parse("4 Lightning Bolt (M21)", includeSideboard = true, includeCommanders = true)
        assertEquals(1, result.size)
        val entry = result.first()
        assertEquals(4, entry.qty)
        assertEquals("Lightning Bolt", entry.cardName)
        assertEquals("M21", entry.setCodeHint)
    }

    @Test
    fun parsesSetCodeWithCollectorNumber() {
        val result = DecklistParser.parse("4 Lightning Bolt (M21 123)", includeSideboard = true, includeCommanders = true)
        assertEquals(1, result.size)
        val entry = result.first()
        assertEquals("Lightning Bolt", entry.cardName)
        assertEquals("M21", entry.setCodeHint)
        assertEquals("123", entry.collectorNumberHint)
    }

    @Test
    fun parsesSideboardSection() {
        val input = """
            4 Lightning Bolt
            SIDEBOARD:
            2 Pyroblast
        """.trimIndent()
        val result = DecklistParser.parse(input, includeSideboard = true, includeCommanders = true)
        assertEquals(2, result.size)
        assertEquals(Section.MAIN, result[0].section)
        assertEquals(Section.SIDEBOARD, result[1].section)
        assertEquals("Pyroblast", result[1].cardName)
        assertTrue(result[1].include)
    }

    @Test
    fun sideboardExcludedWhenFlagFalse() {
        val input = """
            4 Lightning Bolt
            SIDEBOARD:
            2 Pyroblast
        """.trimIndent()
        val result = DecklistParser.parse(input, includeSideboard = false, includeCommanders = true)
        assertEquals(2, result.size)
        assertTrue(result[0].include)
        assertEquals(false, result[1].include)
    }

    @Test
    fun blankLinesAreSkipped() {
        val input = """
            4 Lightning Bolt

            3 Counterspell
        """.trimIndent()
        val result = DecklistParser.parse(input, includeSideboard = true, includeCommanders = true)
        assertEquals(2, result.size)
        assertEquals("Lightning Bolt", result[0].cardName)
        assertEquals("Counterspell", result[1].cardName)
    }

    @Test
    fun htmlTagsAreStripped() {
        val input = "4 <b>Lightning Bolt</b>"
        val result = DecklistParser.parse(input, includeSideboard = true, includeCommanders = true)
        assertEquals(1, result.size)
        assertEquals("Lightning Bolt", result.first().cardName)
    }

    @Test
    fun htmlEntitiesAreDecoded() {
        val input = "4 Fire&amp;Ice"
        val result = DecklistParser.parse(input, includeSideboard = true, includeCommanders = true)
        assertEquals(1, result.size)
        assertEquals("Fire&Ice", result.first().cardName)
    }

    @Test
    fun nonBreakingSpacesAreNormalized() {
        val input = "4 Lightning\u00A0Bolt"
        val result = DecklistParser.parse(input, includeSideboard = true, includeCommanders = true)
        assertEquals(1, result.size)
        assertEquals("Lightning Bolt", result.first().cardName)
    }

    @Test
    fun emptyInputReturnsEmptyList() {
        val result = DecklistParser.parse("", includeSideboard = true, includeCommanders = true)
        assertTrue(result.isEmpty())
    }

    @Test
    fun linesWithoutQuantityAreSkipped() {
        val input = """
            4 Lightning Bolt
            Some random text
            3 Counterspell
        """.trimIndent()
        val result = DecklistParser.parse(input, includeSideboard = true, includeCommanders = true)
        assertEquals(2, result.size)
    }

    @Test
    fun multipleCardsAreParsedInOrder() {
        val input = """
            4 Lightning Bolt
            3 Counterspell
            2 Dark Ritual
            1 Sol Ring
        """.trimIndent()
        val result = DecklistParser.parse(input, includeSideboard = true, includeCommanders = true)
        assertEquals(4, result.size)
        assertEquals("Lightning Bolt", result[0].cardName)
        assertEquals("Counterspell", result[1].cardName)
        assertEquals("Dark Ritual", result[2].cardName)
        assertEquals("Sol Ring", result[3].cardName)
    }
}
