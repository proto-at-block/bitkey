package build.wallet.nfc.platform

import build.wallet.bitkey.spending.SpendingKeyset

/**
 * Builds a [SweepSigningContext] from an OLD [SpendingKeyset] for W3 sweep signing.
 *
 * Extracts the 33-byte compressed pubkey + 32-byte chain code from the keyset's
 * app and server descriptor public keys at account depth 3, and pairs them with
 * the keyset's account index. Returns `null` when the caller does not need
 * sweep routing — i.e. when [oldKeyset] has the same account index as
 * [currentAccountIndex] (normal signing path applies).
 */
interface SweepSigningContextBuilder {
  fun buildFor(oldKeyset: SpendingKeyset, currentAccountIndex: UInt): SweepSigningContext?
}
