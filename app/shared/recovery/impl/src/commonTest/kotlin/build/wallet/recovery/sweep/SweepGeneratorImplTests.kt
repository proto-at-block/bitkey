package build.wallet.recovery.sweep

import build.wallet.bdk.bindings.BdkError.Generic
import build.wallet.bdk.bindings.BdkError.InsufficientFunds
import build.wallet.bitcoin.AppPrivateKeyDaoFake
import build.wallet.bitcoin.BitcoinNetworkType.SIGNET
import build.wallet.bitcoin.address.BitcoinAddress
import build.wallet.bitcoin.fees.BitcoinFeeRateEstimatorMock
import build.wallet.bitcoin.keys.DescriptorPublicKeyMock
import build.wallet.bitcoin.keys.ExtendedPrivateKey
import build.wallet.bitcoin.transactions.PsbtMock
import build.wallet.bitcoin.wallet.SpendingWalletMock
import build.wallet.bitcoin.wallet.WatchingWallet
import build.wallet.bitkey.app.AppSpendingPrivateKey
import build.wallet.bitkey.app.AppSpendingPublicKey
import build.wallet.bitkey.f8e.F8eSpendingKeyset
import build.wallet.bitkey.f8e.F8eSpendingPublicKey
import build.wallet.bitkey.factor.PhysicalFactor.App
import build.wallet.bitkey.factor.PhysicalFactor.Hardware
import build.wallet.bitkey.hardware.HwSpendingPublicKey
import build.wallet.bitkey.keybox.KeyboxMock
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.recovery.ListKeysetsF8eClientMock
import build.wallet.keybox.KeyboxDaoMock
import build.wallet.keybox.wallet.KeysetWalletProvider
import build.wallet.money.BitcoinMoney
import build.wallet.notifications.RegisterWatchAddressContext
import build.wallet.queueprocessor.ProcessorMock
import build.wallet.recovery.sweep.SweepGenerator.SweepGeneratorError
import build.wallet.recovery.sweep.SweepGenerator.SweepGeneratorError.BdkFailedToCreatePsbt
import build.wallet.testing.shouldBeErrOfType
import build.wallet.testing.shouldBeOkOfType
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

