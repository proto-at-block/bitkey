package build.wallet.integration.statemachine.mobilepay

import build.wallet.bitcoin.fees.FeePolicy
import build.wallet.bitcoin.transactions.BitcoinTransactionSendAmount
import build.wallet.bitkey.account.SoftwareAccount
import build.wallet.feature.setFlagValue
import build.wallet.money.BitcoinMoney
import build.wallet.platform.permissions.PermissionStatus
import build.wallet.testing.AppTester.Companion.launchNewApp
import build.wallet.testing.ext.mineBlock
import build.wallet.testing.shouldBeOk
import build.wallet.testing.shouldBeOkOfType
import com.github.michaelbull.result.getOrThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first

class SoftwareWalletSigningFunctionalTests : FunSpec({

  test("create account and send sats back to treasury") {
    val app = launchNewApp()

    // Set push notifications to authorized to enable us to successfully advance through
    // the notifications step in onboarding.
    app.pushNotificationPermissionStatusProvider.updatePushNotificationStatus(
      PermissionStatus.Authorized
    )

    app.softwareWalletIsEnabledFeatureFlag.setFlagValue(true)
    val softwareAccount = app.onboardSoftwareAccountService.createAccount().shouldBeOkOfType<SoftwareAccount>()
    val wallet = app.appSpendingWalletProvider.getSpendingWallet(softwareAccount.keybox)
      .shouldBeOk()

    app.treasuryWallet.fund(
      destinationWallet = wallet,
      amount = BitcoinMoney.sats(50_000L)
    )

    wallet.balance().first()
      .total.shouldBe(BitcoinMoney.sats(50_000L))

    val psbt = wallet.createPsbt(
      recipientAddress = app.treasuryWallet.getReturnAddress(),
      amount = BitcoinTransactionSendAmount.SendAll,
      feePolicy = FeePolicy.MinRelayRate
    ).shouldBeOk()

    // TODO [W-10273]: Remove shareDetails parameter
    val signedPsbt = app.softwareWalletSigningService.sign(
      psbt = psbt,
      shareDetails = softwareAccount.keybox.shareDetails
    ).shouldBeOk()

    app.bitcoinBlockchain.broadcast(signedPsbt).getOrThrow()
    app.mineBlock(signedPsbt.id)
  }
})
