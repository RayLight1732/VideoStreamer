package com.jp.ray.videostreamer.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jp.ray.videostreamer.modle.SocketUIState
import com.jp.ray.videostreamer.repository.BluetoothRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.LinkedBlockingQueue


class SocketViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(SocketUIState())
    val uiState = _uiState.asStateFlow()

    private var socket: Socket? = null
    private var outputStream: OutputStream? = null
    private var inputStream: InputStream? = null

    private val blockingQueue = LinkedBlockingQueue<ByteArray>()
    private var senderJob: Job? = null

    private var readerJob: Job? = null
    var messageHandler:((Int)->Unit)? = null

    fun updateHost(host: String) {
        _uiState.update { it.copy(host = host) }
    }

    fun updatePort(port: String) {
        _uiState.update { it.copy(port = port) }
    }


    fun createSocket():Boolean {
        if (senderJob != null) return false
        viewModelScope.launch(Dispatchers.IO) {
            blockingQueue.clear()
            executeSafely {
                _uiState.update { it.copy(errorMessage = "") }
                val host = _uiState.value.host
                val port = _uiState.value.port.toIntOrNull() ?: throw IllegalArgumentException("Invalid port")

                socket = Socket().apply {
                    connect(InetSocketAddress(host, port), 5000)
                    this@SocketViewModel.outputStream = getOutputStream()
                    this@SocketViewModel.inputStream = getInputStream()
                }
                _uiState.update { it.copy(connected = true) }

                senderJob = launchSenderJob()
                readerJob = launchReaderJob()
            }
        }
        return true
    }

    private fun launchSenderJob(): Job {
        return viewModelScope.launch(Dispatchers.IO) {
            executeSafely {
                while (true) {
                    val item = blockingQueue.take()
                    if (item.isEmpty()) {
                        break
                    }
                    println("remains:${blockingQueue.size}")
                    blockingQueue.clear()//TODO 場合によってはemptyが消える


                    try {
                        outputStream!!.apply {
                            write(item)
                            flush()
                        }
                    } catch (e: Exception) {
                        closeSocket()
                        println("close in launch sender job")
                        break
                    }

                }
            }
        }
    }

    private fun launchReaderJob(): Job {
        return viewModelScope.launch(Dispatchers.IO) {
            executeSafely {
                while (true) {
                    val value = inputStream!!.read()
                    if (value != -1) {
                        messageHandler?.let { it(value) }
                    }
                }
            }
        }
    }

    fun sendMessage(message: ByteArray) {
        senderJob?.let { blockingQueue.add(message) }
    }

    fun closeSocket() {
        senderJob?.let {
                job->
            _uiState.update { it.copy(errorMessage = "") }
            blockingQueue.clear()
            blockingQueue.put(ByteArray(0))//to stop the thread
            viewModelScope.launch(Dispatchers.IO) {
                job.join()
                executeSafely {
                    socket?.close()
                    socket = null
                    outputStream = null
                    _uiState.update { it.copy(connected = false) }
                }
                readerJob?.join()
            }.invokeOnCompletion { senderJob = null }
        }

    }

    private fun executeSafely(action: () -> Unit) {
        try {
            action()
        } catch (e: Exception) {
            e.printStackTrace()
            val errorMessage = "error:${e.javaClass.name} \nmessage:${e.message.orEmpty()}"
            println(errorMessage)
            _uiState.update { it.copy(errorMessage = errorMessage) }
        }
    }

    override fun onCleared() {
        closeSocket()
        viewModelScope.cancel()
    }
}