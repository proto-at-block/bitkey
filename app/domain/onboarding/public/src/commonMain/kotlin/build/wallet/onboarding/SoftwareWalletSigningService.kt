package build.wallet.onboarding

import build.wallet.bitcoin.transactions.Psbt
import build.wallet.frost.ShareDetails
import com.github.michaelbull.result.Result

/**
 * Service responsible for producing a FROST signature for a given PSBT.
 */
interface SoftwareWalletSigningService {
  @Suppress("ktlint:standard:no-consecutive-comments")
  /**
   * Takes in a Psbt type, and returns a base64-encoded, signed PSBT.
   */
  // TODO [W-10273]: Remove shareDetails parameter
  suspend fun sign(
    psbt: Psbt,
    shareDetails: ShareDetails,
  ): Result<Psbt, Throwable>
}
