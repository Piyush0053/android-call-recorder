package com.callrecorder.app.domain.usecase

import com.callrecorder.app.data.local.entity.CallCategory
import com.callrecorder.app.domain.repository.CallRecordRepository

class UpdateCallCategoryUseCase(
    private val repository: CallRecordRepository
) {
    suspend operator fun invoke(id: Long, category: CallCategory) {
        repository.updateCategory(id, category)
    }
}
