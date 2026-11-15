package kr.chagok.domain

import kr.chagok.domain.model.Person

/** 말한 이름으로 등록된 사람 찾기. Gleo 명령과 마이크 받아쓰기가 같이 쓴다 */
object PersonMatcher {
    private val HONORIFICS = listOf("님", "씨")

    /**
     * 말한 이름에 맞는 사람. 띄어쓰기·"님/씨"는 무시하고, 똑같은 이름이 있으면 그 사람,
     * 없으면 이름 일부("정훈")가 맞는 사람들. 이름 없이 등록한 사람은 부를 수 없다.
     */
    fun match(spoken: String, people: List<Person>): List<Person> {
        val wanted = normalize(spoken)
        if (wanted.isEmpty()) return emptyList()
        val named = people.filter(Person::isNamed)
        named.filter { normalize(it.name) == wanted }.takeIf { it.isNotEmpty() }?.let { return it }
        return named.filter { normalize(it.name).contains(wanted) }
    }

    private fun normalize(name: String): String {
        val compact = name.filterNot(Char::isWhitespace)
        val honorific = HONORIFICS.firstOrNull { compact.length > it.length && compact.endsWith(it) }
        return honorific?.let { compact.removeSuffix(it) } ?: compact
    }
}
