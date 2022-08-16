package com.test.exam.data.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.callbackFlow
import kotlin.math.abs

private const val MAX_READING_COUNT = 10
private const val THRESHOLD = 0.5f

class RotationDetector(context: Context): SensorEventSimpleListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val gyroscope: Sensor? by lazy {
        sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    }

    private lateinit var flow: ProducerScope<Rotation>

    private val readings = mutableListOf<Reading>()

    fun observeRotation() = callbackFlow {
        if(gyroscope == null) {
            currentCoroutineContext().cancel(RotationDetectorException("gyroscope error"))
        }

        flow = this
        sensorManager.registerListener(this@RotationDetector, gyroscope, SensorManager.SENSOR_DELAY_GAME)

        awaitClose()
    }

    fun stopObserving(){
        if(gyroscope == null) return
        sensorManager.unregisterListener(this)
        flow.cancel(CancellationException("stoppedObserving"))
    }

    override fun onSensorChanged(event: SensorEvent) {
        val x: Float = event.values[0]
        val y: Float = event.values[1]
        val z: Float = event.values[2]

        readings.add(Reading(x, y, z))

        if(readings.size >= MAX_READING_COUNT){
            val avgX = readings.map { it.x }.average()
            val avgY = readings.map { it.y }.average()
            val avgZ = readings.map { it.z }.average()

            if(abs(avgX) >= THRESHOLD && isDirectionX(x = avgX, y = avgY, z = avgZ)) {
                flow.trySend(Rotation.AxisX(avgX))
            }

            if(abs(avgZ) >= THRESHOLD && isDirectionZ(x = avgX, y = avgY, z = avgZ)) {
                flow.trySend(Rotation.AxisZ(avgZ))
            }
            readings.clear()
        }
    }


    private fun isDirectionX(x: Double, y: Double, z: Double): Boolean {
        return abs(x) > abs(y) && abs(x) > abs(z)
    }

    private fun isDirectionZ(x: Double, y: Double, z: Double): Boolean {
        return abs(z) > abs(x) && abs(z) > abs(y)
    }

    sealed class Rotation {
        data class AxisX(val amount: Double): Rotation()
        data class AxisZ(val amount: Double): Rotation()
    }

    private data class Reading(val x: Float, val y: Float, val z: Float)

    class RotationDetectorException(message: String) : CancellationException(message)
}