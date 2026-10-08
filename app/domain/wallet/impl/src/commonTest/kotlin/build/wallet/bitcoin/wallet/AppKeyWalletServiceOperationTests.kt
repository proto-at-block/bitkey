package build.wallet.bitcoin.wallet

import build.wallet.bdk.bindings.BdkScript
import build.wallet.bdk.bindings.BdkScriptMock
import build.wallet.bdk.bindings.BdkTxOut
import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.address.BitcoinAddress
import build.wallet.bitcoin.address.BitcoinAddressServiceFake
import build.wallet.bitcoin.balance.BitcoinBalance
import build.wallet.bitcoin.fees.BitcoinFeeRateEstimatorMock
import build.wallet.bitcoin.fees.Fee
import build.wallet.bitcoin.fees.FeePolicy
import build.wallet.bitcoin.transactions.BitcoinTransactionSendAmount
import build.wallet.bitcoin.transactions.BitcoinWalletServiceFake
import build.wallet.bitcoin.transactions.Psbt
import build.wallet.bitkey.spending.SpendingKeysetMock
import build.wallet.money.BitcoinMoney
import build.wallet.testing.shouldBeErrOfType
import build.wallet.testing.shouldBeOk
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class AppKeyWalletServiceOperationTests : FunSpec({
  val destination = build.wallet.bitcoin.address.someBitcoinAddress
  val sourceWallet = OperationAppKeyWallet()
  val activeWallet = OwnedDestinationWallet(destination)
  val provider = AppKeyWalletProviderFake(Ok(sourceWallet))
  val addressService = BitcoinAddressServiceFake().apply { result = Ok(destination) }
  val bitcoinWalletService = BitcoinWalletServiceFake().apply { spendingWallet.value = activeWallet }
  val service = AppKeyWalletServiceImpl(
    appKeyWalletProvider = provider,
    bitcoinAddressService = addressService,
    bitcoinWalletService = bitcoinWalletService,
    bitcoinFeeRateEstimator = BitcoinFeeRateEstimatorMock()
  )

  beforeTest {
    sourceWallet.reset()
    provider.walletResult = Ok(sourceWallet)
    bitcoinWalletService.reset()
    bitcoinWalletService.spendingWallet.value = activeWallet
  }

  test("prepare sign and broadcast completes the operation") {
    val preview = service.prepareSweep(SpendingKeysetMock).shouldBeOk()

    service.signSweep(SpendingKeysetMock, preview.id).shouldBe(Ok(Unit))
    service.broadcastSweep(SpendingKeysetMock, preview.id).value.transactionId.shouldBe("signed-psbt")
    bitcoinWalletService.broadcastedPsbts.value.single().id.shouldBe("signed-psbt")
  }

  test("unknown preview is stale") {
    service.signSweep(SpendingKeysetMock, AppKeyWalletSweepId("unknown"))
      .shouldBeErrOfType<AppKeyWalletServiceError.StalePreview>()
  }

  test("keyset mismatch is stale") {
    val preview = service.prepareSweep(SpendingKeysetMock).shouldBeOk()
    val otherKeyset = SpendingKeysetMock.copy(localId = "other-keyset")

    service.signSweep(otherKeyset, preview.id)
      .shouldBeErrOfType<AppKeyWalletServiceError.StalePreview>()
  }

  test("changed source balance before signing is stale") {
    val preview = service.prepareSweep(SpendingKeysetMock).shouldBeOk()
    sourceWallet.balanceSats.value = 2_000uL

    service.signSweep(SpendingKeysetMock, preview.id)
      .shouldBeErrOfType<AppKeyWalletServiceError.StalePreview>()
  }

  test("broadcast before signing is stale") {
    val preview = service.prepareSweep(SpendingKeysetMock).shouldBeOk()

    service.broadcastSweep(SpendingKeysetMock, preview.id)
      .shouldBeErrOfType<AppKeyWalletServiceError.StalePreview>()
  }
})

private class OperationAppKeyWallet : AppKeyWallet,
  SpendingWallet by SpendingWalletFake() {
  val balanceSats = MutableStateFlow(1_000uL)
  private val destinationScript = BdkScriptMock()

  override val networkType = BitcoinNetworkType.SIGNET
  override fun balance() = balanceSats.map { sats ->
    val amount = BitcoinMoney.sats(sats)
    BitcoinBalance(
      immature = BitcoinMoney.zero(),
      trustedPending = BitcoinMoney.zero(),
      untrustedPending = BitcoinMoney.zero(),
      confirmed = amount,
      spendable = amount,
      total = amount
    )
  }

  override suspend fun fullScan(): Result<Unit, Error> = Ok(Unit)

  override suspend fun createPsbt(
    recipientAddress: BitcoinAddress,
    amount: BitcoinTransactionSendAmount,
    feePolicy: FeePolicy,
    coinSelectionStrategy: CoinSelectionStrategy,
  ): Result<Psbt, Throwable> = Ok(psbt("unsigned-psbt"))

  override suspend fun signPsbt(psbt: Psbt): Result<Psbt, Throwable> = Ok(psbt("signed-psbt"))

  fun reset() {
    balanceSats.value = 1_000uL
  }

  private fun psbt(id: String) = Psbt(
    id = id,
    base64 = id,
    fee = Fee(BitcoinMoney.sats(100)),
    vsize = 100,
    numOfInputs = 1,
    amountSats = 900uL,
    outputs = setOf(BdkTxOut(value = 900u, scriptPubkey = destinationScript))
  )
}

private class OwnedDestinationWallet(
  private val destination: BitcoinAddress,
) : SpendingWallet by SpendingWalletFake(networkType = BitcoinNetworkType.SIGNET) {
  override suspend fun isMine(address: BitcoinAddress): Result<Boolean, Error> =
    Ok(true)

  override suspend fun isMine(scriptPubKey: BdkScript): Result<Boolean, Error> =
    Ok(true)
}
