package com.test.exam.data.location

import android.app.Activity
import android.content.Intent
import android.content.IntentSender
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow

private const val REQUEST_CHECK_SETTINGS = 101
private const val INTERVAL = 2000L
private const val FASTEST_INTERVAL = 1000L

class LocationSettingsChecker(private val activity: AppCompatActivity) {

    private lateinit var flow: ProducerScope<Unit>
    lateinit var locationRequest: LocationRequest

    fun createLocationRequest() = callbackFlow {
        flow = this
        locationRequest = LocationRequest.create().apply {
            interval = INTERVAL
            fastestInterval = FASTEST_INTERVAL
            priority = Priority.PRIORITY_HIGH_ACCURACY
        }

        val builder = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)

        val client = LocationServices.getSettingsClient(activity)
        val task: Task<LocationSettingsResponse> = client.checkLocationSettings(builder.build())

        task.addOnSuccessListener {
            flow.trySend(Unit)
        }

        task.addOnFailureListener { exception ->
            if (exception is ResolvableApiException) {
                try {
                    exception.startResolutionForResult(activity, REQUEST_CHECK_SETTINGS)
                } catch (sendEx: IntentSender.SendIntentException) {
                    flow.cancel(LocationSettingsNotGoodException())
                }
            } else {
                flow.cancel(LocationSettingsNotGoodException())
            }
        }

        awaitClose()
    }

    fun handleActivityResult(requestCode: Int, resultCode: Int, data: Intent?){
        if(requestCode == REQUEST_CHECK_SETTINGS) {
            if(resultCode == Activity.RESULT_OK) {
                flow.trySend(Unit)
            } else {
                flow.cancel(LocationSettingsNotGoodException())
            }
        }
    }

    class LocationSettingsNotGoodException : CancellationException()
}