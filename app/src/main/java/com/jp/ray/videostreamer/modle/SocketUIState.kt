package com.jp.ray.videostreamer.modle

data class SocketUIState(
    val host: String = "",
    val port: String = "1",
    val errorMessage: String = "",
    val connected: Boolean = false
)