package com.test.exam.data.location

import android.Manifest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow

class LocationPermissionRequester(activity: AppCompatActivity) {

    private lateinit var flow: ProducerScope<Unit>

    private val locationPermissionRequest = activity.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()) { permissions ->

            val fineGranted = permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false)
            val coarseGranted = permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false)

            if(fineGranted && coarseGranted){
                flow.trySend(Unit)
            } else if(coarseGranted) {
                flow.cancel(FineLocationNotGrantedException())
            } else {
                flow.cancel(LocationNotGrantedException())
            }
    }


    fun requestPermission() = callbackFlow {
        flow = this

        locationPermissionRequest.launch(arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION))

        awaitClose()
    }

    class FineLocationNotGrantedException : CancellationException()
    class LocationNotGrantedException : CancellationException()

}