package com.callrecorder.app.domain.model

import com.callrecorder.app.data.local.entity.CallCategory

/**
 * Filter criteria for filtering call records in the dashboard and search screens.
 */
data class CallFilter(
    val query: String = "",
    val category: CallCategory = CallCategory.ALL,
    val onlyKnown: Boolean = false,
    val onlyUnknown: Boolean = false,
    val onlyFavorites: Boolean = false
)
