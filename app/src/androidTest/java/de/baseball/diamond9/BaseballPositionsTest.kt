package de.baseball.diamond9

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BaseballPositionsTest {

    @Test
    fun label_returnsCorrectNameForAllPositions() {
        assertEquals("1 – Pitcher", BaseballPositions.label(1))
        assertEquals("2 – Catcher", BaseballPositions.label(2))
        assertEquals("3 – First Base", BaseballPositions.label(3))
        assertEquals("4 – Second Base", BaseballPositions.label(4))
        assertEquals("5 – Third Base", BaseballPositions.label(5))
        assertEquals("6 – Shortstop", BaseballPositions.label(6))
        assertEquals("7 – Left Field", BaseballPositions.label(7))
        assertEquals("8 – Center Field", BaseballPositions.label(8))
        assertEquals("9 – Right Field", BaseballPositions.label(9))
        assertEquals("DH – Designated Hitter", BaseballPositions.label(10))
    }

    @Test
    fun label_unknownPosition_returnsQuestionMark() {
        assertEquals("?", BaseballPositions.label(0))
        assertEquals("?", BaseballPositions.label(99))
    }

    @Test
    fun shortLabel_returnsCorrectAbbreviations() {
        assertEquals("P", BaseballPositions.shortLabel(1))
        assertEquals("C", BaseballPositions.shortLabel(2))
        assertEquals("1B", BaseballPositions.shortLabel(3))
        assertEquals("2B", BaseballPositions.shortLabel(4))
        assertEquals("3B", BaseballPositions.shortLabel(5))
        assertEquals("SS", BaseballPositions.shortLabel(6))
        assertEquals("LF", BaseballPositions.shortLabel(7))
        assertEquals("CF", BaseballPositions.shortLabel(8))
        assertEquals("RF", BaseballPositions.shortLabel(9))
        assertEquals("DH", BaseballPositions.shortLabel(10))
        assertEquals("?", BaseballPositions.shortLabel(99))
    }
}