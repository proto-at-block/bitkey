package build.wallet.nfc.transaction

import bitkey.data.PrivateData
import build.wallet.bitkey.challange.DelayNotifyChallenge
import build.wallet.bitkey.challange.SignedChallenge.HardwareSignedChallenge
import build.wallet.cloud.backup.csek.*
import build.wallet.nfc.NfcSession
import build.wallet.nfc.platform.NfcCommands
import build.wallet.nfc.platform.SignChallengeAndSealSeksResult
import build.wallet.nfc.transaction.SignChallengeAndSealSeks.SignedChallengeAndSeks

/**
 * W3 variant of [SignChallengeAndSealSeks].
 *
 * Generates fresh CSEK and SSEK locally, then calls the single composite
 * [NfcCommands.signChallengeAndSealSeks] command which signs the D&N challenge
 * and seals both keys in one confirmable NFC tap. Returns the same
 * [SignChallengeAndSealSeks.SignedChallengeAndSeks] result so callers can persist
 * the raw + sealed key pairs identically to the W1 path.
 */
class W3SignChallengeAndSealSeks(
  private val challenge: DelayNotifyChallenge,
  private val csek: Csek,
  private val ssek: Ssek,
  private val success: suspend (SignedChallengeAndSeks) -> Unit,
  private val failure: () -> Unit,
) {
  @OptIn(PrivateData::class)
  fun toConfirmable(): RecoveryNfcSession.Confirmable<SignChallengeAndSealSeksResult> {
    return RecoveryNfcSession.Confirmable(
      session = { session: NfcSession, commands: NfcCommands ->
        commands.signChallengeAndSealSeks(
          session = session,
          challenge = challenge.asByteString(),
          unsealedCsek = csek.key.raw,
          unsealedSsek = ssek.key.raw
        )
      },
      onSuccess = { result ->
        success(
          SignedChallengeAndSeks(
            signedChallenge = HardwareSignedChallenge(
              challenge = challenge,
              signature = result.signedChallenge
            ),
            csek = csek,
            ssek = ssek,
            sealedCsek = result.sealedCsek,
            sealedSsek = result.sealedSsek
          )
        )
      },
      onCancel = failure
    )
  }
}
