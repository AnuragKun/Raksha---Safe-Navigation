package com.arlabs.raksha.domain.repository

import com.arlabs.raksha.domain.util.Result

interface SosRepository {
    suspend fun triggerSos(
        latitude: Double?,
        longitude: Double?
    ): Result<Unit>
}
