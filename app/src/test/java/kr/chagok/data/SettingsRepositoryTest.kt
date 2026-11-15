package kr.chagok.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kr.chagok.domain.FirstRunResult
import kr.chagok.domain.model.Category
import kr.chagok.domain.model.ChagokSettings
import kr.chagok.domain.model.TripPlace
import kr.chagok.domain.model.VehicleInfo
import kr.chagok.domain.model.VehicleUsage
import kr.chagok.testing.TripFixtures.HOME
import kr.chagok.testing.TripFixtures.OFFICE
import kr.chagok.testing.TripFixtures.TODAY
import kr.chagok.testing.testDataStore
import kr.chagok.testing.unreadableDataStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

/** 기록 방식과 등록 정보 (DataStore). 임시 파일에 쓰고 읽는다. 안드로이드 없이 돈다 */
class SettingsRepositoryTest {
    private val directory = Files.createTempDirectory("settings-test").toFile()
    private var job = Job()
    private var repo = open()

    /** 같은 파일을 새로 연다 (앱을 다시 켠 것과 같다). 파일 하나에 DataStore 하나라 앞의 것을 닫고 연다 */
    private fun open() = SettingsRepository(testDataStore(CoroutineScope(Dispatchers.IO + job), directory))

    private suspend fun restartApp() {
        job.cancelAndJoin()
        job = Job()
        repo = open()
    }

    @After
    fun tearDown() = runBlocking {
        job.cancelAndJoin()
        directory.deleteRecursively()
        Unit
    }

    private suspend fun settings(): ChagokSettings = repo.settings.first()

    private fun firstRun(
        usage: VehicleUsage = VehicleUsage.SHARED,
        defaultCategory: Category = Category.PERSONAL,
        home: TripPlace? = HOME,
        office: TripPlace? = OFFICE,
    ) = FirstRunResult(usage, defaultCategory, home, office, people = emptyList())

    @Test
    fun `아무것도 저장하지 않았으면 처음 상태다 (첫 실행 전·업무·혼자 쓰는 차·음성 안내 켬·집 회사 없음)`() = runBlocking {
        assertEquals(ChagokSettings(), settings())
        val defaults = settings()
        assertFalse(defaults.isFirstRunDone)
        assertEquals(Category.BUSINESS, defaults.defaultCategory)
        assertEquals(VehicleUsage.SOLO, defaults.usage)
        assertTrue(defaults.isTripVoiceOn)
        assertNull(defaults.installedOn)
        assertFalse(defaults.hasHomeOffice)
        assertEquals(VehicleInfo(), defaults.vehicle)
    }

    @Test
    fun `설정을 읽지 못하면 기본값으로 보이되 첫 실행은 마친 것으로 본다 (첫 실행을 다시 하면 사람 목록을 덮어쓴다)`() = runBlocking {
        val unreadable = SettingsRepository(unreadableDataStore())

        assertEquals(ChagokSettings(isFirstRunDone = true), unreadable.settings.first())
    }

    @Test
    fun `첫 실행을 마치면 기본 분류·누가 쓰는 차·집·회사가 저장된다`() = runBlocking {
        repo.completeFirstRun(firstRun())

        val saved = settings()
        assertTrue(saved.isFirstRunDone)
        assertEquals(Category.PERSONAL, saved.defaultCategory)
        assertEquals(VehicleUsage.SHARED, saved.usage)
        assertEquals(HOME, saved.home)
        assertEquals(OFFICE, saved.office)
        assertTrue(saved.hasHomeOffice)
    }

    @Test
    fun `장소의 이름·주소·poiId·좌표가 모두 그대로 돌아오고, 좌표가 없는 장소는 좌표 없이 돌아온다`() = runBlocking {
        val full = TripPlace(
            name = "회사",
            address = "성남시 분당구 판교역로 1",
            poiId = "123",
            poiSubId = "4",
            latitude = 37.401,
            longitude = 127.108,
        )
        val addressOnly = TripPlace(address = "성남시 수정구 어딘가 3")

        repo.completeFirstRun(firstRun(home = addressOnly, office = full))

        assertEquals(addressOnly, settings().home)
        assertEquals(full, settings().office)
    }

    @Test
    fun `첫 실행을 다시 하며 집·회사를 비우면 앞서 저장한 집·회사가 지워진다`() = runBlocking {
        repo.completeFirstRun(firstRun())

        repo.completeFirstRun(firstRun(home = null, office = null))

        assertNull(settings().home)
        assertNull(settings().office)
        assertFalse(settings().hasHomeOffice)
    }

    @Test
    fun `좌표가 있던 장소를 좌표 없는 장소로 바꾸면 예전 좌표가 남지 않는다`() = runBlocking {
        repo.completeFirstRun(firstRun(home = HOME))

        repo.completeFirstRun(firstRun(home = TripPlace(address = "새 집 주소")))

        assertEquals(TripPlace(address = "새 집 주소"), settings().home)
    }

    @Test
    fun `설치일은 처음 연 날 한 번만 남고 다음 날 다시 불러도 바뀌지 않는다`() = runBlocking {
        repo.markInstalled(TODAY)

        repo.markInstalled(TODAY.plusDays(1))

        assertEquals(TODAY, settings().installedOn)
    }

    @Test
    fun `설치 식별값은 처음 한 번 생기고 다시 불러도·다시 켜도 그대로다`() = runBlocking {
        assertEquals("", settings().installUid)
        repo.markInstalled(TODAY)
        val first = settings().installUid

        repo.markInstalled(TODAY.plusDays(1))
        restartApp()

        assertTrue(first.isNotEmpty())
        assertEquals(first, settings().installUid)
    }

    @Test
    fun `차량 정보를 저장하면 서식 머리 칸이 바뀌고 차에서 읽은 차대번호는 그대로다`() = runBlocking {
        repo.setVin("KMHXX00XXXX000001")

        repo.setVehicleInfo(
            VehicleInfo(vin = "무시됨", model = "아이오닉 5", plate = "12가 3456", company = "(주)한빛물산", bizNo = "123-45-67890"),
        )

        assertEquals(
            VehicleInfo(
                vin = "KMHXX00XXXX000001",
                model = "아이오닉 5",
                plate = "12가 3456",
                company = "(주)한빛물산",
                bizNo = "123-45-67890",
            ),
            settings().vehicle,
        )
    }

    @Test
    fun `음성 안내를 끄면 꺼진 채로 남고 다시 켤 수 있다`() = runBlocking {
        repo.setTripVoiceOn(false)
        assertFalse(settings().isTripVoiceOn)

        repo.setTripVoiceOn(true)
        assertTrue(settings().isTripVoiceOn)
    }

    @Test
    fun `설정에서 기본 분류와 누가 쓰는 차를 바꿀 수 있다`() = runBlocking {
        repo.completeFirstRun(firstRun(usage = VehicleUsage.SOLO, defaultCategory = Category.BUSINESS))

        repo.setDefaultCategory(Category.PERSONAL)
        repo.setUsage(VehicleUsage.CHAUFFEUR)

        assertEquals(Category.PERSONAL, settings().defaultCategory)
        assertEquals(VehicleUsage.CHAUFFEUR, settings().usage)
        assertEquals("다른 값은 그대로", HOME, settings().home)
    }

    @Test
    fun `앱을 다시 켜도 저장한 설정이 그대로 남는다`() = runBlocking {
        repo.completeFirstRun(firstRun())
        repo.markInstalled(TODAY)
        repo.setTripVoiceOn(false)
        val before = settings()

        restartApp()

        assertEquals(before, settings())
    }
}
