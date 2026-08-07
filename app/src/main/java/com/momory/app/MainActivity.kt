package com.momory.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import com.momory.app.ui.ChatScreen
import com.momory.app.ui.theme.MomoryTheme

class MainActivity : ComponentActivity() {

    private val viewModel: ChatViewModel by viewModels()

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        results[Manifest.permission.RECORD_AUDIO]?.let { viewModel.onMicPermissionRequestResult(it) }
        val locationGranted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            results[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (results.containsKey(Manifest.permission.ACCESS_FINE_LOCATION) ||
            results.containsKey(Manifest.permission.ACCESS_COARSE_LOCATION)
        ) {
            viewModel.onLocationPermissionResult(locationGranted)
        }
    }

    private fun hasPermission(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val micGranted = hasPermission(Manifest.permission.RECORD_AUDIO)
        val locationGranted = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) ||
            hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
        viewModel.setInitialMicPermission(micGranted)
        viewModel.setInitialLocationPermission(locationGranted)

        val toRequest = buildList {
            if (!micGranted) add(Manifest.permission.RECORD_AUDIO)
            if (!locationGranted) {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
        }
        if (toRequest.isNotEmpty()) {
            requestPermissions.launch(toRequest.toTypedArray())
        }

        setContent {
            val settings by viewModel.settings
            MomoryTheme(theme = settings.appTheme) {
                ChatScreen(
                    viewModel = viewModel,
                    onMicClick = {
                        if (viewModel.micPermissionGranted.value) {
                            viewModel.onMicButtonPressed()
                        } else {
                            requestPermissions.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                        }
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onAppForeground(true)
    }

    override fun onPause() {
        viewModel.onAppForeground(false)
        super.onPause()
    }
}
