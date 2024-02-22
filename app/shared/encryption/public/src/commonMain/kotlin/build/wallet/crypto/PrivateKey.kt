package build.wallet.crypto

import dev.zacsweers.redacted.annotations.Redacted
import okio.ByteString
import kotlin.jvm.JvmInline

@Redacted
@JvmInline
value class PrivateKey(
  val bytes: ByteString,
)
