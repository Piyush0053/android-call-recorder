package com.callrecorder.app.domain.usecase

import com.callrecorder.app.domain.repository.CallRecordRepository

class ToggleFavoriteUseCase(
    private val repository: CallRecordRepository
) {
    suspend operator fun invoke(id: Long, isFavorite: Boolean) {
        repository.toggleFavorite(id, isFavorite)
    }
}
