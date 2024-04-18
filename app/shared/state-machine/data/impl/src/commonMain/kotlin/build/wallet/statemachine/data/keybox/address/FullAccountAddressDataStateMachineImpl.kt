package build.wallet.statemachine.data.keybox.address

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.bitcoin.address.BitcoinAddress
import build.wallet.keybox.wallet.AppSpendingWalletProvider
import build.wallet.logging.logFailure
import build.wallet.notifications.RegisterWatchAddressContext
import build.wallet.queueprocessor.Processor
import com.github.michaelbull.result.flatMap
import com.github.michaelbull.result.onSuccess

class FullAccountAddressDataStateMachineImpl(
  private val registerWatchAddressProcessor: Processor<RegisterWatchAddressContext>,
  private val appSpendingWalletProvider: AppSpendingWalletProvider,
) : FullAccountAddressDataStateMachine {
  @Composable
  override fun model(props: FullAccountAddressDataProps): KeyboxAddressData {
    var latestAddress: BitcoinAddress? by remember { mutableStateOf(null) }

    latestAddress?.let { address ->
      LaunchedEffect(latestAddress) {
        registerWatchAddressProcessor.process(
          RegisterWatchAddressContext(
            address = address,
            f8eSpendingKeyset = props.account.keybox.activeSpendingKeyset.f8eSpendingKeyset,
            accountId = props.account.accountId.serverId,
            f8eEnvironment = props.account.config.f8eEnvironment
          )
        )
      }
    }

    return KeyboxAddressData(
      latestAddress = latestAddress,
      generateAddress = {
        appSpendingWalletProvider
          .getSpendingWallet(props.account)
          .flatMap { it.getNewAddress() }
          .logFailure { "Failed to generate bitcoin address" }
          .onSuccess {
            latestAddress = it
          }
      }
    )
  }
}
