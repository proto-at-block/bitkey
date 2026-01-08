package build.wallet.bitcoin.address

import bitkey.notifications.NotificationsPreferencesCachedProvider
import bitkey.recovery.DescriptorBackupService
import build.wallet.account.AccountService
import build.wallet.account.getAccount
import build.wallet.bdk.bindings.BdkAddressIndex
import build.wallet.bitcoin.transactions.BitcoinWalletService
import build.wallet.bitkey.account.FullAccount
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.ensureNotNull
import build.wallet.logging.logFailure
import build.wallet.notifications.RegisterWatchAddressContext
import build.wallet.notifications.RegisterWatchAddressProcessor
import build.wallet.queueprocessor.process
import build.wallet.recovery.keyset.SpendingKeysetRepairService
import build.wallet.recovery.keyset.SpendingKeysetSyncStatus
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.fold
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

@BitkeyInject(AppScope::class)
class BitcoinAddressServiceImpl(
  private val registerWatchAddressProcessor: RegisterWatchAddressProcessor,
  private val bitcoinWalletService: BitcoinWalletService,
  private val accountService: AccountService,
  private val notificationsPreferencesCachedProvider: NotificationsPreferencesCachedProvider,
  private val descriptorBackupService: DescriptorBackupService,
  private val spendingKeysetRepairService: SpendingKeysetRepairService,
) : BitcoinAddressService, BitcoinRegisterWatchAddressWorker {
  private val addressCache = MutableStateFlow<AccountWithAddress?>(null)

  override suspend fun executeWork() {
    // Asynchronously register newly generated addresses to avoid performance degradation.
    addressCache
      .filterNotNull()
      .collect { accountWithAddress ->
        if (isMoneyMovementNotificationEnabled()) {
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
  }

  private suspend fun isMoneyMovementNotificationEnabled(): Boolean {
    val notificationResult = notificationsPreferencesCachedProvider
      .getNotificationsPreferences()
      .first()

    return notificationResult
      ?.fold(
        success = { preferences -> preferences.moneyMovement.isNotEmpty() },
        failure = { false }
      ) ?: false
  }

  override suspend fun generateAddress(
    addressIndex: BdkAddressIndex,
  ): Result<BitcoinAddress, Throwable> {
    return coroutineBinding {
      val account = accountService.getAccount<FullAccount>().bind()

      // Check keyset sync status first - block address generation if mismatch detected
      when (val syncStatus = spendingKeysetRepairService.syncStatus.value) {
        is SpendingKeysetSyncStatus.Mismatch -> {
          Err(
            KeysetMismatchError(
              message = "Cannot generate address: local keyset doesn't match server",
              localKeysetId = syncStatus.localActiveKeysetId,
              serverKeysetId = syncStatus.serverActiveKeysetId
            )
          ).bind()
        }
        else -> {} // Continue with address generation
      }

      // Check descriptor backup health
      val activeSpendingKeyset = account.keybox.activeSpendingKeyset
      if (activeSpendingKeyset.isPrivateWallet) {
        descriptorBackupService
          .checkBackupForPrivateKeyset(activeSpendingKeyset.f8eSpendingKeyset.keysetId)
          .bind()
      }

      val wallet = bitcoinWalletService.spendingWallet().value
      ensureNotNull(wallet) { Error("No spending wallet found.") }
      val address = when (addressIndex) {
        BdkAddressIndex.LastUnused -> wallet.getLastUnusedAddress().bind()
        BdkAddressIndex.New -> wallet.getNewAddress().bind()
        is BdkAddressIndex.Peek -> wallet.peekAddress(addressIndex.index).bind()
      }
      addressCache.emit(AccountWithAddress(account = account, bitcoinAddress = address))
      address
    }.logFailure { "Error generating bitcoin address." }
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
