package com.callrecorder.app.domain.usecase

import com.callrecorder.app.domain.model.CallRecord
import com.callrecorder.app.domain.repository.CallRecordRepository
import kotlinx.coroutines.flow.Flow

class SearchCallRecordsUseCase(
    private val repository: CallRecordRepository
) {
    operator fun invoke(query: String): Flow<List<CallRecord>> {
        return repository.searchRecordings(query)
    }
}
