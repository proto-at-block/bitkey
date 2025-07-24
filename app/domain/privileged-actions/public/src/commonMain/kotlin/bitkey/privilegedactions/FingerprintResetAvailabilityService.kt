package bitkey.privilegedactions

import kotlinx.coroutines.flow.Flow

interface FingerprintResetAvailabilityService {
  fun isAvailable(): Flow<Boolean>
}
