package com.logiclabs.hardware.hal

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class HardwareSensorState(
    val lightLux: Float = 100f,
    val isLightTriggerHigh: Boolean = false,
    val proximityCm: Float = 5.0f,
    val isProximityNear: Boolean = false,
    val tiltAngleDegrees: Float = 0f,
    val isTiltTriggerHigh: Boolean = false,
    val audioLevelDb: Float = 30f,
    val isAudioTriggerHigh: Boolean = false,
    val isHardwareAvailable: Boolean = false
)

class SensorHalManager(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val lightSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_LIGHT)
    private val proximitySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)
    private val accelSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val _sensorState = MutableStateFlow(HardwareSensorState(isHardwareAvailable = sensorManager != null))
    val sensorState: StateFlow<HardwareSensorState> = _sensorState.asStateFlow()

    fun startListening() {
        lightSensor?.let { sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        proximitySensor?.let { sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        accelSensor?.let { sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    fun stopListening() {
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        when (event.sensor.type) {
            Sensor.TYPE_LIGHT -> {
                val lux = event.values[0]
                _sensorState.value = _sensorState.value.copy(
                    lightLux = lux,
                    isLightTriggerHigh = (lux > 150f)
                )
            }
            Sensor.TYPE_PROXIMITY -> {
                val dist = event.values[0]
                val isNear = dist < 2.0f
                _sensorState.value = _sensorState.value.copy(
                    proximityCm = dist,
                    isProximityNear = isNear
                )
            }
            Sensor.TYPE_ACCELEROMETER -> {
                val x = event.values[0]
                val y = event.values[1]
                val angle = Math.toDegrees(Math.atan2(y.toDouble(), x.toDouble())).toFloat()
                _sensorState.value = _sensorState.value.copy(
                    tiltAngleDegrees = angle,
                    isTiltTriggerHigh = (angle > 45f)
                )
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    // Fallback manual setters for test/emulator without hardware sensors
    fun setVirtualLight(lux: Float) {
        _sensorState.value = _sensorState.value.copy(
            lightLux = lux,
            isLightTriggerHigh = (lux > 150f)
        )
    }

    fun setVirtualTilt(angleDegrees: Float) {
        _sensorState.value = _sensorState.value.copy(
            tiltAngleDegrees = angleDegrees,
            isTiltTriggerHigh = (angleDegrees > 45f)
        )
    }
}
