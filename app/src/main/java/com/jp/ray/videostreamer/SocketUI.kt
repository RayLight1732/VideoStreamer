package com.jp.ray.videostreamer

import android.Manifest
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.core.text.isDigitsOnly
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun SocketUI(paddingValues: PaddingValues, socketViewModel: SocketViewModel = viewModel()) {
    val permissionState: PermissionState = rememberPermissionState(permission = Manifest.permission.INTERNET)

    if(!permissionState.status.isGranted){
        NoPermission(onRequestPermission = permissionState::launchPermissionRequest)
        return
    }

    val uiState by socketViewModel.uiState.collectAsState()
    Column(modifier = Modifier.padding(paddingValues)) {
        TextFieldWithPlaceholder(
            value = uiState.host,
            onValueChange = socketViewModel::updateHost,
            placeholder = "Host"
        )
        TextFieldWithPlaceholder(
            value = uiState.port,
            onValueChange = { if (it.isDigitsOnly()) socketViewModel.updatePort(it) },
            placeholder = "Port",
            keyboardType = KeyboardType.Number
        )
        Button(onClick = { socketViewModel.createSocket() }, enabled = !uiState.connected) {
            Text(text = "Create Socket")
        }
        TextFieldWithPlaceholder(
            value = uiState.message,
            onValueChange = socketViewModel::updateMessage,
            placeholder = "Message"
        )
        Button(onClick = { socketViewModel.sendMessage() }, enabled = uiState.connected) {
            Text(text = "Send Message")
        }
        Button(onClick = { socketViewModel.closeSocket() }, enabled = uiState.connected) {
            Text(text = "Close Socket")
        }
        if (uiState.errorMessage.isNotEmpty()) {
            Text(text = "Error: ${uiState.errorMessage}", color = Color.Red)
        }
    }
}

@Composable
fun TextFieldWithPlaceholder(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(text = placeholder) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType)
    )
}

data class SocketUIState(
    val host: String = "",
    val port: String = "",
    val message: String = "",
    val errorMessage: String = "",
    val connected: Boolean = false
)

class SocketViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(SocketUIState())
    val uiState = _uiState.asStateFlow()

    private var socket: Socket? = null
    private var outputStream: OutputStream? = null

    fun updateHost(host: String) {
        _uiState.update { it.copy(host = host) }
    }

    fun updatePort(port: String) {
        _uiState.update { it.copy(port = port) }
    }

    fun updateMessage(message: String) {
        _uiState.update { it.copy(message = message) }
    }

    fun createSocket() {
        viewModelScope.launch(Dispatchers.IO) {
            executeSafely {
                if (_uiState.value.port.isNotEmpty()) {
                    val host = _uiState.value.host
                    val port = _uiState.value.port.toInt()
                    println("create")
                    val newSocket = Socket()
                    newSocket.connect(InetSocketAddress(host,port),5000)
                    println("complete")
                    socket = newSocket
                    outputStream = newSocket.getOutputStream()
                    _uiState.update { it.copy(connected = true, errorMessage = "") }
                }
            }
        }
    }

    fun sendMessage() {
        viewModelScope.launch(Dispatchers.IO) {
            executeSafely {
                outputStream?.apply {
                    val message = _uiState.value.message
                    write(message.toByteArray())
                    flush()
                }
            }
        }
    }

    fun closeSocket() {
        viewModelScope.launch(Dispatchers.IO) {
            executeSafely {
                socket?.close()
                socket = null
                outputStream = null
                _uiState.update { it.copy(connected = false, errorMessage = "") }
            }
        }
    }

    private fun executeSafely(action: () -> Unit) {
        try {
            action()
        } catch (e: Exception) {
            e.printStackTrace()
            println("error:${e.message.orEmpty()}")
            _uiState.update { it.copy(errorMessage = e.message.orEmpty()) }
        }
    }

    override fun onCleared() {
        try {
            socket?.close()

        } catch (ignore: Exception) {
        }
        viewModelScope.cancel()
    }
}