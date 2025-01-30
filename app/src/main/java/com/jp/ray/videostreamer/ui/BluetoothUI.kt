package com.jp.ray.videostreamer.ui

import android.Manifest
import android.Manifest.permission.BLUETOOTH_CONNECT
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.jp.ray.videostreamer.modle.BondedDevice
import com.jp.ray.videostreamer.modle.ConnectionUIStatus
import com.jp.ray.videostreamer.viewmodel.BluetoothViewmodel
import kotlinx.coroutines.launch

@SuppressLint("MissingPermission")
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun Bluetooth(modifier: Modifier,viewmodel: BluetoothViewmodel) {
    val permissionState: PermissionState =
        rememberPermissionState(permission = Manifest.permission.BLUETOOTH_CONNECT)

    if (!permissionState.status.isGranted) {
        RequestPermission(
            "Bluetoothの許可を与えてください",
            onRequestPermission = permissionState::launchPermissionRequest
        )
        return
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
    }

    val bluetoothEnabled by viewmodel.isBluetoothEnabled.collectAsState()
    if (!bluetoothEnabled) {
        val enableBluetoothIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        Button(
            modifier = modifier,
            onClick = { launcher.launch(enableBluetoothIntent) }) {
            Text("Enable bluetooth")
        }
    } else {

        val connectionState by viewmodel.connectionUIStatus.collectAsState()
        if (connectionState is ConnectionUIStatus.Idle || connectionState is ConnectionUIStatus.Connecting || connectionState is ConnectionUIStatus.FailedToConnect || connectionState is ConnectionUIStatus.CancelConnecting) {
            val isConnecting = connectionState is ConnectionUIStatus.Connecting
            val isFailedToConnect = connectionState is ConnectionUIStatus.FailedToConnect
            val isCancellingToConnect = connectionState is ConnectionUIStatus.CancelConnecting
            ConnectUI(
                modifier,
                viewmodel,
                isConnecting,
                isFailedToConnect,
                isCancellingToConnect,
                viewmodel::dismissFailedToConnect
            )
        } else if (connectionState is ConnectionUIStatus.Connected || connectionState is ConnectionUIStatus.Disconnecting || connectionState is ConnectionUIStatus.DisconnectedByPair) {
            val isDisconnecting = connectionState is ConnectionUIStatus.Disconnecting
            val disconnectedByPair = connectionState is ConnectionUIStatus.DisconnectedByPair
            val voltage = viewmodel.voltage.collectAsState()
            AfterConnected(
                modifier,
                isDisconnecting,
                disconnectedByPair,
                voltage.value,
                viewmodel::write,
                viewmodel::disconnect,
                viewmodel::dismissDisconnectedByPair
            )
        } else {
            Text(text = "Undefined", modifier = modifier)
        }
    }
}

@Composable
@RequiresPermission(BLUETOOTH_CONNECT)
fun ConnectUI(modifier: Modifier,viewmodel: BluetoothViewmodel,isConnecting:Boolean,isFailedToConnect:Boolean,isCancellingToConnect:Boolean,dismissFailedToConnect:()->Unit) {
    val bondedDevice by viewmodel.bondedDevice.collectAsState()
    Box(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            Button(onClick = { viewmodel.viewModelScope.launch { viewmodel.refreshBondedDevices() } }) {
                Text(text = "refresh")
            }
            LazyColumn {
                items(bondedDevice) { item ->
                    BondedDeviceItem(device = item) {
                        viewmodel.connect(item.address)
                    }
                }
            }
        }
        if (isConnecting || isCancellingToConnect) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.5f))
                    .clickable(enabled = false) {}) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = if (isConnecting) {
                            "Connecting"
                        } else {
                            "Cancelling"
                        }
                    )
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    if (isConnecting) {
                        Button(onClick = viewmodel::disconnect) {
                            Text(text = "cancel")
                        }
                    }
                }
            }
        } else if (isFailedToConnect) {
            ConnectionFailDialog(message = "failed to connect") {
                viewmodel.dismissFailedToConnect()
            }
        }
    }
}

@Composable
fun RequestPermission(text:String,onRequestPermission:() -> Unit){
    Column(modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center)
    {
        Button(onClick =  onRequestPermission ) {
            Text(text = text)
        }
    }
}

@Composable
fun BondedDeviceItem(device: BondedDevice,onClick:()->Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 4.dp
        ),
        shape = RoundedCornerShape(8.dp),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = device.name,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = device.address,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}


@Composable
fun ConnectionFailDialog(message:String,close:()->Unit) {
    AlertDialog(onDismissRequest = close, confirmButton = {
        TextButton(onClick = close) {
            Text(text = "OK")
        }
    }, title = {
        Text(text = "Connection Failure")
    }, text = {
        Text(text = message)
    })
}

@Composable
fun AfterConnected(modifier: Modifier, isDisconnecting:Boolean,disconnectedByPair:Boolean, voltage: Float?,write:(message:ByteArray)->Unit, disconnect:()->Unit,reset:()->Unit) {
    Box(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (voltage == null) {
                Text(text = "---")
            } else {
                Text(text = "測定電圧:${voltage}V")
            }
            TextButton(onClick = disconnect) {
                Text(text = "Disconnect")
            }
        }

        if (isDisconnecting) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.5f))
                    .clickable(enabled = false) {}) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Disconnecting"
                    )
                    CircularProgressIndicator()
                }
            }
        }
    }
    if (disconnectedByPair) {
        AlertDialog(onDismissRequest = reset,confirmButton = {
            TextButton(onClick = reset) {
                Text(text = "OK")
            }
        }, title = {
            Text(text = "Disconnected")
        }, text = {
            Text(text = "Disconnected By Pair")
        })
    }
}