package build.wallet.statemachine.partnerships.transfer

import build.wallet.bitcoin.address.BitcoinAddress
import build.wallet.bitkey.keybox.Keybox
import build.wallet.partnerships.PartnerRedirectionMethod
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.core.StateMachine
import com.github.michaelbull.result.Result

interface PartnershipsTransferUiStateMachine : StateMachine<PartnershipsTransferUiProps, SheetModel>

data class PartnershipsTransferUiProps(
  val keybox: Keybox,
  val generateAddress: suspend () -> Result<BitcoinAddress, Throwable>,
  val onBack: () -> Unit,
  val onAnotherWalletOrExchange: () -> Unit,
  val onPartnerRedirected: (PartnerRedirectionMethod) -> Unit,
  val onExit: () -> Unit,
)
