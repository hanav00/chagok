package kr.chagok.domain

/**
 * 말한 금액 읽기: "5만 원", "오만 사천 원", "54,000원", "3만5천원이요" → 원.
 * "원"이 있으면 그 바로 앞의 숫자 덩어리만 읽어 "이번에 오만 원"의 "이"를 숫자로 잡지 않는다.
 * "원"이 없으면 말 전체가 숫자일 때만 읽는다. 못 읽거나 0원이거나 너무 크면 null.
 */
object SpokenAmount {
    private const val WON = '원'
    private const val TEN_THOUSAND = 10_000L

    private val DIGIT_WORDS = mapOf(
        '영' to 0, '공' to 0, '일' to 1, '이' to 2, '삼' to 3, '사' to 4,
        '오' to 5, '육' to 6, '칠' to 7, '팔' to 8, '구' to 9,
    )
    private val SMALL_UNITS = mapOf('십' to 10L, '백' to 100L, '천' to 1_000L)
    private const val MAN = '만'

    fun parse(spoken: String): Long? {
        val text = spoken.filterNot { it == ',' || it.isWhitespace() }
        val number = if (WON in text) {
            text.substringBefore(WON).takeLastWhile(::isNumberChar)
        } else {
            text.takeIf { it.all(::isNumberChar) }
        }
        if (number.isNullOrEmpty()) return null
        return read(number)?.takeIf { it in 1..ChagokConfig.REFUEL_AMOUNT_MAX_WON }
    }

    private fun isNumberChar(char: Char): Boolean =
        char.isDigit() || char in DIGIT_WORDS || char in SMALL_UNITS || char == MAN

    // "5만4천" = 5 × 만 + 4 × 천. 단위 앞에 숫자가 없으면 1 ("만 원", "천 원")
    private fun read(number: String): Long? {
        var total = 0L
        var section = 0L
        var pending: Long? = null
        for (char in number) {
            when {
                char.isDigit() -> pending = (pending ?: 0L) * 10 + char.digitToInt()
                char in DIGIT_WORDS -> pending = DIGIT_WORDS.getValue(char).toLong()
                char in SMALL_UNITS -> {
                    section += (pending ?: 1L) * SMALL_UNITS.getValue(char)
                    pending = null
                }
                char == MAN -> {
                    val upToMan = section + (pending ?: 0L)
                    total += (if (upToMan == 0L) 1L else upToMan) * TEN_THOUSAND
                    section = 0L
                    pending = null
                }
                else -> return null
            }
        }
        return total + section + (pending ?: 0L)
    }
}
