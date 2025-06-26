package build.wallet.bitcoin.export

import build.wallet.account.AccountServiceFake
import build.wallet.bitcoin.descriptor.BitcoinMultiSigDescriptorBuilderMock
import build.wallet.bitcoin.wallet.SpendingWalletFake
import build.wallet.bitcoin.wallet.WatchingWalletProviderMock
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.f8e.recovery.ListKeysetsF8eClient
import build.wallet.f8e.recovery.ListKeysetsF8eClientMock
import build.wallet.money.BitcoinMoney
import build.wallet.money.currency.BTC
import build.wallet.testing.shouldBeErr
import build.wallet.testing.shouldBeOk
import com.github.michaelbull.result.Ok
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first

class ExportTransactionsServiceImplTests : FunSpec({
  val accountService = AccountServiceFake()
  val watchingWallet = SpendingWalletFake()
  val listKeysetsF8eClient = ListKeysetsF8eClientMock()
  val service = ExportTransactionsServiceImpl(
    accountService = accountService,
    watchingWalletProvider = WatchingWalletProviderMock(
      watchingWallet = watchingWallet
    ),
    bitcoinMultiSigDescriptorBuilder = BitcoinMultiSigDescriptorBuilderMock(),
    exportTransactionsAsCsvSerializer = ExportTransactionsAsCsvSerializerImpl(),
    listKeysetsF8eClient = listKeysetsF8eClient
  )

  beforeEach {
    accountService.reset()
    watchingWallet.reset()
    accountService.setActiveAccount(FullAccountMock)

    val activeKeyset =
      (accountService.activeAccount().first() as FullAccount).keybox.activeSpendingKeyset
    listKeysetsF8eClient.result =
      Ok(ListKeysetsF8eClient.ListKeysetsResponse(keysets = listOf(activeKeyset), descriptorBackups = null))
  }

  suspend fun onboardAndSendMoney(value: BigDecimal) {
    watchingWallet.sendFunds(
      amount = BitcoinMoney(currency = BTC, value = value)
    )
    watchingWallet.mineBlock()
  }

  suspend fun onboardAndReceiveMoney(value: BigDecimal) {
    watchingWallet.receiveFunds(
      amount = BitcoinMoney(currency = BTC, value = value)
    )
    watchingWallet.mineBlock()
  }

  test("export with no confirmed transactions") {
    // Assert we only produce the header.
    val dataString = service.export().shouldBeOk().data.utf8()
    dataString.split("\n").count().shouldBe(1)
  }

  test("export with one pending transaction") {
    watchingWallet.sendFunds(
      BitcoinMoney(currency = BTC, value = BigDecimal.ONE)
    )

    // Assert we still only produce the header.
    val dataString = service.export().shouldBeOk().data.utf8()
    dataString.split("\n").count().shouldBe(1)
  }

  test("successful export with confirmed transaction") {
    onboardAndSendMoney(value = BigDecimal.ONE)

    // Assert we produce the header, with one transaction.
    val dataString = service.export().shouldBeOk().data.utf8()
    val dataList = dataString.split("\n")

    dataList.count().shouldBe(2)
    // Assert header and row have the same number of cells
    val headerCount = dataList[0].split(",").count()
    val rowCount = dataList[1].split(",").count()

    headerCount.shouldBeExactly(rowCount)
  }

  // This should never happen, but we make sure we handle things gracefully.
  test("ensure we return the correct error message when trying to do this without an active account") {
    accountService.reset()

    service.export().shouldBeErr(Error("No active FullAccount present, found none."))
  }
})
