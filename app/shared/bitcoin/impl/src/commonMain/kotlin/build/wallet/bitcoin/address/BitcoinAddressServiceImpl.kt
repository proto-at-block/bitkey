package build.wallet.bitcoin.address

import build.wallet.bitkey.account.FullAccount
import build.wallet.keybox.wallet.AppSpendingWalletProvider
import build.wallet.logging.logFailure
import build.wallet.notifications.RegisterWatchAddressContext
import build.wallet.queueprocessor.Processor
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.flatMap
import com.github.michaelbull.result.onSuccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull

class BitcoinAddressServiceImpl(
  private val registerWatchAddressProcessor: Processor<RegisterWatchAddressContext>,
  private val appSpendingWalletProvider: AppSpendingWalletProvider,
) : BitcoinAddressService, BitcoinRegisterWatchAddressWorker {
  private val addressCache = MutableStateFlow<AccountWithAddress?>(null)

  override suspend fun executeWork() {
    // Asynchronously register newly generated addresses to avoid performance degradation.
    addressCache
      .filterNotNull()
      .collect { accountWithAddress ->
        val account = accountWithAddress.account
        registerWatchAddressProcessor.process(
          RegisterWatchAddressContext(
            address = accountWithAddress.bitcoinAddress,
            f8eSpendingKeyset = account.keybox.activeSpendingKeyset.f8eSpendingKeyset,
            accountId = account.accountId.serverId,
            f8eEnvironment = account.config.f8eEnvironment
          )
        )
      }
  }

  override suspend fun generateAddress(account: FullAccount): Result<BitcoinAddress, Throwable> {
    return coroutineBinding {
      appSpendingWalletProvider
        .getSpendingWallet(account)
        .flatMap { it.getNewAddress() }
        .logFailure { "Failed to generate bitcoin address" }
        .onSuccess { address ->
          addressCache.emit(
            AccountWithAddress(
              account = account,
              bitcoinAddress = address
            )
          )
        }
        .bind()
    }
  }

  /**
   * A simple wrapper data class to couple an account and a newly generated address. Used instead of
   * retrieving the account dynamically when registering the address.
   */
  private data class AccountWithAddress(
    val account: FullAccount,
    val bitcoinAddress: BitcoinAddress,
  )
}
