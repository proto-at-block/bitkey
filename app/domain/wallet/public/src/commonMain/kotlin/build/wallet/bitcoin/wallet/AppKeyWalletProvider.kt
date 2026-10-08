package build.wallet.bitcoin.wallet

import build.wallet.bitkey.spending.SpendingKeyset
import com.github.michaelbull.result.Result

/**
 * Provides a native-SegWit (BIP-84 `wpkh`), app-key-only wallet for a [SpendingKeyset].
 *
 * The provider couples the app key, network, and local wallet namespace to the keyset so callers
 * cannot accidentally reuse a BDK store for a different key. Unlike the normal 2-of-3 wallet, the
 * returned wallet can spend without a hardware or server co-signature.
 */
interface AppKeyWalletProvider {
  suspend fun getAppKeyWallet(
    keyset: SpendingKeyset,
  ): Result<AppKeyWallet, Throwable>
}
