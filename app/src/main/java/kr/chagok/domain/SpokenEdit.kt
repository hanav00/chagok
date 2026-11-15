package kr.chagok.domain

import kr.chagok.domain.model.Category

/**
 * 마이크 한마디가 운행의 무엇을 고치는 말인지. 정해 둔 짧은 말만 분류·사람으로 받고, 나머지는 모두 방문 목적이다.
 * "개인 고객 미팅"처럼 분류 말이 섞인 긴 말은 방문 목적으로 남는다. 잘못 바꾸는 것보다 메모로 남는 편이 낫다.
 */
sealed interface SpokenEdit {
    data class Classify(val category: Category) : SpokenEdit

    /**
     * [names] 는 이름 후보로, 앞에서부터 맞춰 본다 ("박정훈이" → 그런 사람이 없으면 "박정훈").
     * 맞는 사람이 없으면 [spoken] 을 방문 목적으로 남긴다
     */
    data class AssignPerson(val names: List<String>, val spoken: String) : SpokenEdit

    data class Note(val text: String) : SpokenEdit

    companion object {
        // 띄어쓰기·끝말을 뗀 뒤 이 말과 똑같아야 분류를 바꾼다
        private val CATEGORY_WORDS = mapOf(
            "개인" to Category.PERSONAL,
            "개인용무" to Category.PERSONAL,
            "개인운행" to Category.PERSONAL,
            "업무" to Category.BUSINESS,
            "업무운행" to Category.BUSINESS,
            "일반업무" to Category.BUSINESS,
            "출퇴근" to Category.COMMUTE,
            "출퇴근운행" to Category.COMMUTE,
        )

        // 긴 것부터 뗀다 ("으로"를 "로"보다 먼저). "업무로요"처럼 겹치면 여러 번 뗀다
        private val ENDINGS = listOf(
            "으로바꿔줘", "로바꿔줘", "으로기록해줘", "로기록해줘", "으로해줘", "로해줘",
            "이었어요", "였어요", "이었어", "였어", "이에요", "예요", "이야", "이요", "으로", "로", "야", "요",
        )

        // "박정훈이 탔어", "최서연 운전했어요". 이 모양으로 끝나는 말만 사람 지정으로 본다
        private val PERSON_PATTERN = Regex("^(.+?)(탔어요|탔어|탔습니다|탔음|운전했어요|운전했어|운전했습니다|운전해요|운전해|운전중|운전함)$")
        private val SUBJECT_PARTICLES = listOf("이", "가")

        fun parse(spoken: String): SpokenEdit {
            val text = spoken.trim()
            val compact = text.filter(Char::isLetterOrDigit)
            CATEGORY_WORDS[stripEndings(compact)]?.let { return Classify(it) }
            PERSON_PATTERN.matchEntire(compact)?.let { return AssignPerson(nameCandidates(it.groupValues[1]), text) }
            return Note(text)
        }

        private fun stripEndings(word: String): String {
            var rest = word
            while (true) {
                val ending = ENDINGS.firstOrNull { rest.length > it.length && rest.endsWith(it) } ?: return rest
                rest = rest.removeSuffix(ending)
            }
        }

        private fun nameCandidates(raw: String): List<String> {
            val particle = SUBJECT_PARTICLES.firstOrNull { raw.length > it.length && raw.endsWith(it) }
            return listOfNotNull(raw, particle?.let { raw.removeSuffix(it) })
        }
    }
}