class SweepGeneratorImplTests : FunSpec({
  val psbtMock = PsbtMock.copy(fee = BitcoinMoney.btc(BigDecimal.TEN))
  val activeKeyset =
    SpendingKeyset(
      localId = "active",
      f8eSpendingKeyset =
        F8eSpendingKeyset(
          keysetId = "active-serverId",
          spendingPublicKey = F8eSpendingPublicKey(DescriptorPublicKeyMock("server-dpub-active"))
        ),
      networkType = SIGNET,
      appKey = AppSpendingPublicKey(DescriptorPublicKeyMock("app-dpub-active")),
      hardwareKey =
        HwSpendingPublicKey(
          DescriptorPublicKeyMock("hw-dpub-active", fingerprint = "deadbeef")
        )
    )
  val activeKeybox = KeyboxMock.copy(activeSpendingKeyset = activeKeyset)

  val lostAppKeyset1 =
    SpendingKeyset(
      localId = "keyset-lost-app-1",
      f8eSpendingKeyset =
        F8eSpendingKeyset(
          keysetId = "keyset-lost-app-server-1",
          spendingPublicKey = F8eSpendingPublicKey(DescriptorPublicKeyMock("server-dpub-1"))
        ),
      networkType = SIGNET,
      appKey = AppSpendingPublicKey(DescriptorPublicKeyMock("app-dpub-1")),
      hardwareKey =
        HwSpendingPublicKey(
          DescriptorPublicKeyMock("hw-dpub-1", fingerprint = "deadbeef")
        )
    )

  val lostAppKeyset2 =
    SpendingKeyset(
      localId = "keyset-lost-app-2",
      f8eSpendingKeyset =
        F8eSpendingKeyset(
          keysetId = "keyset-lost-app-server-2",
          spendingPublicKey = F8eSpendingPublicKey(DescriptorPublicKeyMock("server-dpub-1"))
        ),
      networkType = SIGNET,
      appKey = AppSpendingPublicKey(DescriptorPublicKeyMock("app-dpub-2")),
      hardwareKey =
        HwSpendingPublicKey(
          DescriptorPublicKeyMock("hw-dpub-2", fingerprint = "deadbeef")
        )
    )

  val lostHwKeyset1 =
    SpendingKeyset(
      localId = "keyset-lost-hw-1",
      f8eSpendingKeyset =
        F8eSpendingKeyset(
          keysetId = "keyset-lost-hw-server-1",
          spendingPublicKey = F8eSpendingPublicKey(DescriptorPublicKeyMock("server-dpub-3"))
        ),
      networkType = SIGNET,
      appKey = AppSpendingPublicKey(DescriptorPublicKeyMock("app-dpub-3")),
      hardwareKey =
        HwSpendingPublicKey(
          DescriptorPublicKeyMock("hw-dpub-3", fingerprint = "cafebabe")
        )
    )
  val lostHwKeyset2 =
    SpendingKeyset(
      localId = "keyset-lost-hw-2",
      f8eSpendingKeyset =
        F8eSpendingKeyset(
          keysetId = "keyset-lost-hw-server-2",
          spendingPublicKey = F8eSpendingPublicKey(DescriptorPublicKeyMock("server-dpub-4"))
        ),
      networkType = SIGNET,
      appKey = AppSpendingPublicKey(DescriptorPublicKeyMock("app-dpub-4")),
      hardwareKey =
        HwSpendingPublicKey(
          DescriptorPublicKeyMock("hw-dpub-4", fingerprint = "cafebabe")
        )
    )

  val lostBothKeyset =
    SpendingKeyset(
      localId = "keyset-lost-both",
      f8eSpendingKeyset =
        F8eSpendingKeyset(
          keysetId = "keyset-lost-both-server-2",
          spendingPublicKey = F8eSpendingPublicKey(DescriptorPublicKeyMock("server-dpub-5"))
        ),
      networkType = SIGNET,
      appKey = AppSpendingPublicKey(DescriptorPublicKeyMock("app-dpub-5")),
      hardwareKey =
        HwSpendingPublicKey(
          DescriptorPublicKeyMock("[cafebabe/84'/0'/2']xpub-5/*")
        )
    )

  val keyboxDao = KeyboxDaoMock(turbines::create, defaultActiveKeybox = activeKeybox)

  // All keyset mocks
  val keysets =
    listOf(
      activeKeyset,
      lostAppKeyset1,
      lostAppKeyset2,
      lostHwKeyset1,
      lostHwKeyset2,
      lostBothKeyset
    )

  // Create a SpendingWalletMock for each keyset
  // TODO(W-4257): create and use WatchingWalletMock
  val wallets =
    keysets.associate {
      it.localId to SpendingWalletMock(turbines::create, it.localId)
    }

  val keysetWalletProvider =
    object : KeysetWalletProvider {
      override suspend fun getWatchingWallet(
        keyset: SpendingKeyset,
      ): Result<WatchingWallet, Throwable> {
        val wallet =
          requireNotNull(wallets[keyset.localId]) {
            "No spending wallet mock found for keyset \"${keyset.localId}\""
          }
        return Ok(wallet)
      }
    }
  val listKeysetsF8eClient = ListKeysetsF8eClientMock()
  val appPrivateKeyDao = AppPrivateKeyDaoFake()
  val registerWatchAddressF8eClient = ProcessorMock<RegisterWatchAddressContext>(turbines::create)
  val sweepGenerator =
    SweepGeneratorImpl(
      listKeysetsF8eClient,
      BitcoinFeeRateEstimatorMock(),
      keysetWalletProvider,
      appPrivateKeyDao,
      registerWatchAddressF8eClient
    )

  beforeEach {
    keyboxDao.reset()
    listKeysetsF8eClient.reset()

    wallets.values.forEach { it.reset() }
  }

  beforeEach {
    appPrivateKeyDao.appSpendingKeys.put(
      lostHwKeyset1.appKey,
      AppSpendingPrivateKey(
        ExtendedPrivateKey(lostHwKeyset1.appKey.key.xpub, "mnemonic")
      )
    )
    appPrivateKeyDao.appSpendingKeys.put(
      lostHwKeyset2.appKey,
      AppSpendingPrivateKey(
        ExtendedPrivateKey(lostHwKeyset2.appKey.key.xpub, "mnemonic")
      )
    )
  }

  beforeTest {
    registerWatchAddressF8eClient.processBatchReturnValues = listOf(Ok(Unit), Ok(Unit))
  }

  afterTest {
    registerWatchAddressF8eClient.reset()
  }

  test("lost app recovery - single keyset - success") {
    wallets.getValue(activeKeyset.localId).createPsbtResult = Ok(psbtMock)
    wallets.getValue(lostAppKeyset1.localId).createPsbtResult = Ok(psbtMock)
    listKeysetsF8eClient.result = Ok(listOf(lostAppKeyset1))

    val result = sweepGenerator.generateSweep(activeKeybox).shouldBeOkOfType<List<SweepPsbt>>()
    result.shouldHaveSize(1)
    result.first()
      .shouldBe(
        SweepPsbt(psbtMock, Hardware, lostAppKeyset1)
      )
    // single address to watch
    registerWatchAddressF8eClient.processBatchCalls.awaitItem()
      .shouldBe(
        listOf(
          RegisterWatchAddressContext(
            address = BitcoinAddress(address = "bc1zw508d6qejxtdg4y5r3zarvaryvaxxpcs"),
            f8eSpendingKeyset = F8eSpendingKeyset(
              keysetId = "active-serverId",
              spendingPublicKey = F8eSpendingPublicKey(DescriptorPublicKeyMock("server-dpub-active"))
            ),
            accountId = "server-id",
            f8eEnvironment = F8eEnvironment.Development
          )
        )
      )
  }

  test("lost app recovery - multiple keysets - success") {
    wallets.getValue(activeKeyset.localId).createPsbtResult = Ok(psbtMock)
    wallets.getValue(lostAppKeyset1.localId).createPsbtResult = Ok(psbtMock)
    wallets.getValue(lostAppKeyset2.localId).createPsbtResult = Ok(psbtMock)
    listKeysetsF8eClient.result = Ok(listOf(lostAppKeyset1, lostAppKeyset2))

    val result = sweepGenerator.generateSweep(activeKeybox).shouldBeOkOfType<List<SweepPsbt>>()
    result.shouldHaveSize(2)
    result.shouldBe(
      listOf(
        SweepPsbt(psbtMock, Hardware, lostAppKeyset1),
        SweepPsbt(psbtMock, Hardware, lostAppKeyset2)
      )
    )

    // two addresses to watch
    registerWatchAddressF8eClient.processBatchCalls.awaitItem()
      .shouldBe(
        listOf(
          RegisterWatchAddressContext(
            address = BitcoinAddress(address = "bc1zw508d6qejxtdg4y5r3zarvaryvaxxpcs"),
            f8eSpendingKeyset = F8eSpendingKeyset(
              keysetId = "active-serverId",
              spendingPublicKey = F8eSpendingPublicKey(DescriptorPublicKeyMock("server-dpub-active"))
            ),
            accountId = "server-id",
            f8eEnvironment = F8eEnvironment.Development
          )
        )
      )
    registerWatchAddressF8eClient.processBatchCalls.awaitItem()
      .shouldBe(
        listOf(
          RegisterWatchAddressContext(
            address = BitcoinAddress(address = "bc1zw508d6qejxtdg4y5r3zarvaryvaxxpcs"),
            f8eSpendingKeyset = F8eSpendingKeyset(
              keysetId = "active-serverId",
              spendingPublicKey = F8eSpendingPublicKey(DescriptorPublicKeyMock("server-dpub-active"))
            ),
            accountId = "server-id",
            f8eEnvironment = F8eEnvironment.Development
          )
        )
      )
  }

  test("destination spending address generation failed") {
    wallets.getValue(activeKeyset.localId).createPsbtResult = Ok(psbtMock)
    wallets.getValue(activeKeyset.localId).newAddressResult =
      Err(Generic(Exception("Dang."), null))
    wallets.getValue(lostAppKeyset1.localId).createPsbtResult = Ok(psbtMock)

    listKeysetsF8eClient.result = Ok(listOf(lostAppKeyset1))

    sweepGenerator.generateSweep(activeKeybox)
      .shouldBeErrOfType<SweepGeneratorError.FailedToGenerateDestinationAddress>()
  }

  test("lost hardware recovery") {
    listKeysetsF8eClient.result = Ok(listOf(lostHwKeyset1, lostHwKeyset2))

    val result = sweepGenerator.generateSweep(activeKeybox).shouldBeOkOfType<List<SweepPsbt>>()
    result.shouldHaveSize(2)
    result.shouldBe(
      listOf(
        SweepPsbt(psbtMock, App, lostHwKeyset1),
        SweepPsbt(psbtMock, App, lostHwKeyset2)
      )
    )
    // two addresses to watch
    registerWatchAddressF8eClient.processBatchCalls.awaitItem()
    registerWatchAddressF8eClient.processBatchCalls.awaitItem()
  }

  test("no signable keysets - lost both") {
    wallets.getValue(lostAppKeyset1.localId).createPsbtResult = Ok(psbtMock)
    listKeysetsF8eClient.result = Ok(listOf(lostBothKeyset))

    val result = sweepGenerator.generateSweep(activeKeybox).shouldBeOkOfType<List<SweepPsbt>>()
    result.shouldBeEmpty()
  }

  test("some signable keysets") {
    wallets.getValue(lostAppKeyset1.localId).createPsbtResult = Ok(psbtMock)
    listKeysetsF8eClient.result = Ok(listOf(lostBothKeyset, lostAppKeyset1, lostHwKeyset1))

    val result = sweepGenerator.generateSweep(activeKeybox).shouldBeOkOfType<List<SweepPsbt>>()
    result.shouldBe(
      listOf(
        SweepPsbt(psbtMock, Hardware, lostAppKeyset1),
        SweepPsbt(psbtMock, App, lostHwKeyset1)
      )
    )
    // two addresses to watch
    registerWatchAddressF8eClient.processBatchCalls.awaitItem()
    registerWatchAddressF8eClient.processBatchCalls.awaitItem()
  }

  test("insufficient balance for one keyset should skip the keyset") {
    wallets.getValue(activeKeyset.localId).createPsbtResult = Ok(psbtMock)
    wallets.getValue(lostHwKeyset1.localId).createPsbtResult =
      Err(InsufficientFunds(Exception("too poor"), null))
    wallets.getValue(lostHwKeyset2.localId).createPsbtResult = Ok(psbtMock)
    listKeysetsF8eClient.result = Ok(listOf(lostHwKeyset1, lostHwKeyset2))

    sweepGenerator.generateSweep(activeKeybox)
      .shouldBe(Ok(listOf(SweepPsbt(psbtMock, App, lostHwKeyset2))))
    // two addresses to watch
    registerWatchAddressF8eClient.processBatchCalls.awaitItem()
    registerWatchAddressF8eClient.processBatchCalls.awaitItem()
  }

  test("psbt create failure for one keyset should fail all") {
    wallets.getValue(activeKeyset.localId).createPsbtResult = Ok(psbtMock)
    wallets.getValue(lostHwKeyset1.localId).createPsbtResult =
      Err(Generic(Exception("Dang."), null))
    wallets.getValue(lostHwKeyset2.localId).createPsbtResult = Ok(psbtMock)
    listKeysetsF8eClient.result = Ok(listOf(lostHwKeyset1, lostHwKeyset2))

    sweepGenerator.generateSweep(activeKeybox).shouldBeErrOfType<BdkFailedToCreatePsbt>()
    // single address to watch, subsequent fail and don't trigger
    registerWatchAddressF8eClient.processBatchCalls.awaitItem()
  }
})
