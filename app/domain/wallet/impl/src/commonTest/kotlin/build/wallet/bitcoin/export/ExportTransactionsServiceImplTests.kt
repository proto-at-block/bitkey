package build.wallet.bitcoin.export

import app.cash.turbine.test
import build.wallet.account.AccountServiceFake
import build.wallet.account.AccountStatus.ActiveAccount
import build.wallet.bitcoin.descriptor.BitcoinMultiSigDescriptorBuilderMock
import build.wallet.bitcoin.wallet.SpendingWalletFake
import build.wallet.bitcoin.wallet.WalletV2ProviderMock
import build.wallet.bitcoin.wallet.WatchingWalletProviderMock
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.keybox.KeyboxMock
import build.wallet.bitkey.spending.SpendingKeysetMock
import build.wallet.coroutines.turbine.awaitUntil
import build.wallet.f8e.recovery.LegacyRemoteKeyset
import build.wallet.f8e.recovery.ListKeysetsF8eClientMock
import build.wallet.f8e.recovery.ListKeysetsResponse
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.feature.flags.Bdk2FeatureFlag
import build.wallet.feature.setFlagValue
import build.wallet.money.BitcoinMoney
import build.wallet.money.currency.BTC
import build.wallet.platform.random.UuidGeneratorFake
import build.wallet.testing.shouldBeErr
import build.wallet.testing.shouldBeOk
import com.github.michaelbull.result.Ok
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first

