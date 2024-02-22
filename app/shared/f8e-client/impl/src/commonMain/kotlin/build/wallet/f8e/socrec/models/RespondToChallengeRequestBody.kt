package build.wallet.f8e.socrec.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class RespondToChallengeRequestBody(
  @SerialName("shared_secret_ciphertext")
  val sharedSecretCiphertext: String,
)
