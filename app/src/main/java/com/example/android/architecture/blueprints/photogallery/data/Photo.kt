package com.example.architecture.blueprints.photogallery.data

data class Photo(
    val id: String,
    val url: String,
    val thumbnail: String,
    val title: String,
    var isFavorite: Boolean = false
)