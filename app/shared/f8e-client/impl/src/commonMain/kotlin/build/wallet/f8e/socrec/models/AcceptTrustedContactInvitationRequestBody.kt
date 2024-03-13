package build.wallet.f8e.socrec.models

import build.wallet.bitkey.socrec.TrustedContactEnrollmentPakeKey
import build.wallet.encrypt.XCiphertext
import build.wallet.serialization.ByteStringAsHexSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okio.ByteString

@Serializable
internal data class AcceptTrustedContactInvitationRequestBody(
  val action: String,
  val code: String,
  @SerialName("customer_alias")
  val customerAlias: String,
  @SerialName("trusted_contact_enrollment_pake_pubkey")
  val trustedContactEnrollmentPakeKey: TrustedContactEnrollmentPakeKey,
  @SerialName("enrollment_pake_confirmation")
  @Serializable(with = ByteStringAsHexSerializer::class)
  val enrollmentPakeConfirmation: ByteString,
  @SerialName("sealed_delegated_decryption_pubkey")
  val sealedDelegateDecryptionKey: XCiphertext,
) {
  constructor(
    code: String,
    customerAlias: String,
    trustedContactEnrollmentPakeKey: TrustedContactEnrollmentPakeKey,
    enrollmentPakeConfirmation: ByteString,
    sealedDelegateDecryptionKey: XCiphertext,
  ) : this(
    action = "Accept",
    code = code,
    customerAlias = customerAlias,
    trustedContactEnrollmentPakeKey = trustedContactEnrollmentPakeKey,
    enrollmentPakeConfirmation = enrollmentPakeConfirmation,
    sealedDelegateDecryptionKey = sealedDelegateDecryptionKey
  )
}
