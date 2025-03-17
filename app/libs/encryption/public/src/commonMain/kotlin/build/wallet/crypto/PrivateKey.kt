package build.wallet.crypto

import dev.zacsweers.redacted.annotations.Redacted
import okio.ByteString
import kotlin.jvm.JvmInline

@Redacted
@JvmInline
@Suppress("Unused")
value class PrivateKey<T : KeyPurpose>(
  val bytes: ByteString,
)
