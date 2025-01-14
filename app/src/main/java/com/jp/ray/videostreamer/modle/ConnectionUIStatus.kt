package com.jp.ray.videostreamer.modle

sealed class ConnectionUIStatus {
    object Idle:ConnectionUIStatus()
    object Connecting:ConnectionUIStatus()
    object FailedToConnect:ConnectionUIStatus()
    object Connected:ConnectionUIStatus()
    object CancelConnecting:ConnectionUIStatus()
    object Disconnecting:ConnectionUIStatus()
    object DisconnectedByPair:ConnectionUIStatus()
}