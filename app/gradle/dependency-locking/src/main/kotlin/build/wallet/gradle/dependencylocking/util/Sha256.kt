package build.wallet.gradle.dependencylocking.util

import org.gradle.internal.impldep.org.apache.commons.codec.digest.MessageDigestAlgorithms
import java.security.MessageDigest

internal fun ByteArray.toReadableSha256(): String {
  val messageDigest = MessageDigest.getInstance(MessageDigestAlgorithms.SHA_256)

  val hash = messageDigest.digest(this)

  return hash.joinToString("") { "%02x".format(it) }
}
