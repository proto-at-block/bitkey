package build.wallet.nfc

import build.wallet.bitcoin.keys.extractAccountIndex
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.logging.logError
import build.wallet.nfc.platform.SweepSigningContext
import build.wallet.nfc.platform.SweepSigningContextBuilder
import build.wallet.nfc.platform.SweepXpub
import okio.ByteString.Companion.toByteString

/**
 * Builds [SweepSigningContext] from a stored [SpendingKeyset].
 *
 * Extracts the 33-byte compressed pubkey + 32-byte chain code from the app
 * and server descriptor public keys (both at BIP-84 account depth 3) by
 * base58check-decoding the embedded xpub and reading the BIP-32 layout
 * directly. Pure Kotlin + okio — no FFI dependency — so the same impl works
 * on Android, iOS, and JVM.
 *
 * Returns `null` when the source keyset's account index equals the current
 * account index (no sweep routing needed - caller should use the normal
 * signing path) or when a descriptor xpub cannot be decoded (caller falls
 * back to normal signing; firmware will reject non-current-account inputs
 * defensively).
 */
@BitkeyInject(AppScope::class)
class SweepSigningContextBuilderImpl : SweepSigningContextBuilder {
  override fun buildFor(
    oldKeyset: SpendingKeyset,
    currentAccountIndex: UInt,
  ): SweepSigningContext? {
    // The HW key's derivation path is the one that increments across
    // account-bumping recoveries (m/84'/coin'/N' with N varying); the app
    // key is always at account index 0. Use the HW key on both sides of
    // the comparison (the caller passes a currentAccountIndex extracted
    // from the active keyset's hardwareKey for the same reason).
    val oldAccountIndex = oldKeyset.hardwareKey.key.extractAccountIndex()
    if (oldAccountIndex == currentAccountIndex) {
      return null
    }

    // The server xpub registered with W3 firmware (via
    // verifyKeysAndBuildDescriptor / HardwareDescriptorDeliveryServiceImpl)
    // is f8eSpendingKeyset.privateWalletRootXpub — the server's ROOT xpub
    // that firmware derives from via non-hardened chain-code delegation
    // ([84, coin, 0, change, addr] off the stored xpub). The derived
    // spendingPublicKey.key.xpub is a DIFFERENT xpub and using it here
    // would cause HW to compute sighashes over the wrong witness script,
    // producing signatures the server side can't combine with — the tx
    // would fail to broadcast even after everyone signs.
    //
    // Only private-wallet keysets carry a root xpub. W3 onboarding
    // requires private wallets (see HardwareDescriptorDeliveryServiceImpl),
    // so all W3 sweeps have this field populated; non-private legacy
    // sweeps go through the W3Upgrade path and are excluded upstream.
    val oldServerRootXpub = oldKeyset.f8eSpendingKeyset.privateWalletRootXpub
      ?: return null.also {
        logError(tag = "NFC") {
          "Cannot build SweepSigningContext: source keyset has no privateWalletRootXpub"
        }
      }

    return try {
      SweepSigningContext(
        oldAccountIndex = oldAccountIndex,
        oldAppXpub = decodeXpubMaterial(oldKeyset.appKey.key.xpub),
        oldServerXpub = decodeXpubMaterial(oldServerRootXpub)
      )
    } catch (e: IllegalArgumentException) {
      // Also covers NumberFormatException (a subclass) from xpub decoding.
      logError(tag = "NFC", throwable = e) { "Failed to build SweepSigningContext from keyset" }
      null
    }
  }
}

/**
 * Decode a base58check-encoded BIP-32 xpub string and extract its
 * 33-byte compressed public key and 32-byte chain code.
 *
 * BIP-32 serialized extended public key layout (78 bytes, pre-checksum):
 *
 *   offset size field
 *   0      4    version
 *   4      1    depth
 *   5      4    parent fingerprint
 *   9      4    child number
 *   13     32   chain code
 *   45     33   public key
 */
internal fun decodeXpubMaterial(xpub: String): SweepXpub {
  val payload = base58CheckDecode(xpub)
  require(payload.size == 78) {
    "xpub payload must be 78 bytes, got ${payload.size}"
  }
  val chaincode = payload.copyOfRange(13, 45)
  val pubkey = payload.copyOfRange(45, 78)
  return SweepXpub(
    pubkey = pubkey.toByteString(),
    chaincode = chaincode.toByteString()
  )
}

/**
 * Decode a base58check-encoded string (base58-decode, then strip and verify
 * the 4-byte double-SHA256 checksum suffix).
 *
 * Throws [IllegalArgumentException] on checksum mismatch or length issues,
 * [NumberFormatException] on invalid base58 characters.
 */
internal fun base58CheckDecode(encoded: String): ByteArray {
  val raw = base58Decode(encoded)
  require(raw.size >= CHECKSUM_SIZE) {
    "base58check payload too short: ${raw.size}"
  }
  val payloadLen = raw.size - CHECKSUM_SIZE
  val payload = raw.copyOfRange(0, payloadLen)
  val suppliedChecksum = raw.copyOfRange(payloadLen, raw.size)
  val computedChecksum = payload.toByteString()
    .sha256()
    .sha256()
    .toByteArray()
    .copyOfRange(0, CHECKSUM_SIZE)
  require(suppliedChecksum.contentEquals(computedChecksum)) {
    "base58check checksum mismatch"
  }
  return payload
}

private const val CHECKSUM_SIZE = 4
private const val BASE58_ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
private val base58Indices = IntArray(128) { BASE58_ALPHABET.indexOf(it.toChar()) }

/**
 * Decode a base58 string to bytes. Adapted from the Base58 impl in
 * emergency-exit-kit (MIT-licensed); simplified to be synchronous and
 * local to this module to keep the dependency footprint small.
 */
@Throws(NumberFormatException::class)
private fun base58Decode(input: String): ByteArray {
  if (input.isEmpty()) return ByteArray(0)

  // Convert ASCII chars to base58 digit bytes
  val input58 = ByteArray(input.length)
  for (i in input.indices) {
    val c = input[i]
    val digit = if (c.code < 128) base58Indices[c.code] else -1
    if (digit < 0) {
      throw NumberFormatException("Illegal base58 character '$c' at position $i")
    }
    input58[i] = digit.toByte()
  }

  // Count leading zeros (encoded as '1')
  var zeros = 0
  while (zeros < input58.size && input58[zeros].toInt() == 0) {
    zeros++
  }

  // Convert base-58 digits to base-256 digits (long division).
  val decoded = ByteArray(input.length)
  var outputStart = decoded.size
  var inputStart = zeros
  while (inputStart < input58.size) {
    decoded[--outputStart] = base58Divmod(input58, inputStart, 58, 256).toByte()
    if (input58[inputStart].toInt() == 0) {
      inputStart++
    }
  }

  // Skip extra leading zero bytes added during the calculation.
  while (outputStart < decoded.size && decoded[outputStart].toInt() == 0) {
    outputStart++
  }

  return decoded.copyOfRange(outputStart - zeros, decoded.size)
}

private fun base58Divmod(number: ByteArray, firstDigit: Int, base: Int, divisor: Int): Int {
  var remainder = 0
  for (i in firstDigit until number.size) {
    val digit = number[i].toInt() and 0xff
    val temp = remainder * base + digit
    number[i] = (temp / divisor).toByte()
    remainder = temp % divisor
  }
  return remainder
}
