package com.jp.ray.videostreamer.modle

sealed class ConnectionStatus {
    data object Connected:ConnectionStatus()
    class Disconnected:ConnectionStatus()
}