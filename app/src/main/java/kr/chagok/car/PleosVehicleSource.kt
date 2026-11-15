package kr.chagok.car

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.listener.DistanceDrivenListener
import ai.pleos.playground.vehicle.listener.EvBatteryStateOfChargeListener
import ai.pleos.playground.vehicle.listener.FuelPercentListener
import ai.pleos.playground.vehicle.listener.SeatOccupancyListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Pleos Vehicle SDK 로 누적거리·동승석 착석·연료·배터리 잔량·차대번호를 받는다. 서비스 연결이 비동기라 바로 등록하면 실패할 수 있어
 * 잠시 뒤 등록하고, 실패하면 리스너마다 따로 다시 등록한다.
 * 누적거리는 값이 그대로여도 약 4초마다 다시 오는데, 같은 값은 StateFlow 가 걸러 준다.
 * 기어는 SDK 가 아니라 AAOS 표준 API 로 받는다 ([AaosGearSource]).
 */
class PleosVehicleSource(context: Context) : VehicleSource {
    private val vehicle = Vehicle(context)
    private val gearSource = AaosGearSource(context)
    private val handler = Handler(Looper.getMainLooper())
    private val _odometerKm = MutableStateFlow<Double?>(null)
    private val seats = MutableStateFlow<Map<SeatValue, Boolean>>(emptyMap())
    private val _passengerCount = MutableStateFlow<Int?>(null)
    private val _fuelPercent = MutableStateFlow<Int?>(null)
    private val _batteryPercent = MutableStateFlow<Int?>(null)
    private var isStarted = false
    private val registrationStarted = CompletableDeferred<Unit>()

    override val odometerKm: StateFlow<Double?> = _odometerKm.asStateFlow()
    override val gear: StateFlow<Gear> = gearSource.gear
    override val passengerCount: StateFlow<Int?> = _passengerCount.asStateFlow()
    override val fuelPercent: StateFlow<Int?> = _fuelPercent.asStateFlow()
    override val batteryPercent: StateFlow<Int?> = _batteryPercent.asStateFlow()

    private val odometerListener: DistanceDrivenListener = object : DistanceDrivenListener {
        override fun onDistanceDrivenUpdated(value: Float?) {
            odometerRegistration.markWorking()
            value?.toKmOrNull()?.let { _odometerKm.value = it }
        }

        override fun onFailed(e: Exception) = odometerRegistration.retry(e)
    }

    private val seatWatches = PASSENGER_SEATS.map(::SeatWatch)

    private val fuelListener: FuelPercentListener = object : FuelPercentListener {
        override fun onFuelPercentUpdated(value: Int?) {
            fuelRegistration.markWorking()
            value?.let { _fuelPercent.value = it }
        }

        override fun onFailed(e: Exception) = fuelRegistration.retry(e)
    }

    private val batteryListener: EvBatteryStateOfChargeListener = object : EvBatteryStateOfChargeListener {
        override fun onEvBatteryStateOfChargeUpdated(value: Int?) {
            batteryRegistration.markWorking()
            value?.let { _batteryPercent.value = it }
        }

        override fun onFailed(e: Exception) = batteryRegistration.retry(e)
    }

    // 차종에 따라 둘 중 하나는 등록이 실패한다 (전기차의 연료, 내연기관차의 배터리). 몇 번 다시 해 보고 그친다
    private val fuelRegistration: Registration = Registration(
        name = "연료 잔량",
        register = { vehicle.getFuel().registerFuelPercent(fuelListener) },
        unregister = { vehicle.getFuel().unregisterFuelPercent(fuelListener) },
    )

    private val batteryRegistration: Registration = Registration(
        name = "배터리 SOC",
        register = { vehicle.getEvBattery().registerEvBatteryStateOfCharge(batteryListener) },
        unregister = { vehicle.getEvBattery().unregisterEvBatteryStateOfCharge(batteryListener) },
    )

    private val odometerRegistration: Registration = Registration(
        name = "누적거리",
        register = { vehicle.getOdometer().registerDistanceDriven(odometerListener) },
        unregister = { vehicle.getOdometer().unregisterDistanceDriven(odometerListener) },
    )

    override fun start() {
        if (isStarted) return
        isStarted = true
        vehicle.initialize()
        gearSource.start()
        handler.postDelayed({
            odometerRegistration.run()
            seatWatches.forEach { it.registration.run() }
            fuelRegistration.run()
            batteryRegistration.run()
            readCurrentValues()
            registrationStarted.complete(Unit)
        }, CarConfig.REGISTER_DELAY_MILLIS)
    }

    override fun stop() {
        if (!isStarted) return
        isStarted = false
        handler.removeCallbacksAndMessages(null)
        odometerRegistration.release()
        gearSource.stop()
        seatWatches.forEach { it.registration.release() }
        fuelRegistration.release()
        batteryRegistration.release()
        vehicle.release()
    }

    // 연결 전에 물으면 실패하거나 답이 없다. 차대번호는 직접 넣는 칸이 없어 놓치면 서식에서 빈칸으로 남는다
    override suspend fun readVin(): String? {
        registrationStarted.await()
        return requestVin()
    }

