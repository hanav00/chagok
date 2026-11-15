package kr.chagok.car

import android.car.Car
import android.car.VehicleGear
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 기어 레버 위치. Pleos 문서는 기어 같은 표준 차량 값을 AAOS Car API 로 읽게 하고, Vehicle SDK 는 이를 따로 주지 않는다.
 * 그래서 기어만 CarPropertyManager 의 GEAR_SELECTION 으로 받는다 (권한 CAR_POWERTRAIN, normal).
 */
class AaosGearSource(private val context: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private val _gear = MutableStateFlow(Gear.UNKNOWN)
    private var car: Car? = null
    private var manager: CarPropertyManager? = null
    private var attempt = 0

    val gear: StateFlow<Gear> = _gear.asStateFlow()

    private val callback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) = onValue(value)

        override fun onErrorEvent(propertyId: Int, areaId: Int) {
            Log.w(TAG, "기어 구독 오류 (area $areaId)")
        }
    }

    fun start() {
        if (car != null) return
        attempt++
        val connected = runCatching { Car.createCar(context) }.getOrNull()
        val found = connected?.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager
        if (found == null) {
            Log.w(TAG, "차량 서비스 연결 실패 (시도 $attempt)")
            runCatching { connected?.disconnect() }
            if (attempt < CarConfig.REGISTER_MAX_ATTEMPTS) handler.postDelayed(::start, CarConfig.REGISTER_DELAY_MILLIS)
            return
        }
        car = connected
        manager = found
        attempt = 0
        // 구독하면 현재 값이 한 번 오지만, 늦을 때를 대비해 먼저 직접 읽는다
        runCatching { found.getProperty<Any>(VehiclePropertyIds.GEAR_SELECTION, GLOBAL_AREA) }
            .onSuccess { it?.let(::onValue) }
            .onFailure { Log.w(TAG, "기어 읽기 실패", it) }
        runCatching {
            found.registerCallback(callback, VehiclePropertyIds.GEAR_SELECTION, CarPropertyManager.SENSOR_RATE_ONCHANGE)
        }.onFailure { Log.w(TAG, "기어 구독 실패", it) }
    }

    fun stop() {
        handler.removeCallbacksAndMessages(null)
        runCatching { manager?.unregisterCallback(callback) }
        runCatching { car?.disconnect() }
        manager = null
        car = null
        attempt = 0
    }

    private fun onValue(value: CarPropertyValue<*>) {
        if (value.status != CarPropertyValue.STATUS_AVAILABLE) return
        val raw = value.value as? Int ?: return
        _gear.value = raw.toGear()
    }

    private companion object {
        const val TAG = "AaosGearSource"
        const val GLOBAL_AREA = 0

        fun Int.toGear(): Gear = when (this) {
            VehicleGear.GEAR_UNKNOWN -> Gear.UNKNOWN
            VehicleGear.GEAR_PARK -> Gear.PARK
            VehicleGear.GEAR_REVERSE -> Gear.REVERSE
            VehicleGear.GEAR_NEUTRAL -> Gear.NEUTRAL
            else -> Gear.DRIVE // D 와 수동 1~9단
        }
    }
}
