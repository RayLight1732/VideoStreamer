package com.jp.ray.videostreamer.viewmodel

import android.Manifest.permission.BLUETOOTH_CONNECT
import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jp.ray.videostreamer.modle.BondedDevice
import com.jp.ray.videostreamer.modle.ConnectionStatus
import com.jp.ray.videostreamer.modle.ConnectionUIStatus
import com.jp.ray.videostreamer.repository.BluetoothRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

class BluetoothViewmodel(private val context: Context,private val bluetoothRepository: BluetoothRepository): ViewModel() {
    companion object {
        val BLUETOOTH_REPOSITORY_KEY = object : CreationExtras.Key<BluetoothRepository> {}
        val MY_APPLICATION_KEY = object : CreationExtras.Key<Application> {}
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val bluetoothRepository = this[BLUETOOTH_REPOSITORY_KEY] as BluetoothRepository
                val application = this[MY_APPLICATION_KEY] as Application
                BluetoothViewmodel(application.applicationContext, bluetoothRepository)
            }
        }
    }

    private val _voltage = MutableStateFlow<Float?>(null)
    val voltage = _voltage.asStateFlow()
    private var job: Job? = null // JobをNullableで管理


    private val bluetoothBroadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            if (BluetoothAdapter.ACTION_STATE_CHANGED == intent.action) {
                if (intent.getIntExtra(
                        BluetoothAdapter.EXTRA_STATE,
                        -1
                    ) == BluetoothAdapter.STATE_ON
                ) {
                    _isBluetoothEnabled.update { true }
                } else {
                    _isBluetoothEnabled.update { false }
                }
            }
        }
    }

    private val _bondedDevices = MutableStateFlow<List<BondedDevice>>(listOf())
    val bondedDevice = _bondedDevices.asStateFlow()
    private val _connectionUIStatus = MutableStateFlow<ConnectionUIStatus>(ConnectionUIStatus.Idle)
    val connectionUIStatus = _connectionUIStatus.asStateFlow()
    private val _isBluetoothEnabled = MutableStateFlow(bluetoothRepository.isBluetoothEnabled())
    val isBluetoothEnabled = _isBluetoothEnabled.asStateFlow()


    init {
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        context.registerReceiver(bluetoothBroadcastReceiver, filter)
        observeConnectionStatus()
    }


    @SuppressLint("MissingPermission")
    private fun startRepeatingTask() {
        // Jobをキャンセルしない限り、1秒ごとに処理を実行
        job = CoroutineScope(Dispatchers.Main).launch {
            while (isActive) { // Jobがアクティブな間ループを続ける
                println("in repeating task")
                delay(1000) // 1秒待機
                try {
                    bluetoothRepository.updateCharacteristic(serviceUUID,characteristicUUID)
                }catch (ignore:Exception){
                    ignore.printStackTrace()
                }
            }
        }
    }

    private fun observeConnectionStatus() {
        viewModelScope.launch {
            bluetoothRepository.connectionState.collect {
                when {
                    _connectionUIStatus.value is ConnectionUIStatus.Connecting -> {
                        if (it is ConnectionStatus.Connected) {
                            _connectionUIStatus.update { ConnectionUIStatus.Connected }
                            bluetoothRepository.setCharacteristicHandler(
                                serviceUUID,
                                characteristicUUID
                            ) { bytes ->

                                // リトルエンディアンで解析
                                val bufferLittleEndian = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                                val floatLittleEndian = bufferLittleEndian.getFloat()
                                println(floatLittleEndian)
                                _voltage.update { floatLittleEndian }
                            }
                            startRepeatingTask()
                        } else {
                            _connectionUIStatus.update { ConnectionUIStatus.FailedToConnect }
                        }
                    }
                    _connectionUIStatus.value is ConnectionUIStatus.Connected -> {
                        if (it is ConnectionStatus.Disconnected) {
                            job?.cancel()
                            bluetoothRepository.removeCharacteristicHandler(
                                serviceUUID,
                                characteristicUUID
                            )
                            _connectionUIStatus.update { ConnectionUIStatus.DisconnectedByPair }
                        }
                    }

                    _connectionUIStatus.value is ConnectionUIStatus.Disconnecting -> {
                        if (it is ConnectionStatus.Disconnected) {
                            job?.cancel()
                            bluetoothRepository.removeCharacteristicHandler(
                                serviceUUID,
                                characteristicUUID
                            )
                            _connectionUIStatus.update { ConnectionUIStatus.Idle }
                        }
                    }
                }
            }

        }
    }

    @RequiresPermission(BLUETOOTH_CONNECT)
    suspend fun refreshBondedDevices(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                _bondedDevices.update {
                    bluetoothRepository.getBonded().toList()
                }
                true
            } catch (ignore: Exception) {
                false
            }
        }

    }

    @RequiresPermission(BLUETOOTH_CONNECT)
    fun connect(address: String) {
        if (_connectionUIStatus.value is ConnectionUIStatus.Idle) {
            _connectionUIStatus.update {
                ConnectionUIStatus.Connecting
            }
            bluetoothRepository.connect(address)
        }
    }


    @RequiresPermission(BLUETOOTH_CONNECT)
    fun disconnect() {
        if (_connectionUIStatus.value is ConnectionUIStatus.Connecting) {
            _connectionUIStatus.update { ConnectionUIStatus.Idle }
            bluetoothRepository.disconnect()
        } else if (_connectionUIStatus.value is ConnectionUIStatus.Connected) {
            _connectionUIStatus.update { ConnectionUIStatus.Disconnecting }
            bluetoothRepository.disconnect()
        }
    }

    private val serviceUUID = UUID.fromString("19B10000-E8F2-537E-4F6C-D104768A1214")
    private val characteristicUUID = UUID.fromString("19B10001-E8F2-537E-4F6C-D104768A1214")
    private val switchCharacteristicUUID = UUID.fromString("19B10001-E8F2-537E-4F6C-D104768A1215")
    @RequiresPermission(BLUETOOTH_CONNECT)
    fun write(message: ByteArray) {
        bluetoothRepository.write(serviceUUID, switchCharacteristicUUID, message)
    }

    fun dismissDisconnectedByPair() {
        if (_connectionUIStatus.value == ConnectionUIStatus.DisconnectedByPair) {
            _connectionUIStatus.update { ConnectionUIStatus.Idle }
        }
    }

    fun dismissFailedToConnect() {
        if (_connectionUIStatus.value == ConnectionUIStatus.FailedToConnect) {
            _connectionUIStatus.update { ConnectionUIStatus.Idle }
        }
    }

    fun canSendMessage():Boolean {
        return bluetoothRepository.canSendMessage()
    }


    override fun onCleared() {
        context.unregisterReceiver(bluetoothBroadcastReceiver)
        try {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                bluetoothRepository.disconnect()
            }

        } catch (ignore: Exception) {

        }
    }
}

sealed class UiState {
    object Idle : UiState()
    object Processing : UiState()
    object Success : UiState()
    data class Failure(val message: String) : UiState()
}