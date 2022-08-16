package com.test.exam.data.sensors

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener

interface SensorEventSimpleListener : SensorEventListener {

    override fun onSensorChanged(event: SensorEvent) { }
    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) { }

}