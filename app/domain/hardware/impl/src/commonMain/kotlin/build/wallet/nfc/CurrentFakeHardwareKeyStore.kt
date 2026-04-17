package build.wallet.nfc

import bitkey.account.AccountConfigService
import bitkey.account.HardwareType
import build.wallet.account.AccountService
import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.spending.SpendingKeypair
import build.wallet.bitkey.spending.SpendingPrivateKey
import build.wallet.bitkey.spending.SpendingPublicKey
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.di.W1
import build.wallet.di.W3
import kotlinx.coroutines.flow.first

/**
 * Delegates to the W1 or W3 [FakeHardwareKeyStore] based on the current account's
 * [HardwareType] configuration. Most consumers should inject unqualified
 * [FakeHardwareKeyStore] to get "the current hardware's key store" without needing
 * to know which generation is active.
 *
 * Resolves hardware type from the active account's keybox config first, falling back
 * to [AccountConfigService.defaultConfig] when no account is active (e.g., during
 * onboarding).
 *
 * Use `@W1` or `@W3` qualified injection only when you explicitly need a specific
 * generation's key store (e.g., during the W3 upgrade flow where both are active).
 */
@BitkeyInject(AppScope::class)
class CurrentFakeHardwareKeyStore(
  @W1 private val w1Store: FakeHardwareKeyStore,
  @W3 private val w3Store: FakeHardwareKeyStore,
  private val accountService: AccountService,
  private val accountConfigService: AccountConfigService,
) : FakeHardwareKeyStore {
  private suspend fun current(): FakeHardwareKeyStore {
    val hardwareType = (accountService.activeAccount().first() as? FullAccount)
      ?.config?.hardwareType
      ?: accountConfigService.defaultConfig().value.hardwareType
    return when (hardwareType) {
      HardwareType.W1, null -> w1Store
      HardwareType.W3 -> w3Store
    }
  }

  override suspend fun getSeed() = current().getSeed()

  override suspend fun setSeed(words: FakeHardwareKeyStore.Seed) = current().setSeed(words)

  override suspend fun getAuthKeypair() = current().getAuthKeypair()

  override suspend fun getInitialSpendingKeypair(network: BitcoinNetworkType): SpendingKeypair =
    current().getInitialSpendingKeypair(network)

  override suspend fun getNextSpendingKeypair(
    existingDescriptorPublicKeys: List<String>,
    network: BitcoinNetworkType,
  ): SpendingKeypair = current().getNextSpendingKeypair(existingDescriptorPublicKeys, network)

  override suspend fun getSpendingPrivateKey(
    pubKey: SpendingPublicKey,
    network: BitcoinNetworkType,
  ): SpendingPrivateKey = current().getSpendingPrivateKey(pubKey, network)

  override suspend fun clear() = current().clear()
}
