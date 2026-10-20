package com.storyboard.editor.data.converter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimestampParserTest {
    private val parser = TimestampParser()

    @Test
    fun parsesSecondsAndMinuteSecondRanges() {
        val result = parser.parse("0-5s\n00:05-00:10")

        assertTrue("Unexpected errors: ${result.errors}", result.errors.isEmpty())
        assertEquals(2, result.timestamps.size)
        assertEquals(5_000L, result.timestamps[0].durationMs)
        assertEquals(5_000L, result.timestamps[1].durationMs)
    }

    @Test
    fun rejectsZeroLengthAndBackwardsRanges() {
        val result = parser.parse("5-5\n9-8s")

        assertTrue(result.timestamps.isEmpty())
        assertEquals(2, result.errors.size)
        assertTrue(result.errors.all { it.message.contains("greater than") })
    }

    @Test
    fun rejectsInvalidMinuteSecondValuesAndFractionalMinutes() {
        val result = parser.parse("1.5:20-2:00\n0:61-1:00")

        assertTrue(result.timestamps.isEmpty())
        assertEquals(2, result.errors.size)
    }

    @Test
    fun preservesOriginalLineNumbersAcrossBlankLines() {
        val result = parser.parse("0-1\n\nnot-a-range\n2-3")

        assertEquals(2, result.timestamps.size)
        assertEquals(1, result.errors.size)
        assertEquals(3, result.errors.single().lineNumber)
    }

    @Test
    fun rejectsNonFiniteOrNegativeNumberSyntax() {
        val result = parser.parse("NaN-2\n-1-2\n1-Infinity")

        assertTrue(result.timestamps.isEmpty())
        assertEquals(3, result.errors.size)
    }
}
