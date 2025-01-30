package com.jp.ray.videostreamer

import android.Manifest
import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.LinearLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.impl.ImageOutputConfig
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.jp.ray.videostreamer.repository.BluetoothRepository
import com.jp.ray.videostreamer.ui.Bluetooth
import com.jp.ray.videostreamer.ui.MainCamera
import com.jp.ray.videostreamer.ui.SocketUI
import com.jp.ray.videostreamer.ui.theme.VideoStreamerTheme
import com.jp.ray.videostreamer.viewmodel.BluetoothViewmodel
import com.jp.ray.videostreamer.viewmodel.SocketViewModel
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val bluetoothRepository = BluetoothRepository(application.applicationContext)
        val extras = MutableCreationExtras().apply {
            set(BluetoothViewmodel.BLUETOOTH_REPOSITORY_KEY,bluetoothRepository)
            set(BluetoothViewmodel.MY_APPLICATION_KEY,application)
        }
        enableEdgeToEdge()
        setContent {
            val socketViewModel: SocketViewModel = viewModel()
            val bluetoothViewmodel:BluetoothViewmodel = viewModel(factory = BluetoothViewmodel.Factory, extras = extras)
            val uiState by socketViewModel.uiState.collectAsState()
            var isDanger by remember {
                mutableStateOf(false)
            }
            var isDebug by remember {
                mutableStateOf(false)
            }
            LaunchedEffect(key1 = Unit) {
                socketViewModel.messageHandler = { value ->
                    println("value:$value")
                    if (bluetoothViewmodel.canSendMessage()) {
                        val message = byteArrayOf(if (value == 0) 0 else 1)
                        isDanger = value == 1
                        bluetoothViewmodel.write(message)
                    }
                }
            }
            VideoStreamerTheme {
                Scaffold(floatingActionButton ={
                    SmallFloatingActionButton(onClick = { isDebug = !isDebug }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Debug")
                    }
                } ,modifier = Modifier.fillMaxSize()) { padding ->
                    Column(modifier = Modifier.padding(padding)) {
                        if (!uiState.connected) {
                            SocketUI(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize(),
                                socketViewModel = socketViewModel
                            )
                        } else {
                            MainCamera(socketViewModel,modifier = Modifier
                                .weight(1f)
                                .fillMaxSize())
                        }
                        if (isDebug) {
                            Text(text = "isDanger:$isDanger")
                            TextButton(onClick = { bluetoothViewmodel.write(byteArrayOf(1)) }) {
                                Text(text = "Send danger")
                            }
                            TextButton(onClick = { bluetoothViewmodel.write(byteArrayOf(0)) }) {
                                Text(text = "Send safe")
                            }
                        }
                        Bluetooth(modifier = Modifier
                            .padding(4.dp)
                            .weight(1f)
                            .fillMaxSize(),bluetoothViewmodel)

                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
    }
}


