package build.wallet.f8e.onboarding.model

import bitkey.account.HardwareType
import build.wallet.bitkey.hardware.HwAttestationCertificate
import build.wallet.bitkey.hardware.HwSpendingKeyProof
import build.wallet.ktor.result.RedactedRequestBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CreateAccountV2RequestBody(
  val auth: FullCreateAccountV2AuthKeys,
  @SerialName("is_test_account")
  val isTestAccount: Boolean?,
  val spend: FullCreateAccountV2SpendingKeys,
) : RedactedRequestBody

@Serializable
data class FullCreateAccountV2AuthKeys(
  /** The global app auth key, corresponds to [AppGlobalAuthPublicKey] */
  @SerialName("app_pub")
  val appGlobalAuthPublicKey: String,
  /** The hardware auth key. */
  @SerialName("hardware_pub")
  val hardwareAuthPublicKey: String,
  /**
   * The recovery app auth key, corresponds to [AppRecoveryAuthPublicKey]
   * Used when the account does not have access to  [AppGlobalAuthPublicKey].
   */
  @SerialName("recovery_pub")
  val recoveryAuthPublicKey: String?,
  @SerialName("hardware_type")
  val hardwareType: HardwareType,
)

@Serializable
data class FullCreateAccountV2SpendingKeys(
  /** The app spending key, corresponds to [AppSpendingPublicKey] */
  @SerialName("app_pub")
  val app: String,
  /** The hardware spending key, corresponds to [HwSpendingPublicKey] */
  @SerialName("hardware_pub")
  val hardware: String,
  /**The bitcoin network these keys were created on. */
  val network: String,
  /**
   * Optional device-identity-key attestation binding [hardware] to a specific
   * Bitkey unit. Omitted when the hardware is on an older version without attestation.
   */
  @SerialName("hardware_attestation")
  val hardwareAttestation: HardwareAttestationBody? = null,
)

/**
 * Wire shape of `HardwareAttestation` on f8e: ECDSA signature over
 * `b"HWV1" || hardware_pub` plus the device cert chain (leaf → root-ish).
 * `signature` and each `cert_chain` entry are base64-encoded.
 */
@Serializable
data class HardwareAttestationBody(
  val signature: String,
  @SerialName("cert_chain")
  val certChain: List<String>,
)

internal fun HwSpendingKeyProof.toF8eBody(): HardwareAttestationBody =
  HardwareAttestationBody(
    signature = signature.value,
    certChain = certChain.map(HwAttestationCertificate::value)
  )
