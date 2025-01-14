package com.jp.ray.videostreamer.ui

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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.jp.ray.videostreamer.viewmodel.SocketViewModel

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun SocketUI(modifier: Modifier=Modifier, socketViewModel: SocketViewModel = viewModel()) {
    val permissionState: PermissionState = rememberPermissionState(permission = Manifest.permission.INTERNET)

    if(!permissionState.status.isGranted){
        NoPermission(modifier,onRequestPermission = permissionState::launchPermissionRequest)
        return
    }

    val uiState by socketViewModel.uiState.collectAsState()
    Column(modifier = modifier) {
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



