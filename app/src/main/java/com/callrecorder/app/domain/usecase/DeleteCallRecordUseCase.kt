package com.callrecorder.app.domain.usecase

import com.callrecorder.app.domain.repository.CallRecordRepository

class DeleteCallRecordUseCase(
    private val repository: CallRecordRepository
) {
    suspend operator fun invoke(id: Long): Boolean {
        return repository.deleteRecording(id)
    }
}
