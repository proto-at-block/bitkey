package build.wallet.bitkey.hardware

import build.wallet.bitkey.relationships.TrustedContactKeyCertificate
import build.wallet.logging.*
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/**
 * [AppGlobalAuthPublicKey] signed with [HwAuthPublicKey]'s private key. This is done through
 * a hardware tap. This signature has the same lifecycle as [AppGlobalAuthPublicKey] and
 * [HwAuthPublicKey]. Meaning that if the [AppGlobalAuthPublicKey] or [HwAuthPublicKey] are rotated,
 * a new signature needs to be created using new [AppGlobalAuthPublicKey] and a [HwAuthPublicKey].
 *
 * This signature is used by a PC app to be able to verify the authenticity of a TC's key
 * certificate.
 *
 * See [TrustedContactKeyCertificate] for more details.
 */
@JvmInline
@Serializable
value class AppGlobalAuthKeyHwSignature(val value: String) {
  init {
    // TODO(BKR-991):
    if (value.isBlank()) {
      logError {
        "AppGlobalAuthKeyHwSignature must not be blank. " +
          "An outdated database schema is likely used, reinstall the app."
      }
    }
  }

  companion object {
    /**
     * Sentinel value used to indicate that this signature was not obtained from hardware.
     * This is used during orphaned key recovery when reconstructing a keybox from app keys
     * that were found in the iOS Keychain after app deletion/reinstallation, where the
     * hardware signature is unavailable.
     */
    const val ORPHANED_KEY_RECOVERY_SENTINEL = "orphaned-key-recovery-sentinel"
  }
}
