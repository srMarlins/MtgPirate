package match

import kotlin.test.Test
import kotlin.test.assertEquals

class LevenshteinTest {

    @Test
    fun identicalStringsHaveDistanceZero() {
        assertEquals(0, Levenshtein.distance("lightning bolt", "lightning bolt"))
    }

    @Test
    fun emptyStringsHaveDistanceZero() {
        assertEquals(0, Levenshtein.distance("", ""))
    }

    @Test
    fun emptyVsNonEmptyReturnsLengthOfNonEmpty() {
        assertEquals(5, Levenshtein.distance("", "hello"))
        assertEquals(5, Levenshtein.distance("hello", ""))
    }

    @Test
    fun singleCharacterInsertion() {
        assertEquals(1, Levenshtein.distance("cat", "cats"))
    }

    @Test
    fun singleCharacterDeletion() {
        assertEquals(1, Levenshtein.distance("cats", "cat"))
    }

    @Test
    fun singleCharacterSubstitution() {
        assertEquals(1, Levenshtein.distance("cat", "car"))
    }

    @Test
    fun knownDistanceKittenSitting() {
        assertEquals(3, Levenshtein.distance("kitten", "sitting"))
    }

    @Test
    fun knownDistanceSaturdaySunday() {
        assertEquals(3, Levenshtein.distance("saturday", "sunday"))
    }

    @Test
    fun symmetricDistance() {
        val a = "bolt"
        val b = "bold"
        assertEquals(Levenshtein.distance(a, b), Levenshtein.distance(b, a))
    }

    @Test
    fun completelyDifferentStrings() {
        assertEquals(3, Levenshtein.distance("abc", "xyz"))
    }
}
