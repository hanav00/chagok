package kr.chagok.data

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kr.chagok.domain.FirstRunResult
import kr.chagok.domain.model.Category
import kr.chagok.domain.model.ChagokSettings
import kr.chagok.domain.model.RecordUid
import kr.chagok.domain.model.TripPlace
import kr.chagok.domain.model.VehicleInfo
import kr.chagok.domain.model.VehicleUsage
import java.io.IOException
import java.time.LocalDate

/** 기록 방식과 등록 정보 (DataStore). 운행 기록은 [TripRepository] */
class SettingsRepository(store: DataStore<Preferences>) {
    private val prefs = PreferenceStore(store)

    /**
     * 읽기 실패는 로그를 남기고 기본값으로 보여 준다 (화면 전체가 죽는 것보다 낫다).
     * 다만 첫 실행은 마친 것으로 본다. 쓰던 차에서 첫 실행 화면이 다시 뜨고 "시작하기"를 누르면 사람 목록을 새로 덮어쓴다.
     */
    val settings: Flow<ChagokSettings> = prefs.data
        .map(::toSettings)
        .catch { error ->
            if (error !is IOException) throw error
            Log.e(TAG, "설정 읽기 실패", error)
            emit(ChagokSettings(isFirstRunDone = true))
        }
        .distinctUntilChanged()

    suspend fun setDefaultCategory(category: Category) = update { it[Keys.DEFAULT_CATEGORY] = category.name }

    suspend fun setUsage(usage: VehicleUsage) = update { it[Keys.USAGE] = usage.name }

    suspend fun setTripVoiceOn(isOn: Boolean) = update { it[Keys.TRIP_VOICE] = isOn }

    suspend fun setVin(vin: String) = update { it[Keys.VIN] = vin }

    /** 서식 머리에 들어가는 직접 입력 값 (차종·번호판·법인명·사업자번호). 차대번호는 차에서 읽는다 */
    suspend fun setVehicleInfo(info: VehicleInfo) = update {
        it[Keys.MODEL] = info.model
        it[Keys.PLATE] = info.plate
        it[Keys.COMPANY] = info.company
        it[Keys.BIZ_NO] = info.bizNo
    }

    /** 집·회사 (출퇴근을 나누는 데만 쓴다. 둘 다 있어야 나눈다) */
    suspend fun setPlaces(home: TripPlace, office: TripPlace) = update {
        it.writePlace(Keys.HOME, home)
        it.writePlace(Keys.OFFICE, office)
    }

    /** 운행 분류를 말이나 연습으로 바꿔 봤다 (한 번 남기면 그대로) */
    suspend fun markChangeTried() = update { it[Keys.CHANGE_TRIED] = true }

    /** 홈 시작 가이드를 닫았다 */
    suspend fun dismissGuide() = update { it[Keys.GUIDE_DISMISSED] = true }

    suspend fun setHome(place: TripPlace) = update { it.writePlace(Keys.HOME, place) }

    suspend fun setOffice(place: TripPlace) = update { it.writePlace(Keys.OFFICE, place) }

    /**
     * 앱을 처음 연 날(사용법 안내 기간)과 이 설치의 식별값(내보내기·백업 묶음 머리)을 한 번만 남긴다.
     * 둘을 따로 보는 것은 설치일만 남아 있는 앱에도 식별값이 생기게 하려는 것이다
     */
    suspend fun markInstalled(today: LocalDate) = update {
        if (it[Keys.INSTALLED_ON] == null) it[Keys.INSTALLED_ON] = today.toEpochDay()
        if (it[Keys.INSTALL_UID] == null) it[Keys.INSTALL_UID] = RecordUid.new()
    }

    /** 첫 실행에서 고른 값을 반영한다. 사람은 [PeopleRepository.replaceAll] 이 따로 저장한다 */
    suspend fun completeFirstRun(result: FirstRunResult) = update {
        it[Keys.FIRST_RUN_DONE] = true
        it[Keys.DEFAULT_CATEGORY] = result.defaultCategory.name
        it[Keys.USAGE] = result.usage.name
        it.writePlace(Keys.HOME, result.home)
        it.writePlace(Keys.OFFICE, result.office)
    }

    private suspend fun update(block: (MutablePreferences) -> Unit) {
        writeOrThrow(TAG, "설정 저장") { prefs.edit(block) }
    }

