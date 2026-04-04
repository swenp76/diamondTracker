package de.baseball.diamond9

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BaseballPositionsTest {

    @Test
    fun labelRes_returnsCorrectResForAllPositions() {
        assertEquals(R.string.pos_1, BaseballPositions.labelRes(1))
        assertEquals(R.string.pos_2, BaseballPositions.labelRes(2))
        assertEquals(R.string.pos_3, BaseballPositions.labelRes(3))
        assertEquals(R.string.pos_4, BaseballPositions.labelRes(4))
        assertEquals(R.string.pos_5, BaseballPositions.labelRes(5))
        assertEquals(R.string.pos_6, BaseballPositions.labelRes(6))
        assertEquals(R.string.pos_7, BaseballPositions.labelRes(7))
        assertEquals(R.string.pos_8, BaseballPositions.labelRes(8))
        assertEquals(R.string.pos_9, BaseballPositions.labelRes(9))
        assertEquals(R.string.pos_10, BaseballPositions.labelRes(10))
    }

    @Test
    fun labelRes_unknownPosition_returnsUnknownRes() {
        assertEquals(R.string.pos_short_unknown, BaseballPositions.labelRes(0))
        assertEquals(R.string.pos_short_unknown, BaseballPositions.labelRes(99))
    }

    @Test
    fun shortLabelRes_returnsCorrectRes() {
        assertEquals(R.string.pos_short_1, BaseballPositions.shortLabelRes(1))
        assertEquals(R.string.pos_short_2, BaseballPositions.shortLabelRes(2))
        assertEquals(R.string.pos_short_3, BaseballPositions.shortLabelRes(3))
        assertEquals(R.string.pos_short_4, BaseballPositions.shortLabelRes(4))
        assertEquals(R.string.pos_short_5, BaseballPositions.shortLabelRes(5))
        assertEquals(R.string.pos_short_6, BaseballPositions.shortLabelRes(6))
        assertEquals(R.string.pos_short_7, BaseballPositions.shortLabelRes(7))
        assertEquals(R.string.pos_short_8, BaseballPositions.shortLabelRes(8))
        assertEquals(R.string.pos_short_9, BaseballPositions.shortLabelRes(9))
        assertEquals(R.string.pos_short_10, BaseballPositions.shortLabelRes(10))
        assertEquals(R.string.pos_short_unknown, BaseballPositions.shortLabelRes(99))
    }
}