package build.wallet.f8e.recovery

import bitkey.f8e.error.F8eError
import bitkey.f8e.error.code.HardwareAuthKeyAvailabilityErrorCode
import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.crypto.PublicKey
import build.wallet.f8e.F8eEnvironment
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class HardwareAuthKeyAvailabilityF8eClientFake : HardwareAuthKeyAvailabilityF8eClient {
  var checkAvailabilityResult:
    Result<HardwareAuthKeyAvailabilityStatus, F8eError<HardwareAuthKeyAvailabilityErrorCode>> =
      Ok(HardwareAuthKeyAvailabilityStatus.Available)

  val checkAvailabilityCalls = mutableListOf<CheckAvailabilityCall>()

  data class CheckAvailabilityCall(
    val f8eEnvironment: F8eEnvironment,
    val fullAccountId: FullAccountId,
    val hardwareAuthPublicKey: HwAuthPublicKey,
    val appAuthKey: PublicKey<AppGlobalAuthKey>,
  )

  override suspend fun checkAvailability(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    hardwareAuthPublicKey: HwAuthPublicKey,
    appAuthKey: PublicKey<AppGlobalAuthKey>,
  ): Result<HardwareAuthKeyAvailabilityStatus, F8eError<HardwareAuthKeyAvailabilityErrorCode>> {
    checkAvailabilityCalls += CheckAvailabilityCall(
      f8eEnvironment = f8eEnvironment,
      fullAccountId = fullAccountId,
      hardwareAuthPublicKey = hardwareAuthPublicKey,
      appAuthKey = appAuthKey
    )
    return checkAvailabilityResult
  }

  fun reset() {
    checkAvailabilityResult = Ok(HardwareAuthKeyAvailabilityStatus.Available)
    checkAvailabilityCalls.clear()
  }
}
