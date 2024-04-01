package build.wallet.f8e.socrec.models

import build.wallet.bitkey.socrec.TrustedContactRecoveryPakeKey
import build.wallet.crypto.PublicKey
import build.wallet.encrypt.XCiphertext
import build.wallet.serialization.ByteStringAsHexSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okio.ByteString

@Serializable
internal data class RespondToChallengeRequestBody(
  @SerialName("trusted_contact_recovery_pake_pubkey")
  val trustedContactRecoveryPakePubkey: PublicKey<TrustedContactRecoveryPakeKey>,
  @SerialName("recovery_pake_confirmation")
  @Serializable(with = ByteStringAsHexSerializer::class)
  val recoveryPakeConfirmation: ByteString,
  @SerialName("resealed_dek")
  val resealedDek: XCiphertext,
)
