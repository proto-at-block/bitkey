package bitkey.verification

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TxVerificationApproval(
  val version: Int,
  @SerialName("hw_auth_public_key")
  val hwAuthPublicKey: String,
  val commitment: String,
  val signature: String,
  @SerialName("reverse_hash_chain")
  val reverseHashChain: List<String>,
)
