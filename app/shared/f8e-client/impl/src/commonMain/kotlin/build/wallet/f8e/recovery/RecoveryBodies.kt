package build.wallet.f8e.recovery

import build.wallet.bitkey.app.AppGlobalAuthPublicKey
import build.wallet.bitkey.app.AppRecoveryAuthPublicKey
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.factor.PhysicalFactor
import build.wallet.bitkey.factor.PhysicalFactor.App
import build.wallet.bitkey.factor.PhysicalFactor.Hardware
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.encrypt.Secp256k1PublicKey
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.binding
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AuthKeypairBody(
  /**
   * [AppGlobalPublicKey] the app global auth pub key that will be used for the account
   * once the recovery is complete
   */
  @SerialName("app")
  val appGlobal: String,
  /**
   * [AppRecoveryPublicKey] the app recovery auth pub key that will be used for the account
   * once the recovery is complete
   */
  @SerialName("recovery")
  val appRecovery: String,
  /**
   * [hardwareAuthPubKey] the hardware auth pub key that will be used for the account
   * once the recovery is complete
   */
  @SerialName("hardware")
  val hardware: String,
)

@Serializable
data class ServerResponseBody(
  /**
   * [delayStartTime] the time in which the recovery started
   */
  @SerialName("delay_start_time")
  val delayStartTime: Instant,
  /**
   * [delayEndTime] the time in which the recovery ended
   */
  @SerialName("delay_end_time")
  val delayEndTime: Instant,
  /**
   * [lostFactor] the factor we are trying to recovery
   */
  @SerialName("lost_factor")
  val lostFactorStr: String,
  /**
   * [authKeyPair] destination auth keys used for the Recovery
   */
  @SerialName("auth_keys")
  val authKeyPair: AuthKeypairBody,
)

internal fun ServerResponseBody.toServerRecovery(
  fullAccountId: FullAccountId,
): Result<ServerRecovery, Throwable> {
  return binding {
    ServerRecovery(
      fullAccountId = fullAccountId,
      delayStartTime = delayStartTime,
      delayEndTime = delayEndTime,
      lostFactor = lostFactorStr.toPhysicalFactor().bind(),
      destinationAppGlobalAuthPubKey =
        AppGlobalAuthPublicKey(
          Secp256k1PublicKey(authKeyPair.appGlobal)
        ),
      destinationAppRecoveryAuthPubKey = AppRecoveryAuthPublicKey(Secp256k1PublicKey(authKeyPair.appRecovery)),
      destinationHardwareAuthPubKey = HwAuthPublicKey(Secp256k1PublicKey(authKeyPair.hardware))
    )
  }
}

private fun String.toPhysicalFactor(): Result<PhysicalFactor, IllegalArgumentException> {
  return when (this) {
    "App" -> Ok(App)
    "Hw" -> Ok(Hardware)
    else -> Err(IllegalArgumentException("Invalid lost_factor $this"))
  }
}

internal fun PhysicalFactor.toServerString(): String {
  return when (this) {
    App -> "App"
    Hardware -> "Hw"
  }
}