    private fun toSettings(prefs: Preferences) = ChagokSettings(
        isFirstRunDone = prefs[Keys.FIRST_RUN_DONE] ?: false,
        // 모르는 이름(나중에 열거형 이름을 바꾼 경우)은 기본값으로. valueOf 는 던져서 화면이 죽는다
        defaultCategory = Category.entries.firstOrNull { it.name == prefs[Keys.DEFAULT_CATEGORY] } ?: Category.BUSINESS,
        usage = VehicleUsage.entries.firstOrNull { it.name == prefs[Keys.USAGE] } ?: VehicleUsage.SOLO,
        isTripVoiceOn = prefs[Keys.TRIP_VOICE] ?: true,
        installedOn = prefs[Keys.INSTALLED_ON]?.let(LocalDate::ofEpochDay),
        installUid = prefs[Keys.INSTALL_UID].orEmpty(),
        home = prefs.readPlace(Keys.HOME),
        office = prefs.readPlace(Keys.OFFICE),
        isChangeTried = prefs[Keys.CHANGE_TRIED] ?: false,
        isGuideDismissed = prefs[Keys.GUIDE_DISMISSED] ?: false,
        vehicle = VehicleInfo(
            vin = prefs[Keys.VIN].orEmpty(),
            model = prefs[Keys.MODEL].orEmpty(),
            plate = prefs[Keys.PLATE].orEmpty(),
            company = prefs[Keys.COMPANY].orEmpty(),
            bizNo = prefs[Keys.BIZ_NO].orEmpty(),
        ),
    )

    // 장소 한 점은 접두사가 같은 키 여러 개로 펼쳐 저장한다. 이름 키가 없으면 장소 없음
    private fun Preferences.readPlace(keys: PlaceKeys): TripPlace? {
        val name = this[keys.name] ?: return null
        return TripPlace(
            name = name,
            address = this[keys.address].orEmpty(),
            poiId = this[keys.poiId].orEmpty(),
            poiSubId = this[keys.poiSubId].orEmpty(),
            latitude = this[keys.latitude],
            longitude = this[keys.longitude],
        )
    }

    private fun MutablePreferences.writePlace(keys: PlaceKeys, place: TripPlace?) {
        keys.all.forEach { remove(it) }
        if (place == null) return
        this[keys.name] = place.name
        this[keys.address] = place.address
        this[keys.poiId] = place.poiId
        this[keys.poiSubId] = place.poiSubId
        place.latitude?.let { this[keys.latitude] = it }
        place.longitude?.let { this[keys.longitude] = it }
    }

    private class PlaceKeys(prefix: String) {
        val name = stringPreferencesKey("${prefix}_name")
        val address = stringPreferencesKey("${prefix}_address")
        val poiId = stringPreferencesKey("${prefix}_poi_id")
        val poiSubId = stringPreferencesKey("${prefix}_poi_sub_id")
        val latitude = doublePreferencesKey("${prefix}_latitude")
        val longitude = doublePreferencesKey("${prefix}_longitude")
        val all: List<Preferences.Key<*>> = listOf(name, address, poiId, poiSubId, latitude, longitude)
    }

    private object Keys {
        val FIRST_RUN_DONE = booleanPreferencesKey("first_run_done")
        val CHANGE_TRIED = booleanPreferencesKey("change_tried")
        val GUIDE_DISMISSED = booleanPreferencesKey("guide_dismissed")
        val DEFAULT_CATEGORY = stringPreferencesKey("default_category")
        val USAGE = stringPreferencesKey("usage")
        val TRIP_VOICE = booleanPreferencesKey("arrival_voice") // 도착 안내만 있던 때의 이름. 저장된 값을 잃지 않게 그대로 둔다
        val INSTALLED_ON = longPreferencesKey("installed_on")
        val INSTALL_UID = stringPreferencesKey("install_uid")
        val HOME = PlaceKeys("home")
        val OFFICE = PlaceKeys("office")
        val VIN = stringPreferencesKey("vin")
        val MODEL = stringPreferencesKey("model")
        val PLATE = stringPreferencesKey("plate")
        val COMPANY = stringPreferencesKey("company")
        val BIZ_NO = stringPreferencesKey("biz_no")
    }

    private companion object {
        const val TAG = "SettingsRepository"
    }
}
