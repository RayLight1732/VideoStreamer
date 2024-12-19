package com.jp.ray.videostreamer

import android.Manifest
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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.jp.ray.videostreamer.ui.theme.VideoStreamerTheme
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val socketViewModel:SocketViewModel = viewModel()
            val uiState by socketViewModel.uiState.collectAsState()
            VideoStreamerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    if (!uiState.connected) {
                        SocketUI(paddingValues = padding, socketViewModel = socketViewModel)
                    } else {
                        MainCamera(socketViewModel)
                    }
                }
            }
        }
    }
}


@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MainCamera(socketViewModel: SocketViewModel) {
    val permissionState: PermissionState = rememberPermissionState(permission = Manifest.permission.CAMERA)

    if(permissionState.status.isGranted){
        CameraStart(socketViewModel)
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
fun CameraStart(socketViewModel: SocketViewModel){
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
            it.setAnalyzer(executor,LuminosityAnalyzer(socketViewModel))
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
        FloatingActionButton(onClick = {socketViewModel.closeSocket() }) {
            Icon(imageVector = Icons.Default.Close, contentDescription = "Clothesadd")
        }
    }) {paddingValues: PaddingValues ->
        AndroidView(factory = {previewView},
            modifier = Modifier.padding(paddingValues))
    }
}
private class LuminosityAnalyzer(private val socketViewModel: SocketViewModel) : ImageAnalysis.Analyzer {

    private fun ByteBuffer.toByteArray(): ByteArray {
        rewind()    // Rewind the buffer to zero
        val data = ByteArray(remaining())
        get(data)   // Copy the buffer into a byte array
        return data // Return the byte array
    }

    override fun analyze(image: ImageProxy) {
        val width = image.width
        val height = image.height

        // ImageProxyをBitmapに変換
        val bitmap = image.toBitmap()

        // Bitmapをバイト配列に変換 (PNG形式で圧縮)
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        outputStream.flush()
        val bitmapBytes = outputStream.toByteArray()

        // メッセージとして送信
        val message = buildMessage(width, height, bitmapBytes)
        socketViewModel.sendMessage(message)
        // ImageProxyを閉じる
        image.close()
    }


    // 高さ、幅、ビットマップを結合したバイト配列を作成
    private fun buildMessage(width: Int, height: Int, bitmapBytes: ByteArray): ByteArray {
        println(bitmapBytes.size)
        return width.toBytes()+height.toBytes()+bitmapBytes.size.toBytes() + bitmapBytes // ヘッダとビットマップバイト配列を結合
    }

    private fun Int.toBytes():ByteArray {
        return ByteBuffer.allocate(Int.SIZE_BYTES).order(ByteOrder.BIG_ENDIAN).putInt(this).array()
    }
}