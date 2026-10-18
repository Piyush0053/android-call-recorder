package com.callrecorder.app.domain.usecase

import com.callrecorder.app.domain.model.CallRecord
import com.callrecorder.app.domain.repository.CallRecordRepository

class SaveCallRecordUseCase(
    private val repository: CallRecordRepository
) {
    suspend operator fun invoke(record: CallRecord): Long {
        return repository.saveRecording(record)
    }
}
