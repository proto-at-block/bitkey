package build.wallet.encrypt

import okio.ByteString

/**
 * Fake [MessageSigner] implementation which slightly modifies original message
 * and encodes it with utf-8 without actual signing.
 */
class MessageSignerFake : MessageSigner {
  @Deprecated(
    "Not actually deprecated, but please use signResult with checked exceptions.",
    replaceWith = ReplaceWith("this.signResult(message, key)")
  )
  override fun sign(
    message: ByteString,
    key: Secp256k1PrivateKey,
  ): String {
    return buildString {
      append("signed-")
      append(message.utf8())
    }
  }
}
