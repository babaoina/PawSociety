package com.example.pawsociety.data

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object FavoritesManager {
    private val _favoriteChanged = MutableSharedFlow<String>() // postId that changed
    val favoriteChanged = _favoriteChanged.asSharedFlow()

    suspend fun notifyFavoriteChanged(postId: String) {
        _favoriteChanged.emit(postId)
    }
}