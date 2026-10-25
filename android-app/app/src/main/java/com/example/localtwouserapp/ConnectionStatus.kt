package com.example.localtwouserapp

object ConnectionStatus {
    fun disconnected(internetValidated: Boolean) = if (internetValidated)
        "Firebase unreachable · changes saved on this phone"
    else "No internet connection · changes saved on this phone"
}
