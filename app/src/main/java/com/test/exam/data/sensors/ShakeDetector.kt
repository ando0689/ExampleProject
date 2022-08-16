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

private const val ACCELERATION_THRESHOLD = 12
private const val ACCELERATION_ACCUMULATE_TIME = 500000000
private const val SHAKING_ACCUMULATE_TIME = 1200000000
private const val MIN_READING_COUNT = 10
private const val SHAKING_RATIO = 0.5
private const val SHAKING_COUNT_TO_NOTIFY = 3

class ShakeDetector(context: Context) : SensorEventSimpleListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? by lazy {
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }

    private lateinit var flow: ProducerScope<Unit>

    private var readingsList = mutableListOf<SensorReading>()
    private var shakingList = mutableListOf<Long>()

    fun observeShaking() = callbackFlow {
        if(accelerometer == null) {
            currentCoroutineContext().cancel(ShakeDetectorException("accelerometer error"))
        }

        flow = this
        sensorManager.registerListener(this@ShakeDetector, accelerometer, SensorManager.SENSOR_DELAY_GAME)

        awaitClose()
    }

    fun stopObserving(){
        if(accelerometer == null) return
        sensorManager.unregisterListener(this)
        flow.cancel(CancellationException("stoppedObserving"))
    }

    override fun onSensorChanged(event: SensorEvent) {
        val reading = SensorReading(event.timestamp, isAccelerating(event))

        cleanupOldReadings(reading)
        readingsList.add(reading)

        if (isShaking()) {
            readingsList.clear()
            cleanupOldShaking(event.timestamp)
            shakingList.add(event.timestamp)

            if(shouldNotifyShaking()) {
                shakingList.clear()
                flow.trySend(Unit)
            }
        }
    }

    private fun shouldNotifyShaking(): Boolean {
        return shakingList.size >= SHAKING_COUNT_TO_NOTIFY
    }

    private fun isAccelerating(event: SensorEvent): Boolean {
        val ax = event.values[0]
        val ay = event.values[1]
        val az = event.values[2]

        val magnitudeSquared = ax * ax + ay * ay + az * az
        return magnitudeSquared > ACCELERATION_THRESHOLD * ACCELERATION_THRESHOLD
    }

    private fun isShaking(): Boolean {
        val readingsSize = readingsList.size
        if(readingsSize < MIN_READING_COUNT) return false
        val acceleratingCount = readingsList.count { it.accelerating }
        return acceleratingCount.toDouble() / readingsSize.toDouble() >= SHAKING_RATIO
    }

    private fun cleanupOldShaking(lastShakingTimestamp: Long){
        shakingList = shakingList.filter { shakingTimestamp ->
            lastShakingTimestamp - shakingTimestamp < SHAKING_ACCUMULATE_TIME
        }.toMutableList()
    }

    private fun cleanupOldReadings(lastReading: SensorReading){
        readingsList = readingsList.filter { reading ->
            lastReading.timestamp - reading.timestamp < ACCELERATION_ACCUMULATE_TIME
        }.toMutableList()
    }

    data class SensorReading(
        val timestamp: Long,
        val accelerating: Boolean)

    class ShakeDetectorException(message: String) : CancellationException(message)
}