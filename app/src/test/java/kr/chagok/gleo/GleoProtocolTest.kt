package kr.chagok.gleo

import kr.chagok.domain.PersonMatcher
import kr.chagok.domain.model.Category
import kr.chagok.domain.model.Person
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Gleo 와 주고받는 값: 액션 이름, 인자, 회신 JSON, 이름 맞추기 */
@RunWith(RobolectricTestRunner::class)
class GleoProtocolTest {

    @Test
    fun `액션이 패키지명을 붙여 와도 함수명만 와도 알아듣고, 우리 함수가 아니면 무시한다`() {
        assertEquals(
            GleoProtocol.SET_TRIP_CATEGORY,
            GleoProtocol.functionOf("kr.chagok.SET_TRIP_CATEGORY", PACKAGE),
        )
        assertEquals(GleoProtocol.ADD_TRIP_NOTE, GleoProtocol.functionOf("ADD_TRIP_NOTE", PACKAGE))
        assertNull(GleoProtocol.functionOf("kr.chagok.DELETE_ALL", PACKAGE))
        assertNull(GleoProtocol.functionOf(null, PACKAGE))
    }

    @Test
    fun `깨진 인자는 빈 인자로 본다`() {
        assertEquals(0, GleoProtocol.paramsOf("{not json").length())
        assertEquals(0, GleoProtocol.paramsOf(null).length())
        assertEquals("PERSONAL", GleoProtocol.paramsOf("""{"category":"PERSONAL"}""").getString("category"))
    }

    @Test
    fun `회신은 함수명·상태·문장·data(JSON 배열 문자열)로 나간다`() {
        val json = GleoReply.success("Done.", mapOf("category" to "PERSONAL")).toJson(GleoProtocol.SET_TRIP_CATEGORY)

        assertEquals("SET_TRIP_CATEGORY", json.getString("functionName"))
        assertEquals("success", json.getString("status"))
        assertEquals("Done.", json.getString("message"))
        assertEquals("PERSONAL", JSONArray(json.getString("data")).getJSONObject(0).getString("category"))
    }

    @Test
    fun `대상 운행 값은 정해진 것만 받고 나머지는 앱이 정하게 둔다`() {
        assertEquals(GleoTripTarget.LAST, GleoTripTarget.parse("LAST"))
        assertNull(GleoTripTarget.parse(""))
        assertNull(GleoTripTarget.parse("YESTERDAY"))
    }

    @Test
    fun `분류는 업무·출퇴근·개인을 받고 나머지는 못 알아들은 것으로 본다`() {
        assertEquals(Category.PERSONAL, GleoCommands.parseCategory("personal"))
        assertEquals(Category.BUSINESS, GleoCommands.parseCategory(" BUSINESS "))
        assertEquals(Category.COMMUTE, GleoCommands.parseCategory("COMMUTE"))
        assertNull(GleoCommands.parseCategory("HOLIDAY"))
        assertNull(GleoCommands.parseCategory(JSONObject().optString("category")))
    }

    @Test
    fun `이름은 띄어쓰기·님·씨를 무시하고, 똑같은 이름이 있으면 그 사람만, 이름 없는 사람은 부를 수 없다`() {
        val people = listOf(
            Person(1, "박정훈", initial = "박"),
            Person(2, "박정훈이", initial = "박"),
            Person(3, "", initial = "3"),
        )

        assertEquals(listOf(1L), PersonMatcher.match("박 정훈 님", people).map(Person::id))
        assertEquals(listOf(1L, 2L), PersonMatcher.match("정훈", people).map(Person::id))
        assertEquals(emptyList<Long>(), PersonMatcher.match("  ", people).map(Person::id))
        assertEquals(emptyList<Long>(), PersonMatcher.match("나", people).map(Person::id))
    }

    private companion object {
        const val PACKAGE = "kr.chagok"
    }
}
