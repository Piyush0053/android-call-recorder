package com.callrecorder.app.domain.usecase

import com.callrecorder.app.domain.model.CallFilter
import com.callrecorder.app.domain.model.CallRecord
import com.callrecorder.app.domain.repository.CallRecordRepository
import kotlinx.coroutines.flow.Flow

class GetCallRecordsUseCase(
    private val repository: CallRecordRepository
) {
    operator fun invoke(filter: CallFilter = CallFilter()): Flow<List<CallRecord>> {
        return repository.getRecordings(filter)
    }
}
