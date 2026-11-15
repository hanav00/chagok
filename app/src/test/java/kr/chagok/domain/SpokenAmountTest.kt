package kr.chagok.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 말한 금액 읽기 [SpokenAmount.parse]: 받아쓰기가 숫자로 주든 한글로 주든 원으로 */
class SpokenAmountTest {
    @Test
    fun `숫자로 받아쓴 금액`() {
        assertEquals(50_000L, SpokenAmount.parse("5만 원"))
        assertEquals(54_000L, SpokenAmount.parse("54,000원"))
        assertEquals(54_000L, SpokenAmount.parse("5만4천원"))
        assertEquals(54_000L, SpokenAmount.parse("54000"))
    }

    @Test
    fun `한글로 받아쓴 금액`() {
        assertEquals(50_000L, SpokenAmount.parse("오만 원"))
        assertEquals(72_500L, SpokenAmount.parse("칠만 이천오백 원"))
        assertEquals(100_000L, SpokenAmount.parse("십만 원"))
        assertEquals(10_000L, SpokenAmount.parse("만 원"))
    }

    @Test
    fun `원 앞의 숫자만 읽어 앞뒤 말을 숫자로 잡지 않는다`() {
        assertEquals(50_000L, SpokenAmount.parse("이번에 오만 원이요"))
        assertEquals(63_000L, SpokenAmount.parse("주유비는 6만 3천 원 나왔어"))
    }

    @Test
    fun `원이 없으면 말 전체가 숫자일 때만 읽는다`() {
        assertEquals(50_000L, SpokenAmount.parse("오만"))
        assertNull(SpokenAmount.parse("오만 넣었어"))
    }

    @Test
    fun `숫자가 없거나 0원이거나 너무 크면 못 알아들은 것이다`() {
        assertNull(SpokenAmount.parse("카드로 냈어요"))
        assertNull(SpokenAmount.parse("0원"))
        assertNull(SpokenAmount.parse("2백만 원"))
        assertNull(SpokenAmount.parse(""))
    }
}
