package kr.chagok.gleo

import org.json.JSONArray
import org.json.JSONObject

/**
 * Gleo AI 와 주고받는 약속. 함수 이름·인자 이름을 바꾸면 `assets/schema.json` 과 AndroidManifest.xml 도 같이 바꾼다.
 * Gleo 는 `패키지명.함수명` 액션으로 브로드캐스트하고 인자를 [EXTRA_PARAM] 에 JSON 문자열로 싣는다
 * (공식 문서 예시는 함수명만이라 둘 다 받는다). 앱은 [ACTION_RESPONSE] 로 7초 안에 회신한다.
 */
object GleoProtocol {
    /** Gleo AI 앱. 없으면(Gleo 가 없는 차) 말하기 예시를 보여 주지 않는다. AndroidManifest.xml <queries> 에도 있다 */
    const val GLEO_PACKAGE = "ai.umos.ambientai"

    const val SET_TRIP_CATEGORY = "SET_TRIP_CATEGORY"
    const val ADD_TRIP_NOTE = "ADD_TRIP_NOTE"
    const val SET_TRIP_USER = "SET_TRIP_USER"
    val FUNCTIONS = setOf(SET_TRIP_CATEGORY, ADD_TRIP_NOTE, SET_TRIP_USER)

    const val PARAM_CATEGORY = "category"
    const val PARAM_TRIP = "trip"
    const val PARAM_NOTE = "note"
    const val PARAM_NAME = "name"

    const val EXTRA_PARAM = "ACTION_PARAM"
    const val ACTION_RESPONSE = "ai.pleos.playground.intent.action.APP_ACTION_RESPONSE"
    const val EXTRA_PACKAGE = "packageName"
    const val EXTRA_RESPONSE = "response"

    /** Gleo 는 7초가 지나면 취소한다. 그 전에 회신이 나가게 처리는 이만큼만 기다린다 */
    const val HANDLE_TIMEOUT_MILLIS = 6_000L

    /** 액션에서 함수 이름을 꺼낸다. 우리 함수가 아니면 null */
    fun functionOf(action: String?, packageName: String): String? =
        action?.removePrefix("$packageName.")?.takeIf { it in FUNCTIONS }

    /** 인자 JSON. 비었거나 깨졌으면 빈 객체 (함수가 인자 없음으로 답한다) */
    fun paramsOf(json: String?): JSONObject = runCatching { JSONObject(json ?: "{}") }.getOrElse { JSONObject() }
}

/** 어느 운행을 고치나. Gleo 가 못 정하면 null 이고, 앱이 주행 중인지로 정한다 */
enum class GleoTripTarget {
    /** 지금 달리는 운행 */
    CURRENT,

    /** 방금(오늘 마지막으로) 끝난 운행 */
    LAST,

    /** 다음에 출발할 운행 (홈 "다음 출발"·"누가 타나요?") */
    NEXT;

    companion object {
        fun parse(value: String?): GleoTripTarget? = entries.firstOrNull { it.name == value }
    }
}

/**
 * Gleo 에 돌려주는 결과. [message] 는 영어 한 문장으로 쓰고, Gleo 가 한국어로 바꿔 말한다.
 * [data] 에는 위치·메모 같은 운행 내용을 넣지 않는다 (회신은 다른 앱도 받을 수 있는 브로드캐스트다).
 */
data class GleoReply(val status: Status, val message: String, val data: Map<String, String> = emptyMap()) {
    enum class Status(val wire: String) { SUCCESS("success"), ASKING("asking"), FAIL("fail") }

    fun toJson(function: String): JSONObject = JSONObject()
        .put("functionName", function)
        .put("status", status.wire)
        .put("message", message)
        .put("data", JSONArray().put(JSONObject(data)).toString())

    companion object {
        fun success(message: String, data: Map<String, String> = emptyMap()) = GleoReply(Status.SUCCESS, message, data)
        fun asking(message: String) = GleoReply(Status.ASKING, message)
        fun fail(message: String) = GleoReply(Status.FAIL, message)
    }
}
