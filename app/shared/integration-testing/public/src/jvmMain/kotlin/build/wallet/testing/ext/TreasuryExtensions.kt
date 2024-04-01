package build.wallet.testing.ext

import build.wallet.bitcoin.fees.FeePolicy
import build.wallet.bitcoin.transactions.BitcoinTransactionSendAmount
import build.wallet.bitcoin.treasury.FundingResult
import build.wallet.bitcoin.wallet.SpendingWallet
import build.wallet.bitkey.account.FullAccount
import build.wallet.money.BitcoinMoney
import build.wallet.testing.AppTester
import build.wallet.testing.fakeTransact
import com.github.michaelbull.result.getOrThrow

/**
 * Returns funds to the treasury wallet.
 */
suspend fun AppTester.returnFundsToTreasury(account: FullAccount) {
  app.apply {
    val spendingWallet =
      appComponent.appSpendingWalletProvider.getSpendingWallet(
        account.keybox.activeSpendingKeyset
      ).getOrThrow()

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
    mineBlock()
  }
}

/**
 * Add some funds from treasury to active Full account.
 *
 * Please return back to treasury later using [returnFundsToTreasury]!
 */
suspend fun AppTester.addSomeFunds(
  amount: BitcoinMoney = BitcoinMoney.sats(10_000L),
): FundingResult {
  val keybox = getActiveFullAccount().keybox
  val wallet = app.appComponent.appSpendingWalletProvider
    .getSpendingWallet(keybox.activeSpendingKeyset)
    .getOrThrow()
  return treasuryWallet.fund(wallet, amount)
}
