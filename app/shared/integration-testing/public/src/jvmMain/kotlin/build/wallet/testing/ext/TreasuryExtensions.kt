package build.wallet.testing.ext

import build.wallet.bitcoin.fees.FeePolicy
import build.wallet.bitcoin.transactions.BitcoinTransactionSendAmount
import build.wallet.bitcoin.treasury.FundingResult
import build.wallet.bitcoin.wallet.SpendingWallet
import build.wallet.money.BitcoinMoney
import build.wallet.testing.AppTester
import build.wallet.testing.fakeTransact
import com.github.michaelbull.result.getOrThrow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

/**
 * Returns funds to the treasury wallet.
 */
suspend fun AppTester.returnFundsToTreasury() {
  val account = getActiveFullAccount()
  val spendingWallet = getActiveWallet()
  spendingWallet.sync().getOrThrow()

  val appSignedPsbt =
    spendingWallet
      .createSignedPsbt(
        SpendingWallet.PsbtConstructionMethod.Regular(
          recipientAddress = treasuryWallet.getReturnAddress(),
          amount = BitcoinTransactionSendAmount.SendAll,
          feePolicy = FeePolicy.MinRelayRate
        )
      )
      .getOrThrow()

  val appAndHwSignedPsbt =
    nfcTransactor.fakeTransact(
      transaction = { session, commands ->
        commands.signTransaction(session, appSignedPsbt, account.keybox.activeSpendingKeyset)
      }
    ).getOrThrow()
  bitcoinBlockchain.broadcast(appAndHwSignedPsbt).getOrThrow()
  mineBlock(appAndHwSignedPsbt.id)
}

/**
 * Add some funds from treasury to active Full account.
 *
 * Please return back to treasury later using [returnFundsToTreasury]!
 */
suspend fun AppTester.addSomeFunds(
  amount: BitcoinMoney = BitcoinMoney.sats(10_000L),
  waitForConfirmation: Boolean = true,
): FundingResult {
  val wallet = bitcoinWalletService.spendingWallet().filterNotNull().first()
  return treasuryWallet.fund(wallet, amount, waitForConfirmation)
}
