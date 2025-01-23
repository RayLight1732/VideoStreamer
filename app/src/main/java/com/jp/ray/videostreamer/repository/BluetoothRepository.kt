package com.jp.ray.videostreamer.repository

import android.Manifest.permission.BLUETOOTH_CONNECT
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat.getSystemService
import com.jp.ray.videostreamer.modle.BondedDevice
import com.jp.ray.videostreamer.modle.ConnectionStatus
import com.jp.ray.videostreamer.modle.ConnectionUIStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

//TODO インターフェースに分離
//そもそもrepositoryという名前が正しいか検討
class BluetoothRepository(private val context: Context) {
    private val _connectionState = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Disconnected(255))
    val connectionState = _connectionState.asStateFlow()
    private val _discoveredState = MutableStateFlow(false)
    val discoveredState = _discoveredState.asStateFlow()
    private val bluetoothAdapter: BluetoothAdapter
    private var gatt: BluetoothGatt? = null

    private val bluetoothGattCallback = object : BluetoothGattCallback() {

        @RequiresPermission(BLUETOOTH_CONNECT)
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            println("on connection state change ${status},${newState}")
            _connectionState.update {
                println("update")
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    ConnectionStatus.Connected(newState)
                } else {
                    println("disconnected")
                    ConnectionStatus.Disconnected(newState)
                }
            }
            gatt?.let {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    it.discoverServices()
                } else {
                    //it.close()
                    this@BluetoothRepository.gatt = null
                }
            }
        }

        @RequiresPermission(BLUETOOTH_CONNECT)
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                _discoveredState.update { true }
                gatt?.let {
                    it.services.forEach{
                        service->
                        println("service:${service.uuid}")
                        service.characteristics.forEach {
                            c->
                            println("char:${c.uuid}")
                        }
                    }
                }
            } else {
                _discoveredState.update { false }
            }
        }
    }

    init {
        val bluetoothManager = getSystemService(context, BluetoothManager::class.java)
        bluetoothAdapter = bluetoothManager?.adapter
            ?: throw IllegalStateException("This device doesn't support bluetooth")

    }

    fun isBluetoothEnabled(): Boolean = bluetoothAdapter.isEnabled

    fun canSendMessage():Boolean = connectionState.value is ConnectionStatus.Connected && discoveredState.value

    @RequiresPermission(BLUETOOTH_CONNECT)
    fun connect(address: String) {
        if (_connectionState.value  !is ConnectionStatus.Disconnected) {
            throw IllegalStateException("Already connected or trying")
        }
        gatt = bluetoothAdapter.getRemoteDevice(address)
            .connectGatt(context, false, bluetoothGattCallback)
    }

    @RequiresPermission(BLUETOOTH_CONNECT)
    fun disconnect() {
        gatt?.also {
            it.disconnect()
            _discoveredState.update { false }
        } ?: throw IllegalStateException("Bluetooth is not connected")
    }

    @RequiresPermission(BLUETOOTH_CONNECT)
    fun write(serviceUUID: UUID, characteristicUUID: UUID, message: ByteArray) {
        val currentState = _connectionState.value
        if (currentState is ConnectionStatus.Connected || !_discoveredState.value) {
            throw IllegalStateException("Bluetooth is not connected or service is not discovered")
        }
        gatt?.also {
            val service =
                it.getService(serviceUUID) ?: throw IllegalArgumentException("Service is not found")
            val characteristic = service.getCharacteristic(characteristicUUID)
                ?: throw IllegalArgumentException("Characteristic is not found")
            writeCharacteristic(it, characteristic, message)
        } ?: throw IllegalStateException("Gatt server is null")
    }

    @RequiresPermission(BLUETOOTH_CONNECT)
    private fun writeCharacteristic(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        message: ByteArray
    ) {
        val writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            //TODO よくない 本当はコールバックを見てやる必要がある
            gatt.writeCharacteristic(
                characteristic,
                message,
                writeType
            )
        } else {
            characteristic.writeType = writeType
            @Suppress("DEPRECATION")
            characteristic.value = message
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(characteristic)
        }
    }

    @RequiresPermission(BLUETOOTH_CONNECT)
    fun getBonded(): Set<BondedDevice> {
        return bluetoothAdapter.bondedDevices.map {
            BondedDevice(
                it.name,
                it.address,
                it.bondState
            )
        }.toSet()
    }
}