    private suspend fun requestVin(): String? = suspendCancellableCoroutine { continuation ->
        runCatching {
            vehicle.getCarInfo().getVINInfo(
                // 에뮬레이터는 "invalid" 를 준다. 17자리가 아니면 못 읽은 것으로 본다
                { vin ->
                    if (continuation.isActive) continuation.resume(vin?.takeIf { it.length == CarConfig.VIN_LENGTH })
                },
                { error ->
                    Log.w(TAG, "차대번호 읽기 실패", error)
                    if (continuation.isActive) continuation.resume(null)
                },
            )
        }.onFailure {
            Log.w(TAG, "차대번호 요청 실패", it)
            if (continuation.isActive) continuation.resume(null)
        }
    }

    // 리스너가 첫 값을 늦게 줄 때를 대비해 한 번 직접 읽는다. 등록처럼 서비스 연결 전이면 던질 수 있는데,
    // 메인 스레드에서 돌아 그대로 두면 앱이 죽는다
    private fun readCurrentValues() {
        runCatching {
            vehicle.getOdometer().getCurrentDistanceDriven(
                { value -> value?.toKmOrNull()?.let { _odometerKm.value = it } },
                { error -> Log.w(TAG, "누적거리 읽기 실패", error) },
            )
        }.onFailure { Log.w(TAG, "누적거리 읽기 요청 실패", it) }
        seatWatches.forEach(SeatWatch::readCurrent)
        // 배터리 SOC 는 읽기 API 가 없고 구독만 된다. 연료만 한 번 읽는다
        runCatching {
            vehicle.getFuel().getFuelPercent(
                { value -> value?.let { _fuelPercent.value = it } },
                { error -> Log.w(TAG, "연료 잔량 읽기 실패", error) },
            )
        }.onFailure { Log.w(TAG, "연료 잔량 읽기 요청 실패", it) }
    }

    private fun onSeat(seat: SeatValue, isOccupied: Boolean?) {
        if (isOccupied == null) return
        val latest = seats.updateAndGet { it + (seat to isOccupied) }
        _passengerCount.value = latest.values.count { it }
    }

    /**
     * 동승석 한 자리의 착석. 에뮬레이터는 바꾼 값이 20초 넘게 늦게 오기도 한다.
     * SDK 는 원시 값 1 을 true(앉음)로 바꾸는데, Virtual Console 은 비었음을 1, 앉음을 2 로 보낸다(AAOS 표준).
     * 그래서 에뮬레이터에서는 거꾸로 보인다.
     * TODO: 실차에서 원시 값이 어느 쪽인지 확인하고, 표준 쪽이면 SDK 대신 원시 값으로 읽는다
     */
    private inner class SeatWatch(private val seat: SeatValue) : SeatOccupancyListener {
        private val area = SeatArea(seat)

        val registration = Registration(
            name = "착석 $seat",
            register = { vehicle.getSeat().registerSeatOccupancy(area, this@SeatWatch) },
            unregister = { vehicle.getSeat().unregisterSeatOccupancy(area, this@SeatWatch) },
        )

        override fun onSeatOccupancyUpdated(value: Boolean?) {
            registration.markWorking()
            onSeat(seat, value)
        }

        override fun onFailed(e: Exception) = registration.retry(e)

        fun readCurrent() {
            runCatching {
                vehicle.getSeat().getCurrentSeatOccupancy(
                    area,
                    { onSeat(seat, it) },
                    { error -> Log.w(TAG, "착석 $seat 읽기 실패", error) },
                )
            }.onFailure { Log.w(TAG, "착석 $seat 읽기 요청 실패", it) }
        }
    }

    /**
     * 리스너 하나의 등록·재시도. 하나가 실패했다고 전부 다시 등록하면 중복 등록된다.
     * 값이 오면 동작하는 것이라 시도 횟수를 0으로 되돌린다. 안 그러면 시동 동안 실패가 쌓여 상한에 닿은 뒤로 다시 등록하지 않는다
     */
    private inner class Registration(
        private val name: String,
        private val register: () -> Unit,
        private val unregister: () -> Unit,
    ) {
        private var attempt = 0

        fun run() {
            attempt++
            runCatching(unregister) // 다시 등록할 때 중복 방지
            runCatching(register).onFailure(::retry)
        }

        fun retry(error: Throwable) {
            Log.w(TAG, "$name 등록 실패 (시도 $attempt)", error)
            if (attempt < CarConfig.REGISTER_MAX_ATTEMPTS) handler.postDelayed(::run, CarConfig.REGISTER_DELAY_MILLIS)
        }

        fun markWorking() {
            attempt = 0
        }

        fun release() {
            runCatching(unregister)
            attempt = 0
        }
    }

    private companion object {
        const val TAG = "PleosVehicleSource"

        // 운전석(1열 왼쪽)을 뺀 자리. 2열 가운데는 차종마다 센서가 없을 수 있어 보지 않는다
        val PASSENGER_SEATS = listOf(SeatValue.ROW_1_RIGHT, SeatValue.ROW_2_LEFT, SeatValue.ROW_2_RIGHT)

        // Float 를 그대로 Double 로 바꾸면 15847.8 이 15847.7998… 이 된다. 차가 준 값(글자) 그대로 옮긴다.
        // 0 이하는 아직 값이 없는 것으로 본다. 에뮬레이터는 재부팅하면 0 으로 돌아가고, 부팅 직후 0 이 잠깐 올 수 있다.
        // 그대로 쓰면 0km 에서 출발해 나중에 맞춘 누적거리만큼의 운행 하나가 생긴다
        fun Float.toKmOrNull(): Double? = takeIf { it > 0f }?.toString()?.toDouble()
    }
}
