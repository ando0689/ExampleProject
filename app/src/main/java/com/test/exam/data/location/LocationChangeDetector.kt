package com.test.exam.data.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.*

private const val DISTANCE_THRESHOLD = 10f
private const val ACCURACY_THRESHOLD = 18f
private const val MAX_METER_PER_SECOND = 2.8f // For this exam app we assume user will move maximum ~ 10 km/h

class LocationChangeDetector(
    context: Context,
    private val permissionRequester: LocationPermissionRequester,
    private val settingsChecker: LocationSettingsChecker) {

    private lateinit var flow: ProducerScope<Unit>
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    private lateinit var locationCallback: LocationCallback

    private var lastCheckpoint: Location? = null
    private var previousLocation: Location? = null

    fun observeLocationChange() = callbackFlow {
        flow = this
        val permissionFlow = permissionRequester.requestPermission().catch {  cause ->
            if(cause is CancellationException) currentCoroutineContext().cancel(cause)
            else currentCoroutineContext().cancel(CancellationException())
        }
        val settingsFlow = settingsChecker.createLocationRequest().catch {  cause ->
            if(cause is CancellationException) currentCoroutineContext().cancel(cause)
            else currentCoroutineContext().cancel(CancellationException())
        }

        permissionFlow.collect {
            settingsFlow.collect {
                startTrackingLocationUpdates()
            }
        }

        awaitClose()
    }

    @SuppressLint("MissingPermission")
    private fun startTrackingLocationUpdates(){
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let(::analyzeLocationData)
            }
        }

        fusedLocationClient.requestLocationUpdates(
            settingsChecker.locationRequest,
            locationCallback,
            Looper.getMainLooper())
    }

    private fun analyzeLocationData(currentLocation: Location){
        val accuracy = currentLocation.accuracy

        if(accuracy <= ACCURACY_THRESHOLD && !isUnexpectedLocationJump(currentLocation)) {
            if (lastCheckpoint == null){
                lastCheckpoint = currentLocation
            } else {
                val distance = currentLocation.distanceTo(lastCheckpoint)
                if (distance >= DISTANCE_THRESHOLD) {
                    lastCheckpoint = currentLocation
                    flow.trySend(Unit)
                }
            }
        }

        previousLocation = currentLocation
    }

    // A quick and naive attempt to prevent unexpected location jumps due to GPS noise
    // For production application we would need much better approach to reduce noise
    // But for this exam task it can be fine
    private fun isUnexpectedLocationJump(currentLocation: Location): Boolean {
        if(currentLocation.bearing == 0f) return true
        if(currentLocation.speed == 0f) return true
        if(previousLocation == null) return true

        val seconds = (currentLocation.time - previousLocation!!.time) / 1000f
        val distance = currentLocation.distanceTo(previousLocation)

        val meterPerSecond = distance / seconds

        return meterPerSecond > MAX_METER_PER_SECOND
    }

    fun stopObserving() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        flow.cancel(CancellationException("stoppedObserving"))
    }
}