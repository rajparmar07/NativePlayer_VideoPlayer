package com.example.data

data class VideoModel(
    val id: String,
    val title: String,
    val urlOrPath: String,
    val duration: Long = 0,
    val size: Long = 0,
    val isLocal: Boolean = false,
    val isOffline: Boolean = false,
    val isStream: Boolean = false,
    val subtitleUrlOrPath: String? = null
)
