package com.jp.ray.videostreamer

import android.bluetooth.BluetoothDevice
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.BlockingQueue
import kotlin.jvm.Throws

interface MessageParser<T> {
    @Throws(IOException::class)
    fun parseMessage(data: InputStream):T
}

interface MessageHandler<T> {
    fun onMessage(message: T)
}

class BoolMessageParser:MessageParser<Boolean> {
    override fun parseMessage(data: InputStream): Boolean {
        val read = data.read()
        if (read == -1) {
            throw EOFException()
        } else if (read == 0) {
            return false
        } else {
            return true
        }
    }
}

class SimplePipeMessageHandler<T>(private val blockingQueue: BlockingQueue<T>):MessageHandler<T> {
    override fun onMessage(message: T) {
        blockingQueue.put(message)

    }
}
