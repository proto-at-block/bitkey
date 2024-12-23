package build.wallet.bitcoin.export

import build.wallet.account.AccountService
import build.wallet.bitcoin.wallet.WatchingWalletDescriptor
import build.wallet.bitkey.account.FullAccount
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.ensure
import build.wallet.keybox.wallet.WatchingWalletDescriptorProvider
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import kotlinx.coroutines.flow.first

@BitkeyInject(AppScope::class)
class ExportWatchingDescriptorServiceImpl(
  private val accountService: AccountService,
  private val watchingWalletDescriptorProvider: WatchingWalletDescriptorProvider,
) : ExportWatchingDescriptorService {
  override suspend fun formattedActiveWalletDescriptorString(): Result<String, Throwable> =
    coroutineBinding {
      val descriptor = exportDescriptor().bind()

      "External: ${descriptor.receivingDescriptor.raw}\n\nInternal: ${descriptor.changeDescriptor.raw}"
    }

  private suspend fun exportDescriptor(): Result<WatchingWalletDescriptor, Throwable> =
    coroutineBinding {
      val account = accountService.activeAccount().first()
      ensure(account is FullAccount) { Error("No active full account present.") }

      val keyset = account.keybox.activeSpendingKeyset
      watchingWalletDescriptorProvider.walletDescriptor(keyset = keyset).bind()
    }
}
