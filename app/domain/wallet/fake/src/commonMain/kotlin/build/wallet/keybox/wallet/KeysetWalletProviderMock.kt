package build.wallet.keybox.wallet

import build.wallet.bitcoin.wallet.WatchingWallet
import build.wallet.bitkey.keybox.SoftwareKeybox
import build.wallet.bitkey.spending.SpendingKeyset
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

/**
 * Mock implementation of [KeysetWalletProvider] that looks up wallets from provided maps.
 *
 * This mock supports lookup by both keyset localId and f8eSpendingKeyset keysetId, which is
 * useful for testing scenarios where keysets are referenced in different ways.
 *
 * @param wallets Map of keyset localId to WatchingWallet
 * @param walletsByF8eKeysetId Optional map of f8e keyset ID to WatchingWallet for fallback lookup
 * @param defaultWallet Optional default wallet to return if no specific wallet is found in the maps
 */
class KeysetWalletProviderMock(
  private val wallets: Map<String, WatchingWallet> = emptyMap(),
  private val walletsByF8eKeysetId: Map<String, WatchingWallet> = emptyMap(),
  private val defaultWallet: WatchingWallet? = null,
) : KeysetWalletProvider {
  override suspend fun getWatchingWallet(
    keyset: SpendingKeyset,
  ): Result<WatchingWallet, Throwable> {
    val wallet =
      wallets[keyset.localId]
        ?: walletsByF8eKeysetId[keyset.f8eSpendingKeyset.keysetId]
        ?: defaultWallet
        ?: error("No watching wallet mock found for keyset \"${keyset.localId}\"")
    return Ok(wallet)
  }

  override suspend fun getWatchingWallet(
    softwareKeybox: SoftwareKeybox,
  ): Result<WatchingWallet, Throwable> {
    val wallet =
      wallets[softwareKeybox.id]
        ?: walletsByF8eKeysetId[softwareKeybox.id]
        ?: defaultWallet
        ?: error("No watching wallet mock found for keybox \"${softwareKeybox.id}\"")
    return Ok(wallet)
  }
}
