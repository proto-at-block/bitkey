package build.wallet.bitkey.inheritance

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Keys associated with an inheritance claim, to be used when claiming.
 */
@Serializable
data class InheritanceClaimKeyset(
  @SerialName("app_pubkey")
  val appPubkey: String,
  @SerialName("hardware_pubkey")
  val hardwarePubkey: String,
)
