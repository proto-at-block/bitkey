package build.wallet.bitcoin.treasury

import build.wallet.bitcoin.address.BitcoinAddress
import build.wallet.bitcoin.transactions.Psbt
import build.wallet.bitcoin.wallet.WatchingWallet
import build.wallet.money.BitcoinMoney
import com.github.michaelbull.result.Result

interface TreasuryWallet {
  suspend fun fund(
    destinationWallet: WatchingWallet,
    amount: BitcoinMoney,
    waitForConfirmation: Boolean = true,
  ): FundingResult

  suspend fun getReturnAddress(): BitcoinAddress

  suspend fun sync(): Result<Unit, Error>
}

data class FundingResult(
  val depositAddress: BitcoinAddress,
  val tx: Psbt,
)
