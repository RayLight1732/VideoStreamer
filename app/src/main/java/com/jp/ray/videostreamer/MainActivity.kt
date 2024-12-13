package com.jp.ray.videostreamer

import android.Manifest
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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.jp.ray.videostreamer.ui.theme.VideoStreamerTheme
import java.nio.ByteBuffer
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VideoStreamerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    //MainCamera()
                    SocketUI(paddingValues = padding)
                }
            }
        }
    }
}


@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MainCamera() {
    val permissionState: PermissionState = rememberPermissionState(permission = Manifest.permission.CAMERA)

    if(permissionState.status.isGranted){
        CameraStart()
    }else{
        NoPermission(onRequestPermission = permissionState::launchPermissionRequest)
    }
}

@Composable
fun NoPermission(onRequestPermission:() -> Unit){
    Column(modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center)
    {

        Button(onClick =  onRequestPermission ) {
            Text(text = "カメラの許可を与えてください")
        }
    }
}

data class CameraPreviewConfig(
    @AspectRatio.Ratio val aspectRatio: Int,
    @ImageOutputConfig.RotationValue val rotation: Int,
)

private fun getCameraProviderConfig(previewView: View): CameraPreviewConfig {
    val rotation = ContextCompat.getDisplayOrDefault(previewView.context).rotation
    return CameraPreviewConfig(
        aspectRatio = AspectRatio.RATIO_4_3,
        rotation = rotation,
    )
}

private fun buildImageAnalysis(previewView: View): ImageAnalysis {
    val (screenAspectRatio, rotation) = getCameraProviderConfig(previewView)
    return ImageAnalysis.Builder()
        .setTargetAspectRatio(screenAspectRatio)
        .build()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraStart(){
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    
    val previewView = PreviewView(context).apply {
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        scaleType = PreviewView.ScaleType.FILL_CENTER
    }
    val executor = remember {
        Executors.newSingleThreadExecutor()
    }


    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
    DisposableEffect(Unit) {
        println("disposeable")
        //メインスレッドとまるけどとりあえず許容 cameraProviderFuture.addListenerで解決できるっぽい
        val cameraProvider = cameraProviderFuture.get()
        Log.d("cameraProvider",cameraProvider.toString())

        val preview = Preview.Builder().build()
        val analyzer = ImageAnalysis.Builder().build().also {
            it.setAnalyzer(executor,LuminosityAnalyzer())
        }

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()

        preview.surfaceProvider = previewView.surfaceProvider
        cameraProvider.bindToLifecycle(
            lifecycleOwner,
            cameraSelector,
            preview,
            analyzer
        )
        onDispose {
            cameraProvider.unbindAll()
        }
    }
    Scaffold(floatingActionButton = {
        FloatingActionButton(onClick = { }) {
            Icon(imageVector = Icons.Default.Add, contentDescription = "Clothesadd")
        }
    }) {paddingValues: PaddingValues ->
        AndroidView(factory = {previewView},
            modifier = Modifier.padding(paddingValues))
    }
}
private class LuminosityAnalyzer() : ImageAnalysis.Analyzer {

    private fun ByteBuffer.toByteArray(): ByteArray {
        rewind()    // Rewind the buffer to zero
        val data = ByteArray(remaining())
        get(data)   // Copy the buffer into a byte array
        return data // Return the byte array
    }

    override fun analyze(image: ImageProxy) {
        val buffer = image.planes[0].buffer
        val data = buffer.toByteArray()
        val pixels = data.map { it.toInt() and 0xFF }
        val luma = pixels.average()

        image.close()
    }
}