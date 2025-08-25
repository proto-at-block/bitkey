package build.wallet.support

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okio.ByteString

/**
 * Represents an encrypted attachment that can be sent with a support ticket.
 *
 * @property encryptedAttachmentId The unique identifier for the encrypted attachment.
 * @property publicKey The public key used to encrypt the attachment.
 */
@Serializable
data class EncryptedAttachment(
  @SerialName("encrypted_attachment_id")
  val encryptedAttachmentId: String,
  @SerialName("public_key")
  val publicKey: ByteString,
)
