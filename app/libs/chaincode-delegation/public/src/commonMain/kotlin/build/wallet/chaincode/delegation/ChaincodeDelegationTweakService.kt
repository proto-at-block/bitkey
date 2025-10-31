package build.wallet.chaincode.delegation

import build.wallet.bitcoin.keys.ExtendedPrivateKey
import build.wallet.bitcoin.transactions.Psbt
import build.wallet.bitkey.spending.SpendingKeyset
import com.github.michaelbull.result.Result

/**
 * Service for performing tweaks on a given Psbt for chaincode delegated signing
 *
 * These functions map 1:1 by name to the rust bindings in PsbtUtils
 */
interface ChaincodeDelegationTweakService {
  /**
   * Returns a tweaked version of the given PSBT for chaincode delegated signing, using the currently
   * active spending keyset.
   *
   * @param psbt The original, untweaked PSBT. This should be a valid PSBT that has not yet had any chaincode delegation tweaks applied.
   * @return A [Result] containing the tweaked [Psbt] on success, or a [ChaincodeDelegationError] on failure.
   */
  suspend fun psbtWithTweaks(psbt: Psbt): Result<Psbt, ChaincodeDelegationError>

  /**
   * Returns a tweaked version of the given PSBT for chaincode delegated signing.
   *
   * @param psbt The original, untweaked PSBT. This should be a valid PSBT that has not yet had any chaincode delegation tweaks applied.
   * @param appSpendingPrivateKey The spending private key to use for tweaking. This should be the private key
   * corresponding to the public key in [spendingKeyset].
   * @param spendingKeyset The spending keyset to use for tweaking. This should be a valid private wallet keyset.
   * @return A [Result] containing the tweaked [Psbt] on success, or a [ChaincodeDelegationError] on failure.
   */
  suspend fun psbtWithTweaks(
    psbt: Psbt,
    appSpendingPrivateKey: ExtendedPrivateKey,
    spendingKeyset: SpendingKeyset,
  ): Result<Psbt, ChaincodeDelegationError>

  /**
   * Applies tweaks to a PSBT for sweeping from a private source keyset to a private destination keyset.
   *
   * @param psbt The original, untweaked PSBT.
   * @param sourceKeyset The source keyset (must be a private wallet keyset).
   * @param destinationKeyset The destination keyset (must be a private wallet keyset).
   * @return A [Result] containing the tweaked [Psbt] on success, or a [ChaincodeDelegationError] on failure.
   */
  suspend fun sweepPsbtWithTweaks(
    psbt: Psbt,
    sourceKeyset: SpendingKeyset,
    destinationKeyset: SpendingKeyset,
  ): Result<Psbt, ChaincodeDelegationError>

  /**
   * Applies tweaks to a PSBT for sweeping from a legacy keyset to a private destination keyset.
   *
   * @param psbt The original, untweaked PSBT.
   * @param destinationKeyset The destination keyset (must be a private wallet keyset).
   * @return A [Result] containing the tweaked [Psbt] on success, or a [ChaincodeDelegationError] on failure.
   */
  suspend fun migrationSweepPsbtWithTweaks(
    psbt: Psbt,
    destinationKeyset: SpendingKeyset,
  ): Result<Psbt, ChaincodeDelegationError>
}
