package com.jp.ray.videostreamer.modle

sealed class ConnectionStatus(val newState:Int) {
    class Connected(newState: Int) : ConnectionStatus(newState)
    class Disconnected(newState: Int):ConnectionStatus(newState)
}