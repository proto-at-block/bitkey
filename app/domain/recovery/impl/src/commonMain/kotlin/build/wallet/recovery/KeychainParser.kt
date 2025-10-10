package build.wallet.recovery

import build.wallet.bitkey.app.AppAuthKey
import build.wallet.crypto.PrivateKey
import build.wallet.crypto.PublicKey
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.logging.logWarn
import okio.ByteString.Companion.decodeHex

/**
 * Parses iOS Keychain entries into structured key data for recovery operations.
 *
 * Separates parsing logic from recovery business logic, making it easier to test
 * and maintain key format handling independently.
 */
@BitkeyInject(AppScope::class)
class KeychainParser {
  private companion object {
    const val LOG_TAG = "[OrphanedKeyRecovery]"

    /** Minimum length for Base58-encoded BIP32 extended private keys (typically 111 chars) */
    const val MIN_XPRV_LENGTH = 111

    /** Valid BIP39 mnemonic word counts (128-256 bits entropy per BIP39 spec) */
    val VALID_MNEMONIC_WORD_COUNTS = setOf(12, 15, 18, 21, 24)

    /** BIP32 extended private key prefixes by network type */
    val XPRV_PREFIXES_PATTERN = Regex("^(xprv|tprv|yprv|zprv).*")

    /** BIP39 word pattern: lowercase letters only */
    val MNEMONIC_WORD_PATTERN = Regex("[a-z]+")

    /**
     * Matches BIP32 derivation path prefix like `[4357ec3d/84'/1'/0']`
     * followed by the extended private key. Captures the xprv in group 2.
     */
    val DERIVATION_PATH_PATTERN = Regex("""^\[([^\]]+)\](.+)$""")
  }

  /**
   * Parses keychain entries into structured key data.
   *
   * Categorizes entries into:
   * - Spending keys (xprv + mnemonic pairs identified by dpub)
   * - Auth keys (secp256k1 public/private key pairs)
   *
   * @param entries Raw keychain entries to parse
   * @return Structured keychain data with validated keys
   */
  fun parse(entries: List<KeychainScanner.KeychainEntry>): ParsedKeychain {
    val spendingKeysMap = mutableMapOf<String, SpendingKeyData>()
    val authKeys = mutableListOf<PublicKey<*>>()
    val authKeyPrivates = mutableMapOf<String, PrivateKey<out AppAuthKey>>()

    entries.forEach { entry ->
      when {
        entry.key.startsWith("secret-key:") ->
          parseSpendingKey(entry, spendingKeysMap)
        entry.key.startsWith("mnemonic:") ->
          parseMnemonic(entry, spendingKeysMap)
        else ->
          parseAuthKey(entry, authKeys, authKeyPrivates)
      }
    }

    return ParsedKeychain(
      spendingKeys = spendingKeysMap.values.toList(),
      authKeys = authKeys,
      authKeyPrivates = authKeyPrivates
    )
  }

  private fun parseSpendingKey(
    entry: KeychainScanner.KeychainEntry,
    spendingKeysMap: MutableMap<String, SpendingKeyData>,
  ) {
    val dpub = entry.key.removePrefix("secret-key:")

    if (dpub.isBlank() || entry.value.isBlank()) {
      logWarn { "$LOG_TAG Skipping invalid spending key entry: blank dpub or value" }
      return
    }

    // Strip optional derivation path prefix using regex
    val xprv = DERIVATION_PATH_PATTERN.matchEntire(entry.value)
      ?.groupValues?.get(2) // Extract xprv (group 2)
      ?: entry.value // No derivation path, use as-is

    if (!isValidXprv(xprv)) {
      logWarn {
        "$LOG_TAG Skipping invalid xprv format for dpub: $dpub"
      }
      return
    }
    val existing = spendingKeysMap[dpub] ?: SpendingKeyData(dpub)
    spendingKeysMap[dpub] = existing.copy(xprv = xprv)
  }

  private fun parseMnemonic(
    entry: KeychainScanner.KeychainEntry,
    spendingKeysMap: MutableMap<String, SpendingKeyData>,
  ) {
    val dpub = entry.key.removePrefix("mnemonic:")

    if (dpub.isBlank() || entry.value.isBlank()) {
      logWarn { "$LOG_TAG Skipping invalid mnemonic entry: blank dpub or value" }
      return
    }
    if (!isValidMnemonic(entry.value)) {
      logWarn {
        "$LOG_TAG Skipping invalid mnemonic format for dpub: $dpub"
      }
      return
    }
    val existing = spendingKeysMap[dpub] ?: SpendingKeyData(dpub)
    spendingKeysMap[dpub] = existing.copy(mnemonic = entry.value)
  }

  private fun parseAuthKey(
    entry: KeychainScanner.KeychainEntry,
    authKeys: MutableList<PublicKey<*>>,
    authKeyPrivates: MutableMap<String, PrivateKey<out AppAuthKey>>,
  ) {
    if (entry.key.isBlank() || entry.value.isBlank()) {
      logWarn { "$LOG_TAG Skipping invalid auth key entry: blank key or value" }
      return
    }
    try {
      val publicKeyBytes = entry.key.decodeHex()
      if (publicKeyBytes.size != 33 && publicKeyBytes.size != 65) {
        logWarn { "$LOG_TAG Skipping auth key with invalid public key length: ${publicKeyBytes.size}" }
        return
      }

      val privateKeyBytes = entry.value.decodeHex()
      if (privateKeyBytes.size != 32) {
        logWarn { "$LOG_TAG Skipping auth key with invalid private key length: ${privateKeyBytes.size}" }
        return
      }

      // Store as generic auth key - the actual type will be determined during authentication
      authKeys.add(PublicKey<AppAuthKey>(entry.key))
      authKeyPrivates[entry.key] = PrivateKey(privateKeyBytes)
    } catch (e: IllegalArgumentException) {
      // Intentionally skip invalid entries and continue processing
      logWarn(throwable = e) { "$LOG_TAG Skipping auth key with invalid hex encoding" }
    }
  }

  private fun isValidXprv(xprv: String): Boolean =
    xprv.matches(XPRV_PREFIXES_PATTERN) && xprv.length >= MIN_XPRV_LENGTH

  private fun isValidMnemonic(mnemonic: String): Boolean {
    val words = mnemonic.trim().split(Regex("\\s+"))
    return words.size in VALID_MNEMONIC_WORD_COUNTS &&
      words.all { it.isNotBlank() && it.matches(MNEMONIC_WORD_PATTERN) }
  }

  /**
   * Structured keychain data after parsing.
   */
  data class ParsedKeychain(
    val spendingKeys: List<SpendingKeyData>,
    val authKeys: List<PublicKey<*>>,
    val authKeyPrivates: Map<String, PrivateKey<out AppAuthKey>>,
  )

  /**
   * Spending key data with optional xprv and mnemonic components.
   */
  data class SpendingKeyData(
    val dpub: String,
    val xprv: String? = null,
    val mnemonic: String? = null,
  ) {
    val hasXprv: Boolean get() = xprv != null
    val hasMnemonic: Boolean get() = mnemonic != null
  }
}
