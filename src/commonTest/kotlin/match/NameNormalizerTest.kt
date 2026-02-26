package match

import kotlin.test.Test
import kotlin.test.assertEquals

class NameNormalizerTest {

    @Test
    fun lowercasesAndCollapsesWhitespace() {
        assertEquals("lightning bolt", NameNormalizer.normalize("  Lightning   Bolt  "))
    }

    @Test
    fun removesApostrophes() {
        assertEquals("jaces ingenuity", NameNormalizer.normalize("Jace's Ingenuity"))
    }

    @Test
    fun removesBacktickAndRightQuote() {
        assertEquals("jaces ingenuity", NameNormalizer.normalize("Jace\u2019s Ingenuity"))
        assertEquals("jaces ingenuity", NameNormalizer.normalize("Jace`s Ingenuity"))
    }

    @Test
    fun removesCommas() {
        assertEquals("anje falkenrath", NameNormalizer.normalize("Anje, Falkenrath"))
    }

    @Test
    fun removesDoubleQuotes() {
        assertEquals("the deck of many things", NameNormalizer.normalize("The \"Deck\" of Many Things"))
    }

    @Test
    fun replacesHyphensAndDashesWithSpaces() {
        assertEquals("lim dul the necromancer", NameNormalizer.normalize("Lim-Dul the Necromancer"))
    }

    @Test
    fun replacesEmDashWithSpace() {
        assertEquals("fire ice", NameNormalizer.normalize("Fire\u2014Ice"))
    }

    @Test
    fun replacesEnDashWithSpace() {
        assertEquals("fire ice", NameNormalizer.normalize("Fire\u2013Ice"))
    }

    @Test
    fun stripsNonAlphanumericCharacters() {
        assertEquals("nissa who shakes the world", NameNormalizer.normalize("Nissa, Who Shakes the World!"))
    }

    @Test
    fun takesOnlyFirstFaceOfSplitCard() {
        assertEquals("fire", NameNormalizer.normalize("Fire // Ice"))
    }

    @Test
    fun emptyStringReturnsEmpty() {
        assertEquals("", NameNormalizer.normalize(""))
    }

    @Test
    fun alreadyNormalizedStringIsUnchanged() {
        assertEquals("lightning bolt", NameNormalizer.normalize("lightning bolt"))
    }
}
