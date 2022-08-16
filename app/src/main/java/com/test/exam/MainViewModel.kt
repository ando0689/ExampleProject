package com.test.exam

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.test.exam.data.location.LocationChangeDetector
import com.test.exam.data.location.LocationPermissionRequester.*
import com.test.exam.data.location.LocationSettingsChecker.*
import com.test.exam.data.sensors.RotationDetector
import com.test.exam.data.sensors.RotationDetector.*
import com.test.exam.data.sensors.ShakeDetector
import com.test.exam.data.sensors.ShakeDetector.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val INITIAL_DELAY = 4000L

class MainViewModel(private val shakeDetector: ShakeDetector, private val rotationDetector: RotationDetector, private val locationChangeDetector: LocationChangeDetector) : ViewModel() {

    private val _action = MutableSharedFlow<Action?>()
    val action = _action.asSharedFlow()

    private val _error = MutableStateFlow<Error?>(null)
    val error = _error.asStateFlow()

    var playerCurrentPosition: Long? = null

    fun start() = viewModelScope.apply {

        launch {
            delay(INITIAL_DELAY)
            _action.emit(Action.PlayVideo)
        }

        launch {
            shakeDetector.observeShaking().catch { throwable ->
                analyzeErrorsAndNotify(throwable)
            }.collect {
                _action.emit(Action.PauseVideo)
            }
        }


        launch {
            locationChangeDetector.observeLocationChange().catch { throwable ->
                analyzeErrorsAndNotify(throwable)
            }.collect {
                _action.emit(Action.ReplayVideo)
            }
        }

        launch {
            rotationDetector.observeRotation().catch { throwable ->
                analyzeErrorsAndNotify(throwable)
            }.collect { rotation ->
                when(rotation) {
                    is Rotation.AxisX -> changeVolume(rotation.amount)
                    is Rotation.AxisZ -> seekBy(rotation.amount)
                }
            }
        }
    }

    private suspend fun changeVolume(amount: Double){
        if(amount > 0) {
            increaseVolume(amount)
        } else {
            decreaseVolume(-amount)
        }
    }

    private suspend fun increaseVolume(amount: Double) {
        if(amount > 1){
            repeat(amount.roundToInt()) {
                _action.emit(Action.IncreaseVolume)
            }
        } else {
            // at least increment once
            _action.emit(Action.IncreaseVolume)
        }
    }

    private suspend fun decreaseVolume(amount: Double) {
        if(amount > 1){
            repeat(amount.roundToInt()) {
                _action.emit(Action.DecreaseVolume)
            }
        } else {
            // at least decrement once
            _action.emit(Action.DecreaseVolume)
        }
    }

    private suspend fun seekBy(amount: Double) {
        _action.emit(Action.SeekBy((amount * 1000).toLong()))
    }

    private fun analyzeErrorsAndNotify(throwable: Throwable) {
        val error = when(throwable) {
            is LocationSettingsNotGoodException -> Error.LocationSettingsError
            is FineLocationNotGrantedException -> Error.FineLocationPermissionError
            is LocationNotGrantedException -> Error.LocationPermissionError
            is RotationDetectorException -> Error.GyroscopeError
            is ShakeDetectorException -> Error.AccelerometerError
            else -> Error.UnknownError
        }

        viewModelScope.launch {
            _error.emit(error)
        }
    }

    override fun onCleared() {
        shakeDetector.stopObserving()
        rotationDetector.stopObserving()
        locationChangeDetector.stopObserving()
    }

    sealed class Action {
        object PlayVideo: Action()
        object PauseVideo : Action()
        object ReplayVideo : Action()
        object IncreaseVolume: Action()
        object DecreaseVolume: Action()
        data class SeekBy(val amountMs: Long): Action()
    }

    sealed class Error {
        object LocationPermissionError: Error()
        object FineLocationPermissionError: Error()
        object LocationSettingsError: Error()
        object GyroscopeError: Error()
        object AccelerometerError: Error()
        object UnknownError: Error()
    }
}

// Using DI framework for this small project would be too much, so let's go by old fashioned way
class MainViewModelFactory(
    private val shakeDetector: ShakeDetector,
    private val rotationDetector: RotationDetector,
    private val locationChangeDetector: LocationChangeDetector
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return modelClass.getConstructor(
            ShakeDetector::class.java,
            RotationDetector::class.java,
            LocationChangeDetector::class.java
        ).newInstance(shakeDetector, rotationDetector, locationChangeDetector)
    }
}