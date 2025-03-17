package build.wallet.encrypt

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.toUByteList
import okio.ByteString
import build.wallet.rust.core.SecretKey as CoreSecretKey

@BitkeyInject(AppScope::class)
class MessageSignerImpl : MessageSigner {
  @Deprecated(
    "Not actually deprecated, but please use signResult with checked exceptions.",
    replaceWith = ReplaceWith("this.signResult(message, key)")
  )
  override fun sign(
    message: ByteString,
    key: Secp256k1PrivateKey,
  ): String {
    val coreKey = CoreSecretKey(key.bytes.toUByteList())
    return coreKey.signMessage(message.toUByteList())
  }
}
