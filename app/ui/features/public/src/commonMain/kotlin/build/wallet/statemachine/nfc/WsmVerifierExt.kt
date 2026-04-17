package build.wallet.statemachine.nfc

import build.wallet.catchingResult
import build.wallet.crypto.WsmVerifier
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.wsmIntegrityKeyVariant
import build.wallet.logging.logError
import com.github.michaelbull.result.getOrElse

/**
 * Verifies a WSM signature over 5 public keys. Logs on failure but never throws or
 * blocks the caller -- following the same soft-check pattern used during account creation.
 *
 * @param context human-readable label for the log message (e.g. "build hardware descriptor")
 */
fun WsmVerifier.verifyPublicKeysOrLog(
  appAuthPubHex: String,
  hardwareAuthPubHex: String,
  appSpendingPubHex: String,
  hardwareSpendingPubHex: String,
  serverSpendingPubHex: String,
  signature: String,
  f8eEnvironment: F8eEnvironment,
  context: String = "build hardware descriptor",
) {
  val verified = catchingResult {
    verifyPublicKeys(
      appAuthPubHex = appAuthPubHex,
      hardwareAuthPubHex = hardwareAuthPubHex,
      appSpendingPubHex = appSpendingPubHex,
      hardwareSpendingPubHex = hardwareSpendingPubHex,
      serverSpendingPubHex = serverSpendingPubHex,
      signature = signature,
      keyVariant = f8eEnvironment.wsmIntegrityKeyVariant
    ).isValid
  }.getOrElse { false }

  if (!verified) {
    // Note: do not remove the '[wsm_integrity_failure]' from the message. We alert on this string in Datadog.
    logError {
      "[wsm_integrity_failure] WSM integrity signature verification failed for $context: " +
        "$signature : $appAuthPubHex : $hardwareAuthPubHex : " +
        "$appSpendingPubHex : $hardwareSpendingPubHex : $serverSpendingPubHex"
    }
  }
}
