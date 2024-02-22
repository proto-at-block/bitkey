package build.wallet.bitcoin.keys

import build.wallet.bitcoin.keys.DescriptorPublicKey.Origin
import build.wallet.bitcoin.keys.DescriptorPublicKey.Wildcard.Unhardened

private val xpub =
  "xpub6Gxgx4jtKP3xsM95Rtub11QE4YqGDxTw9imtJ23Bi7nFi2aqE27HwanX2x3m451zuni5tKSuHeFVHexyCkjDEwB74R7NRtQ2UryVKDy1fgK"

/**
 * Mock instance of [DescriptorPublicKey] identifiable by [identifier].
 *
 * [identifier] will replace last part of [xpub] while maintaining [xpub]'s valid length,
 * this way [DescriptorPublicKey.dpub] of this instance is still valid for parsing.
 */
@Suppress("FunctionName")
fun DescriptorPublicKeyMock(
  identifier: String,
  fingerprint: String = "e5ff120e",
): DescriptorPublicKey {
  // A trick to keep dpub valid for parsing while embedding an identifier for testing purposes.
  val modifiedXpub = xpub.replaceLastPart(value = identifier.replace("-", ""))
  return DescriptorPublicKey(
    origin =
      Origin(
        fingerprint = fingerprint,
        derivationPath = "/84'/0'/0'"
      ),
    derivationPath = "/*",
    xpub = modifiedXpub,
    wildcard = Unhardened
  )
}

/**
 * Replaces the last part of the caller string with the provided [value] while maintaining the
 * original length.
 *
 * If [value] is longer than the caller string, the result is truncated to match the original length.
 *
 * @param value The string that will replace the last part of the caller string.
 * @return The modified string with the last part replaced by [value].
 */
private fun String.replaceLastPart(value: String): String {
  return when {
    value.length >= this.length -> value.take(this.length)
    else -> this.dropLast(value.length) + value
  }
}
