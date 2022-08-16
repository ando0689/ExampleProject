package com.test.exam

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.test.exam.MainViewModel.Action.*
import com.test.exam.MainViewModel.Error.*
import com.test.exam.data.location.LocationChangeDetector
import com.test.exam.data.location.LocationPermissionRequester
import com.test.exam.data.location.LocationSettingsChecker
import com.test.exam.data.sensors.RotationDetector
import com.test.exam.data.sensors.ShakeDetector
import com.test.exam.databinding.ActivityMainBinding
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

private const val VIDEO_URL = "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/WeAreGoingOnBullrun.mp4"

class MainActivity : AppCompatActivity() {

    private val binding: ActivityMainBinding by lazy(LazyThreadSafetyMode.NONE) {
        ActivityMainBinding.inflate(LayoutInflater.from(this))
    }

    private lateinit var viewModel: MainViewModel
    private var player: ExoPlayer? = null

    private val permissionRequester = LocationPermissionRequester(this)
    private val settingsChecker = LocationSettingsChecker(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        createViewModel()
        observeViewModel()
        viewModel.start()
    }

    private fun observeViewModel() = lifecycleScope.launch {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            launch {
                viewModel.action.filterNotNull().collect { action ->
                    when(action) {
                        PlayVideo -> startPlayVideo()
                        ReplayVideo -> replayVideo()
                        PauseVideo -> pauseVideo()
                        IncreaseVolume -> increaseVolume()
                        DecreaseVolume -> decreaseVolume()
                        is SeekBy -> seekVideoBy(action.amountMs)
                    }
                }
            }

            launch {
                viewModel.error.filterNotNull().collect { error ->
                    val messageResId = when(error) {
                        AccelerometerError -> R.string.accelerometer_error
                        FineLocationPermissionError -> R.string.fine_location_permission_error
                        GyroscopeError -> R.string.gyroscope_error
                        LocationPermissionError -> R.string.location_permission_error
                        LocationSettingsError -> R.string.location_settings_error
                        UnknownError -> R.string.unknown_error
                    }

                    showErrorMessage(messageResId)
                }
            }
        }
    }

    private fun showErrorMessage(@StringRes resId: Int) {
        AlertDialog.Builder(this)
            .setMessage(resId)
            .setPositiveButton(R.string.ok) { dialog, _ -> dialog.dismiss()}
            .show()
    }

    private fun startPlayVideo() = player?.apply {
        binding.loading.isVisible = false
        binding.playerView.isVisible = true
        play()
    }

    private fun pauseVideo() = player?.apply { pause() }

    private fun replayVideo() = player?.apply { seekTo(0) }

    private fun increaseVolume() = player?.apply { increaseDeviceVolume() }

    private fun decreaseVolume() = player?.apply { decreaseDeviceVolume() }

    private fun seekVideoBy(amountMs: Long) = player?.apply {
        seekTo(currentPosition + amountMs)
    }

    override fun onStart() {
        super.onStart()
        initializePlayer()
    }

    override fun onStop() {
        super.onStop()
        releasePlayer()
    }

    private fun initializePlayer() {
        val mediaItem = MediaItem.fromUri(VIDEO_URL)
        player = ExoPlayer.Builder(this).build()
            .also { exoPlayer ->
                binding.playerView.player = exoPlayer
                exoPlayer.setMediaItem(mediaItem)
                exoPlayer.prepare()
                viewModel.playerCurrentPosition?.let {
                    exoPlayer.seekTo(it)
                }
            }
    }

    private fun releasePlayer() {
        player?.let { exoPlayer ->
            viewModel.playerCurrentPosition = exoPlayer.currentPosition
            exoPlayer.release()
        }
        player = null
    }

    private fun createViewModel() {
        val factory = MainViewModelFactory(
            ShakeDetector(applicationContext),
            RotationDetector(applicationContext),
            LocationChangeDetector(applicationContext, permissionRequester, settingsChecker))
        viewModel = ViewModelProvider(this, factory)[MainViewModel::class.java]
    }


    // We have to use deprecated onActivityResult here as
    // [LocationSettingsRequest startResolutionForResult] still uses it
    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        settingsChecker.handleActivityResult(requestCode, resultCode, data)
    }
}