class ExportTransactionsServiceImplTests : FunSpec({
  val accountService = AccountServiceFake()
  val watchingWallet = SpendingWalletFake()
  val watchingWalletProvider = WatchingWalletProviderMock(watchingWallet)
  val walletV2Provider = WalletV2ProviderMock()
  val bdk2FeatureFlag = Bdk2FeatureFlag(FeatureFlagDaoFake())
  val listKeysetsF8eClient = ListKeysetsF8eClientMock()
  val uuidGenerator = UuidGeneratorFake()
  val service = ExportTransactionsServiceImpl(
    accountService = accountService,
    watchingWalletProvider = watchingWalletProvider,
    walletV2Provider = walletV2Provider,
    bdk2FeatureFlag = bdk2FeatureFlag,
    bitcoinMultiSigDescriptorBuilder = BitcoinMultiSigDescriptorBuilderMock(),
    exportTransactionsAsCsvSerializer = ExportTransactionsAsCsvSerializerImpl(),
    listKeysetsF8eClient = listKeysetsF8eClient,
    uuidGenerator = uuidGenerator
  )

  beforeEach {
    accountService.reset()
    watchingWallet.reset()
    watchingWalletProvider.reset()
    walletV2Provider.reset()
    bdk2FeatureFlag.setFlagValue(false)
    accountService.setActiveAccount(FullAccountMock)

    val activeKeyset =
      (accountService.activeAccount().first() as FullAccount).keybox.activeSpendingKeyset
    listKeysetsF8eClient.result =
      Ok(
        ListKeysetsResponse(
          keysets = listOf(
            LegacyRemoteKeyset(
              keysetId = activeKeyset.f8eSpendingKeyset.keysetId,
              networkType = activeKeyset.networkType.name,
              appDescriptor = activeKeyset.appKey.key.dpub,
              hardwareDescriptor = activeKeyset.hardwareKey.key.dpub,
              serverDescriptor = activeKeyset.f8eSpendingKeyset.spendingPublicKey.key.dpub
            )
          ),
          wrappedSsek = null,
          descriptorBackups = emptyList(),
          activeKeysetId = activeKeyset.f8eSpendingKeyset.keysetId
        )
      )
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

  test("export uses V2 wallet provider when BDK2 flag enabled") {
    bdk2FeatureFlag.setFlagValue(true)
    walletV2Provider.walletResult = Ok(watchingWallet)

    service.export().shouldBeOk()

    walletV2Provider.requestedDescriptors.test {
      val requestedDescriptors = awaitUntil { it.size == 1 }
      requestedDescriptors.first().identifier.shouldBe("WatchingWallet spending-public-keyset-fake-id-1")
    }

    watchingWalletProvider.requestedDescriptors.value.shouldBe(emptyList())
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

  test("export uses local keysets when canUseKeyboxKeysets is true") {
    // Create additional keyset for local keysets
    val localInactiveKeyset = SpendingKeysetMock.copy(
      localId = "local-export-keyset-id",
      f8eSpendingKeyset = SpendingKeysetMock.f8eSpendingKeyset.copy(
        keysetId = "local-export-keyset-server-id"
      )
    )

    // Set up AccountService with a FullAccount that has canUseKeyboxKeysets = true and multiple keysets
    val accountWithLocalKeysets = FullAccountMock.copy(
      keybox = KeyboxMock.copy(
        canUseKeyboxKeysets = true,
        keysets = listOf(SpendingKeysetMock, localInactiveKeyset)
      )
    )

    accountService.accountState.value = Ok(ActiveAccount(accountWithLocalKeysets))

    val testService = ExportTransactionsServiceImpl(
      accountService = accountService,
      watchingWalletProvider = watchingWalletProvider,
      walletV2Provider = walletV2Provider,
      bdk2FeatureFlag = bdk2FeatureFlag,
      bitcoinMultiSigDescriptorBuilder = BitcoinMultiSigDescriptorBuilderMock(),
      exportTransactionsAsCsvSerializer = ExportTransactionsAsCsvSerializerImpl(),
      listKeysetsF8eClient = ListKeysetsF8eClientMock(),
      uuidGenerator = UuidGeneratorFake()
    )

    testService.export().shouldBeOk()

    // Verify that the local keysets were used by checking the descriptor identifiers
    // We should see descriptors for both the active keyset and the local inactive keyset
    watchingWalletProvider.requestedDescriptors.test {
      val requestedDescriptors = awaitUntil { it.size == 2 }
      requestedDescriptors.map { it.identifier }
        .shouldContainExactlyInAnyOrder(
          "WatchingWallet local-export-keyset-id",
          "WatchingWallet spending-public-keyset-fake-id-1"
        )
    }
  }

  test("export falls back to F8e keysets when canUseKeyboxKeysets is false") {
    // Set up AccountService with a FullAccount that has canUseKeyboxKeysets = false
    val accountWithLocalKeysets = FullAccountMock.copy(
      keybox = KeyboxMock.copy(
        canUseKeyboxKeysets = false
      )
    )

    accountService.accountState.value = Ok(ActiveAccount(accountWithLocalKeysets))

    val f8eClientWithDifferentKeysets = ListKeysetsF8eClientMock().apply {
      numKeysets = 2 // Will generate "spending-public-keyset-fake-server-id-0", etc
    }

    val testService = ExportTransactionsServiceImpl(
      accountService = accountService,
      watchingWalletProvider = watchingWalletProvider,
      walletV2Provider = walletV2Provider,
      bdk2FeatureFlag = bdk2FeatureFlag,
      bitcoinMultiSigDescriptorBuilder = BitcoinMultiSigDescriptorBuilderMock(),
      exportTransactionsAsCsvSerializer = ExportTransactionsAsCsvSerializerImpl(),
      listKeysetsF8eClient = f8eClientWithDifferentKeysets,
      uuidGenerator = UuidGeneratorFake()
    )

    testService.export().shouldBeOk()

    // Verify that the F8E keysets were used by checking the descriptor identifiers
    // F8e mock with numKeysets=1 creates 0..1 = 2 keysets total
    watchingWalletProvider.requestedDescriptors.test {
      val requestedDescriptors = awaitUntil { it.size == 2 }
      requestedDescriptors.map { it.identifier }
        .shouldContainExactlyInAnyOrder(
          "WatchingWallet uuid-0",
          "WatchingWallet uuid-1"
        )
    }
  }
})
