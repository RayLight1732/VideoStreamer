package com.jp.ray.videostreamer

import android.Manifest
import android.Manifest.permission.BLUETOOTH_CONNECT
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.getSystemService
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.util.UUID


@SuppressLint("MissingPermission")
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun RequestBluetoothEnable(paddingValues: PaddingValues) {
    val context = LocalContext.current
    val bluetoothAdapter = remember {
        val bluetoothManager = getSystemService(context,BluetoothManager::class.java)
        bluetoothManager?.adapter
    }
    if (bluetoothAdapter == null) {
        Text(modifier = Modifier.padding(paddingValues),text = "Bluetooth is not supported")
        return
    }

    var bluetoothEnabled by remember { mutableStateOf(bluetoothAdapter.isEnabled) }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        bluetoothEnabled = if (result.resultCode == Activity.RESULT_OK) {
            true
        } else {
            false
        }
    }

    val enableBluetoothIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
    val permissionState: PermissionState = rememberPermissionState(permission = Manifest.permission.BLUETOOTH_CONNECT)
    val permissionState2: PermissionState = rememberPermissionState(permission = Manifest.permission.BLUETOOTH_CONNECT)

    if(!permissionState.status.isGranted){
        NoPermission(onRequestPermission = permissionState::launchPermissionRequest)
    }
    if (!permissionState2.status.isGranted) {
        NoPermission(onRequestPermission = permissionState::launchPermissionRequest)
    }
    //bluetoothAdapter.bondedDevices.first().connectGatt(context,false,)
    bluetoothAdapter.bondedDevices.forEach {
        it.name
    }
    val gatt: BluetoothGatt = bluetoothAdapter.bondedDevices.first().connectGatt(context,false,
        bluetoothGattCallback)

    Button(modifier = Modifier.padding(paddingValues),onClick = { launcher.launch(enableBluetoothIntent) }) {
        Text(if (bluetoothEnabled) "Bluetooth Enabled" else "Enable Bluetooth")
    }
}

private val bluetoothGattCallback = object : BluetoothGattCallback() {

    @RequiresPermission(BLUETOOTH_CONNECT)
    override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
        if (newState == BluetoothProfile.STATE_CONNECTED) {
            //successfully connected to the GATT server
            gatt?.discoverServices()
        } else {
            //disconnected from the GATT server
        }
    }

    @RequiresPermission(BLUETOOTH_CONNECT)
    override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
        if (status == BluetoothGatt.GATT_SUCCESS) {
            gatt?.let {
                it.getService(UUID.fromString(""))?.let { service ->
                    service.getCharacteristic(UUID.fromString(""))?.let { characteristic ->
                        val writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                        val value = ByteArray(1)
                        value[0] = 1
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            it.writeCharacteristic(
                                characteristic,
                                value,
                                writeType
                            )

                        } else {
                            characteristic.writeType = writeType
                            @Suppress("DEPRECATION")
                            characteristic.value = value
                            @Suppress("DEPRECATION")
                            it.writeCharacteristic(characteristic)
                        }

                    }
                }
            }
        }
    }
}