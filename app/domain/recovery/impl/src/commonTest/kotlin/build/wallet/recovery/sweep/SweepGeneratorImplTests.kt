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
import build.wallet.bitkey.app.AppSpendingPrivateKey
import build.wallet.bitkey.app.AppSpendingPublicKey
import build.wallet.bitkey.f8e.F8eSpendingKeyset
import build.wallet.bitkey.f8e.F8eSpendingPublicKey
import build.wallet.bitkey.hardware.HwSpendingPublicKey
import build.wallet.bitkey.keybox.KeyboxMock
import build.wallet.bitkey.spending.PrivateSpendingKeysetMock
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.chaincode.delegation.ChaincodeDelegationError
import build.wallet.chaincode.delegation.ChaincodeDelegationTweakServiceFake
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.recovery.LegacyRemoteKeyset
import build.wallet.f8e.recovery.ListKeysetsF8eClientMock
import build.wallet.f8e.recovery.ListKeysetsResponse
import build.wallet.keybox.KeyboxDaoMock
import build.wallet.keybox.wallet.KeysetWalletProviderMock
import build.wallet.money.BitcoinMoney
import build.wallet.notifications.RegisterWatchAddressContext
import build.wallet.notifications.RegisterWatchAddressProcessor
import build.wallet.platform.random.UuidGeneratorFake
import build.wallet.queueprocessor.Processor
import build.wallet.queueprocessor.ProcessorMock
import build.wallet.recovery.DescriptorBackupServiceFake
import build.wallet.recovery.sweep.SweepGenerator.SweepGeneratorError
import build.wallet.recovery.sweep.SweepGenerator.SweepGeneratorError.BdkFailedToCreatePsbt
import build.wallet.testing.shouldBeErr
import build.wallet.testing.shouldBeErrOfType
import build.wallet.testing.shouldBeOkOfType
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

class SweepGeneratorImplTests : FunSpec({
  val destinationAddress = "bc1zw508d6qejxtdg4y5r3zarvaryvaxxpcs"
  val psbtMock = PsbtMock.copy(fee = BitcoinMoney.btc(BigDecimal.TEN))
  val activeKeyset =
    SpendingKeyset(
      localId = "active",
      f8eSpendingKeyset =
        F8eSpendingKeyset(
          keysetId = "active-serverId",
          spendingPublicKey = F8eSpendingPublicKey(DescriptorPublicKeyMock("server-dpub-active")),
          privateWalletRootXpub = null
        ),
      networkType = SIGNET,
      appKey = AppSpendingPublicKey(DescriptorPublicKeyMock("app-dpub-active")),
      hardwareKey =
        HwSpendingPublicKey(
          DescriptorPublicKeyMock("hw-dpub-active", fingerprint = "deadbeef")
        )
    )
  val activeKeybox =
    KeyboxMock.copy(activeSpendingKeyset = activeKeyset, keysets = listOf(activeKeyset))

  val lostAppKeyset1 =
    SpendingKeyset(
      localId = "keyset-lost-app-1",
      f8eSpendingKeyset =
        F8eSpendingKeyset(
          keysetId = "keyset-lost-app-server-1",
          spendingPublicKey = F8eSpendingPublicKey(DescriptorPublicKeyMock("server-dpub-1")),
          privateWalletRootXpub = null
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
          spendingPublicKey = F8eSpendingPublicKey(DescriptorPublicKeyMock("server-dpub-1")),
          privateWalletRootXpub = null
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
          spendingPublicKey = F8eSpendingPublicKey(DescriptorPublicKeyMock("server-dpub-3")),
          privateWalletRootXpub = null
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
          spendingPublicKey = F8eSpendingPublicKey(DescriptorPublicKeyMock("server-dpub-4")),
          privateWalletRootXpub = null
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
          spendingPublicKey = F8eSpendingPublicKey(DescriptorPublicKeyMock("server-dpub-5")),
          privateWalletRootXpub = null
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
      lostBothKeyset,
      PrivateSpendingKeysetMock
    )

  // Create a SpendingWalletMock for each keyset
  // TODO(W-4257): create and use WatchingWalletMock
  val wallets =
    keysets.associate {
      it.localId to SpendingWalletMock(turbines::create, it.localId)
    }

  val walletsByF8eKeysetId =
    keysets.associate { keyset ->
      keyset.f8eSpendingKeyset.keysetId to wallets.getValue(keyset.localId)
    }

  val keysetWalletProvider = KeysetWalletProviderMock(wallets, walletsByF8eKeysetId)
  val listKeysetsF8eClient = ListKeysetsF8eClientMock()
  val appPrivateKeyDao = AppPrivateKeyDaoFake()
  val processorMock = ProcessorMock<RegisterWatchAddressContext>(turbines::create)
  val registerWatchAddressProcessor = object :
    RegisterWatchAddressProcessor,
    Processor<RegisterWatchAddressContext> by processorMock {}
  val uuidGenerator = UuidGeneratorFake()
  val chaincodeDelegationTweakService = ChaincodeDelegationTweakServiceFake()
  val descriptorBackupService = DescriptorBackupServiceFake()
  val sweepGenerator = SweepGeneratorImpl(
    listKeysetsF8eClient = listKeysetsF8eClient,
    bitcoinFeeRateEstimator = BitcoinFeeRateEstimatorMock(),
    keysetWalletProvider = keysetWalletProvider,
    appPrivateKeyDao = appPrivateKeyDao,
    registerWatchAddressProcessor = registerWatchAddressProcessor,
    uuidGenerator = uuidGenerator,
    chaincodeDelegationTweakService = chaincodeDelegationTweakService,
    descriptorBackupService = descriptorBackupService
  )

  beforeEach {
    keyboxDao.reset()
    listKeysetsF8eClient.reset()
    chaincodeDelegationTweakService.reset()
    descriptorBackupService.reset()

    appPrivateKeyDao.reset()
    appPrivateKeyDao.appSpendingKeys[lostHwKeyset1.appKey] = AppSpendingPrivateKey(
      ExtendedPrivateKey(lostHwKeyset1.appKey.key.xpub, "mnemonic")
    )
    appPrivateKeyDao.appSpendingKeys[lostHwKeyset2.appKey] = AppSpendingPrivateKey(
      ExtendedPrivateKey(lostHwKeyset2.appKey.key.xpub, "mnemonic")
    )

    processorMock.reset()
    processorMock.processBatchReturnValues = listOf(Ok(Unit), Ok(Unit))

    wallets.values.forEach { it.reset() }
  }

  test("lost app recovery - single keyset - success") {
    val keyboxWithLostAppKeyset = activeKeybox.copy(
      keysets = listOf(activeKeyset, lostAppKeyset1)
    )

    wallets.getValue(activeKeyset.localId).createPsbtResult = Ok(psbtMock)
    wallets.getValue(lostAppKeyset1.localId).createPsbtResult = Ok(psbtMock)

    val result =
      sweepGenerator.generateSweep(keyboxWithLostAppKeyset).shouldBeOkOfType<List<SweepPsbt>>()
    result.shouldHaveSize(1)
    result.first()
      .shouldBe(
        SweepPsbt(
          psbtMock,
          SweepSignaturePlan.HardwareAndServer,
          lostAppKeyset1,
          destinationAddress
        )
      )
    // single address to watch
    processorMock.processBatchCalls.awaitItem()
      .shouldBe(
        listOf(
          RegisterWatchAddressContext(
            address = BitcoinAddress(address = destinationAddress),
            f8eSpendingKeyset = F8eSpendingKeyset(
              keysetId = "active-serverId",
              spendingPublicKey = F8eSpendingPublicKey(DescriptorPublicKeyMock("server-dpub-active")),
              privateWalletRootXpub = null
            ),
            accountId = "server-id",
            f8eEnvironment = F8eEnvironment.Development
          )
        )
      )

    wallets.getValue(activeKeyset.localId).syncCalls.awaitItem()
    wallets.getValue(lostAppKeyset1.localId).syncCalls.awaitItem()
  }

  test("lost app recovery - multiple keysets - success") {
    val keyboxWithLostAppKeysets = activeKeybox.copy(
      keysets = listOf(activeKeyset, lostAppKeyset1, lostAppKeyset2)
    )

    wallets.getValue(activeKeyset.localId).createPsbtResult = Ok(psbtMock)
    wallets.getValue(lostAppKeyset1.localId).createPsbtResult = Ok(psbtMock)
    wallets.getValue(lostAppKeyset2.localId).createPsbtResult = Ok(psbtMock)

    val result =
      sweepGenerator.generateSweep(keyboxWithLostAppKeysets).shouldBeOkOfType<List<SweepPsbt>>()
    result.shouldHaveSize(2)
    result.shouldBe(
      listOf(
        SweepPsbt(
          psbtMock,
          SweepSignaturePlan.HardwareAndServer,
          lostAppKeyset1,
          destinationAddress
        ),
        SweepPsbt(
          psbtMock,
          SweepSignaturePlan.HardwareAndServer,
          lostAppKeyset2,
          destinationAddress
        )
      )
    )

    // two addresses to watch
    processorMock.processBatchCalls.awaitItem()
      .shouldBe(
        listOf(
          RegisterWatchAddressContext(
            address = BitcoinAddress(address = destinationAddress),
            f8eSpendingKeyset = F8eSpendingKeyset(
              keysetId = "active-serverId",
              spendingPublicKey = F8eSpendingPublicKey(DescriptorPublicKeyMock("server-dpub-active")),
              privateWalletRootXpub = null
            ),
            accountId = "server-id",
            f8eEnvironment = F8eEnvironment.Development
          )
        )
      )
    processorMock.processBatchCalls.awaitItem()
      .shouldBe(
        listOf(
          RegisterWatchAddressContext(
            address = BitcoinAddress(address = destinationAddress),
            f8eSpendingKeyset = F8eSpendingKeyset(
              keysetId = "active-serverId",
              spendingPublicKey = F8eSpendingPublicKey(DescriptorPublicKeyMock("server-dpub-active")),
              privateWalletRootXpub = null
            ),
            accountId = "server-id",
            f8eEnvironment = F8eEnvironment.Development
          )
        )
      )

    wallets.getValue(activeKeyset.localId).syncCalls.awaitItem()
    wallets.getValue(activeKeyset.localId).syncCalls.awaitItem()
    wallets.getValue(lostAppKeyset1.localId).syncCalls.awaitItem()
    wallets.getValue(lostAppKeyset2.localId).syncCalls.awaitItem()
  }

  test("destination spending address generation failed") {
    val keyboxWithLostAppKeyset = activeKeybox.copy(
      keysets = listOf(activeKeyset, lostAppKeyset1)
    )

    wallets.getValue(activeKeyset.localId).createPsbtResult = Ok(psbtMock)
    wallets.getValue(activeKeyset.localId).newAddressResult =
      Err(Generic(Exception("Dang."), null))
    wallets.getValue(lostAppKeyset1.localId).createPsbtResult = Ok(psbtMock)

    sweepGenerator.generateSweep(keyboxWithLostAppKeyset)
      .shouldBeErrOfType<SweepGeneratorError.FailedToGenerateDestinationAddress>()
  }

  test("lost hardware recovery") {
    val keyboxWithLostHwKeysets = activeKeybox.copy(
      keysets = listOf(activeKeyset, lostHwKeyset1, lostHwKeyset2)
    )

    val result =
      sweepGenerator.generateSweep(keyboxWithLostHwKeysets).shouldBeOkOfType<List<SweepPsbt>>()
    result.shouldHaveSize(2)
    result.shouldBe(
      listOf(
        SweepPsbt(
          psbtMock,
          SweepSignaturePlan.AppAndServer,
          lostHwKeyset1,
          destinationAddress
        ),
        SweepPsbt(
          psbtMock,
          SweepSignaturePlan.AppAndServer,
          lostHwKeyset2,
          destinationAddress
        )
      )
    )
    // two addresses to watch
    processorMock.processBatchCalls.awaitItem()
    processorMock.processBatchCalls.awaitItem()

    wallets.getValue(activeKeyset.localId).syncCalls.awaitItem()
    wallets.getValue(activeKeyset.localId).syncCalls.awaitItem()
    wallets.getValue(lostHwKeyset1.localId).syncCalls.awaitItem()
    wallets.getValue(lostHwKeyset2.localId).syncCalls.awaitItem()
  }

  test("no signable keysets - lost both") {
    val keyboxWithLostBothKeyset = activeKeybox.copy(
      keysets = listOf(activeKeyset, lostBothKeyset)
    )

    wallets.getValue(lostAppKeyset1.localId).createPsbtResult = Ok(psbtMock)

    val result =
      sweepGenerator.generateSweep(keyboxWithLostBothKeyset).shouldBeOkOfType<List<SweepPsbt>>()
    result.shouldBeEmpty()
  }

  test("some signable keysets") {
    val keyboxWithMixedKeysets = activeKeybox.copy(
      keysets = listOf(activeKeyset, lostBothKeyset, lostAppKeyset1, lostHwKeyset1)
    )

    wallets.getValue(lostAppKeyset1.localId).createPsbtResult = Ok(psbtMock)

    val result =
      sweepGenerator.generateSweep(keyboxWithMixedKeysets).shouldBeOkOfType<List<SweepPsbt>>()
    result.shouldBe(
      listOf(
        SweepPsbt(
          psbtMock,
          SweepSignaturePlan.HardwareAndServer,
          lostAppKeyset1,
          destinationAddress
        ),
        SweepPsbt(
          psbtMock,
          SweepSignaturePlan.AppAndServer,
          lostHwKeyset1,
          destinationAddress
        )
      )
    )
    // two addresses to watch
    processorMock.processBatchCalls.awaitItem()
    processorMock.processBatchCalls.awaitItem()

    wallets.getValue(activeKeyset.localId).syncCalls.awaitItem()
    wallets.getValue(activeKeyset.localId).syncCalls.awaitItem()
    wallets.getValue(lostAppKeyset1.localId).syncCalls.awaitItem()
    wallets.getValue(lostHwKeyset1.localId).syncCalls.awaitItem()
  }

  test("insufficient balance for one keyset should skip the keyset") {
    val keyboxWithLostHwKeysets = activeKeybox.copy(
      keysets = listOf(activeKeyset, lostHwKeyset1, lostHwKeyset2)
    )

    wallets.getValue(activeKeyset.localId).createPsbtResult = Ok(psbtMock)
    wallets.getValue(lostHwKeyset1.localId).createPsbtResult =
      Err(InsufficientFunds(Exception("too poor"), null))
    wallets.getValue(lostHwKeyset2.localId).createPsbtResult = Ok(psbtMock)

    sweepGenerator.generateSweep(keyboxWithLostHwKeysets)
      .shouldBe(
        Ok(
          listOf(
            SweepPsbt(
              psbtMock,
              SweepSignaturePlan.AppAndServer,
              lostHwKeyset2,
              destinationAddress
            )
          )
        )
      )
    // two addresses to watch
    processorMock.processBatchCalls.awaitItem()
    processorMock.processBatchCalls.awaitItem()

    wallets.getValue(activeKeyset.localId).syncCalls.awaitItem()
    wallets.getValue(activeKeyset.localId).syncCalls.awaitItem()
    wallets.getValue(lostHwKeyset1.localId).syncCalls.awaitItem()
    wallets.getValue(lostHwKeyset2.localId).syncCalls.awaitItem()
  }

  test("psbt create failure for one keyset should fail all") {
    val keyboxWithLostHwKeysets = activeKeybox.copy(
      keysets = listOf(activeKeyset, lostHwKeyset1, lostHwKeyset2)
    )

    wallets.getValue(activeKeyset.localId).createPsbtResult = Ok(psbtMock)
    wallets.getValue(lostHwKeyset1.localId).createPsbtResult =
      Err(Generic(Exception("Dang."), null))
    wallets.getValue(lostHwKeyset2.localId).createPsbtResult = Ok(psbtMock)

    sweepGenerator.generateSweep(keyboxWithLostHwKeysets).shouldBeErrOfType<BdkFailedToCreatePsbt>()
    // single address to watch, subsequent keysets fail and don't trigger
    processorMock.processBatchCalls.awaitItem()

    wallets.getValue(activeKeyset.localId).syncCalls.awaitItem()
    wallets.getValue(lostHwKeyset1.localId).syncCalls.awaitItem()
  }

  test("generateSweep falls back to F8e keysets when canUseKeyboxKeysets is false") {
    val keyboxWithoutLocalKeysets = activeKeybox.copy(
      canUseKeyboxKeysets = false,
      keysets = listOf(activeKeyset, lostAppKeyset1) // These local keysets should be ignored
    )

    val remoteKeysets = listOf(lostAppKeyset2, lostHwKeyset1).map { keyset ->
      LegacyRemoteKeyset(
        keysetId = keyset.f8eSpendingKeyset.keysetId,
        networkType = keyset.networkType.name,
        appDescriptor = keyset.appKey.key.dpub,
        hardwareDescriptor = keyset.hardwareKey.key.dpub,
        serverDescriptor = keyset.f8eSpendingKeyset.spendingPublicKey.key.dpub
      )
    }

    listKeysetsF8eClient.result = Ok(
      ListKeysetsResponse(
        keysets = remoteKeysets,
        wrappedSsek = null,
        descriptorBackups = emptyList()
      )
    )

    wallets.getValue(lostAppKeyset2.localId).createPsbtResult = Ok(psbtMock)
    wallets.getValue(lostHwKeyset1.localId).createPsbtResult = Ok(psbtMock)

    val result =
      sweepGenerator.generateSweep(keyboxWithoutLocalKeysets).shouldBeOkOfType<List<SweepPsbt>>()

    // Should have 2 sweep PSBTs from F8e keysets
    result.shouldHaveSize(2)
    result.forEach { sweep ->
      sweep.psbt shouldBe psbtMock
      sweep.destinationAddress shouldBe destinationAddress
    }
    result.map { it.sourceKeyset.f8eSpendingKeyset.keysetId to it.signaturePlan }
      .shouldContainExactly(
        "keyset-lost-app-server-2" to SweepSignaturePlan.HardwareAndServer,
        "keyset-lost-hw-server-1" to SweepSignaturePlan.AppAndServer
      )

    // two addresses to watch
    processorMock.processBatchCalls.awaitItem()
    processorMock.processBatchCalls.awaitItem()

    wallets.getValue(activeKeyset.localId).syncCalls.awaitItem()
    wallets.getValue(activeKeyset.localId).syncCalls.awaitItem()
    wallets.getValue(lostAppKeyset2.localId).syncCalls.awaitItem()
    wallets.getValue(lostHwKeyset1.localId).syncCalls.awaitItem()
  }

  test("uses peekAddress(0u) for private wallet destination keyset") {
    val peekedAddress = BitcoinAddress("bc1qpeek0000000000000000000000000000000000")
    val privateActiveKeyset = activeKeyset.copy(
      f8eSpendingKeyset = activeKeyset.f8eSpendingKeyset.copy(
        privateWalletRootXpub = "xpub-private-root"
      )
    )
    val keyboxWithPrivateDestination = activeKeybox.copy(
      activeSpendingKeyset = privateActiveKeyset,
      keysets = listOf(privateActiveKeyset, lostAppKeyset1)
    )

    // Configure the private active keyset wallet to return a specific peeked address
    wallets.getValue(privateActiveKeyset.localId).peekAddressResult = Ok(peekedAddress)
    wallets.getValue(privateActiveKeyset.localId).createPsbtResult = Ok(psbtMock)
    wallets.getValue(lostAppKeyset1.localId).createPsbtResult = Ok(psbtMock)

    val result = sweepGenerator.generateSweep(keyboxWithPrivateDestination)
      .shouldBeOkOfType<List<SweepPsbt>>()

    result.shouldHaveSize(1)
    result.first().destinationAddress.shouldBe(peekedAddress.address)

    wallets.getValue(privateActiveKeyset.localId).syncCalls.awaitItem()
    wallets.getValue(lostAppKeyset1.localId).syncCalls.awaitItem()

    processorMock.processBatchCalls.awaitItem()
  }

  test("private-to-private sweep applies tweaks correctly") {
    val tweakedPsbtBase64 = "tweaked-psbt-base64"

    // Create source private keyset
    val sourcePrivateKeyset = lostAppKeyset1.copy(
      f8eSpendingKeyset = lostAppKeyset1.f8eSpendingKeyset.copy(
        privateWalletRootXpub = "xpub-source-private-root"
      )
    )

    // Create destination private keyset
    val destPrivateKeyset = activeKeyset.copy(
      f8eSpendingKeyset = activeKeyset.f8eSpendingKeyset.copy(
        privateWalletRootXpub = "xpub-dest-private-root"
      )
    )

    val keyboxWithPrivateSweep = activeKeybox.copy(
      activeSpendingKeyset = destPrivateKeyset,
      keysets = listOf(destPrivateKeyset, sourcePrivateKeyset)
    )

    chaincodeDelegationTweakService.sweepPsbtWithTweaksResult =
      Ok(psbtMock.copy(base64 = tweakedPsbtBase64))

    wallets.getValue(destPrivateKeyset.localId).createPsbtResult = Ok(psbtMock)
    wallets.getValue(sourcePrivateKeyset.localId).createPsbtResult = Ok(psbtMock)
    wallets.getValue(destPrivateKeyset.localId).peekAddressResult =
      Ok(BitcoinAddress(destinationAddress))

    val result = sweepGenerator.generateSweep(keyboxWithPrivateSweep)
      .shouldBeOkOfType<List<SweepPsbt>>()

    result.shouldHaveSize(1)
    result.first().psbt.base64.shouldBe(tweakedPsbtBase64)
    result.first().signaturePlan.shouldBe(SweepSignaturePlan.HardwareAndServer)
    result.first().sourceKeyset.shouldBe(sourcePrivateKeyset)

    wallets.getValue(sourcePrivateKeyset.localId).syncCalls.awaitItem()
    wallets.getValue(destPrivateKeyset.localId).syncCalls.awaitItem()

    processorMock.processBatchCalls.awaitItem()
  }

  test("private destination sweep does not apply tweaks if using AppAndHardware signature plan") {
    // Source is legacy with same hw fingerprint as active keyset
    val sourceLegacyKeyset = lostAppKeyset1.copy(
      f8eSpendingKeyset = F8eSpendingKeyset(
        keysetId = "keyset-legacy-source",
        spendingPublicKey = F8eSpendingPublicKey(DescriptorPublicKeyMock("server-dpub-legacy")),
        privateWalletRootXpub = null
      )
    )

    // Destination is private
    val destPrivateKeyset = activeKeyset.copy(
      f8eSpendingKeyset = activeKeyset.f8eSpendingKeyset.copy(
        privateWalletRootXpub = "xpub-dest-private-root"
      )
    )

    val keyboxWithMigrationSweep = activeKeybox.copy(
      activeSpendingKeyset = destPrivateKeyset,
      keysets = listOf(destPrivateKeyset, sourceLegacyKeyset)
    )

    // Setup app private key for source keyset
    appPrivateKeyDao.appSpendingKeys[sourceLegacyKeyset.appKey] = AppSpendingPrivateKey(
      ExtendedPrivateKey(sourceLegacyKeyset.appKey.key.xpub, "mnemonic")
    )

    wallets.getValue(destPrivateKeyset.localId).createPsbtResult = Ok(psbtMock)
    wallets.getValue(sourceLegacyKeyset.localId).createPsbtResult = Ok(psbtMock)

    val result = sweepGenerator.generateSweep(keyboxWithMigrationSweep)
      .shouldBeOkOfType<List<SweepPsbt>>()

    result.shouldHaveSize(1)
    result.first().psbt.base64.shouldBe(psbtMock.base64)
    result.first().signaturePlan.shouldBe(SweepSignaturePlan.AppAndHardware)

    wallets.getValue(sourceLegacyKeyset.localId).syncCalls.awaitItem()
    wallets.getValue(destPrivateKeyset.localId).syncCalls.awaitItem()
    processorMock.processBatchCalls.awaitItem()
  }

  test("legacy-to-private inactive sweep applies migration tweaks correctly") {
    val tweakedPsbtBase64 = "migration-tweaked-psbt-base64"

    // Source is legacy with same hw fingerprint as active keyset
    val sourceLegacyKeyset = lostAppKeyset1.copy(
      f8eSpendingKeyset = F8eSpendingKeyset(
        keysetId = "keyset-legacy-source",
        spendingPublicKey = F8eSpendingPublicKey(DescriptorPublicKeyMock("server-dpub-legacy")),
        privateWalletRootXpub = null
      )
    )

    // Destination is private
    val destPrivateKeyset = activeKeyset.copy(
      f8eSpendingKeyset = activeKeyset.f8eSpendingKeyset.copy(
        privateWalletRootXpub = "xpub-dest-private-root"
      )
    )

    val keyboxWithMigrationSweep = activeKeybox.copy(
      activeSpendingKeyset = destPrivateKeyset,
      keysets = listOf(destPrivateKeyset, sourceLegacyKeyset)
    )

    chaincodeDelegationTweakService.migrationSweepPsbtWithTweaksResult =
      Ok(psbtMock.copy(base64 = tweakedPsbtBase64))

    wallets.getValue(destPrivateKeyset.localId).createPsbtResult = Ok(psbtMock)
    wallets.getValue(sourceLegacyKeyset.localId).createPsbtResult = Ok(psbtMock)
    wallets.getValue(destPrivateKeyset.localId).peekAddressResult =
      Ok(BitcoinAddress(destinationAddress))

    val result = sweepGenerator.generateSweep(keyboxWithMigrationSweep)
      .shouldBeOkOfType<List<SweepPsbt>>()

    result.shouldHaveSize(1)
    result.first().psbt.base64.shouldBe(tweakedPsbtBase64)
    result.first().signaturePlan.shouldBe(SweepSignaturePlan.HardwareAndServer)

    wallets.getValue(sourceLegacyKeyset.localId).syncCalls.awaitItem()
    wallets.getValue(destPrivateKeyset.localId).syncCalls.awaitItem()
    processorMock.processBatchCalls.awaitItem()
  }

  test("sweep to private destination fails when tweak computation fails") {
    val destPrivateKeyset = activeKeyset.copy(
      f8eSpendingKeyset = activeKeyset.f8eSpendingKeyset.copy(
        privateWalletRootXpub = "xpub-dest-private-root"
      )
    )

    val keyboxWithTweakFailure = activeKeybox.copy(
      activeSpendingKeyset = destPrivateKeyset,
      keysets = listOf(destPrivateKeyset, lostHwKeyset1)
    )

    chaincodeDelegationTweakService.migrationSweepPsbtWithTweaksResult =
      Err(
        ChaincodeDelegationError.TweakComputation(
          cause = Exception("Tweak computation failed"),
          message = "Failed to compute tweaks"
        )
      )

    wallets.getValue(destPrivateKeyset.localId).createPsbtResult = Ok(psbtMock)
    wallets.getValue(lostHwKeyset1.localId).createPsbtResult = Ok(psbtMock)
    wallets.getValue(destPrivateKeyset.localId).peekAddressResult =
      Ok(BitcoinAddress(destinationAddress))

    val result = sweepGenerator.generateSweep(keyboxWithTweakFailure)

    result.shouldBeErrOfType<SweepGeneratorError.FailedToTweakPsbt>()

    wallets.getValue(lostHwKeyset1.localId).syncCalls.awaitItem()
    wallets.getValue(destPrivateKeyset.localId).syncCalls.awaitItem()
    processorMock.processBatchCalls.awaitItem()
  }

  test("migration with multiple old keysets sweeps from all") {
    val oldMultisigKeyset1 = lostAppKeyset1.copy(
      f8eSpendingKeyset = F8eSpendingKeyset(
        keysetId = "old-multisig-server-1",
        spendingPublicKey = F8eSpendingPublicKey(DescriptorPublicKeyMock("server-dpub-old-1")),
        privateWalletRootXpub = null
      )
    )

    val oldMultisigKeyset2 = lostAppKeyset2.copy(
      f8eSpendingKeyset = F8eSpendingKeyset(
        keysetId = "old-multisig-server-2",
        spendingPublicKey = F8eSpendingPublicKey(DescriptorPublicKeyMock("server-dpub-old-2")),
        privateWalletRootXpub = null
      )
    )

    val privateActiveKeyset = activeKeyset.copy(
      f8eSpendingKeyset = F8eSpendingKeyset(
        keysetId = "private-active-server",
        spendingPublicKey = F8eSpendingPublicKey(DescriptorPublicKeyMock("server-dpub-private")),
        privateWalletRootXpub = "xpub-private"
      )
    )

    val keyboxWithMultipleOld = activeKeybox.copy(
      activeSpendingKeyset = privateActiveKeyset,
      keysets = listOf(privateActiveKeyset, oldMultisigKeyset1, oldMultisigKeyset2)
    )

    wallets.getValue(activeKeyset.localId).createPsbtResult = Ok(psbtMock)
    wallets.getValue(lostAppKeyset1.localId).createPsbtResult = Ok(psbtMock)
    wallets.getValue(lostAppKeyset2.localId).createPsbtResult = Ok(psbtMock)

    val result = sweepGenerator.generateSweep(keyboxWithMultipleOld).shouldBeOkOfType<List<SweepPsbt>>()

    result.shouldHaveSize(2)
    result.map { it.sourceKeyset.f8eSpendingKeyset.keysetId }
      .shouldContainExactlyInAnyOrder("old-multisig-server-1", "old-multisig-server-2")
    // Assert that no tweaks are applied during PrivateWalletMigration
    result.forEach { it.psbt.base64 shouldBe "migration-tweaked-psbt" }

    // Consume turbine calls (2 addresses to watch + 4 sync calls)
    processorMock.processBatchCalls.awaitItem()
    processorMock.processBatchCalls.awaitItem()
    wallets.getValue(activeKeyset.localId).syncCalls.awaitItem()
    wallets.getValue(activeKeyset.localId).syncCalls.awaitItem()
    wallets.getValue(lostAppKeyset1.localId).syncCalls.awaitItem()
    wallets.getValue(lostAppKeyset2.localId).syncCalls.awaitItem()
  }

  test("post-migration sweep detected and missing hw uses AppAndServer") {
    // Use a keyset where hardware is from a different device (different fingerprint)
    // to simulate lost hardware scenario
    val oldMultisigKeyset = lostHwKeyset1.copy(
      f8eSpendingKeyset = F8eSpendingKeyset(
        keysetId = "old-multisig-server",
        spendingPublicKey = F8eSpendingPublicKey(DescriptorPublicKeyMock("server-dpub-old")),
        privateWalletRootXpub = null // Legacy keyset
      )
    )

    val privateActiveKeyset = activeKeyset.copy(
      f8eSpendingKeyset = F8eSpendingKeyset(
        keysetId = "private-active-server",
        spendingPublicKey = F8eSpendingPublicKey(DescriptorPublicKeyMock("server-dpub-private")),
        privateWalletRootXpub = "xpub-private" // Private keyset
      )
    )

    val keyboxAfterMigration = activeKeybox.copy(
      activeSpendingKeyset = privateActiveKeyset,
      keysets = listOf(privateActiveKeyset, oldMultisigKeyset)
    )

    wallets.getValue(activeKeyset.localId).createPsbtResult = Ok(psbtMock)
    wallets.getValue(lostHwKeyset1.localId).createPsbtResult = Ok(psbtMock)

    val result = sweepGenerator.generateSweep(keyboxAfterMigration).shouldBeOkOfType<List<SweepPsbt>>()

    result.shouldHaveSize(1)
    result.first().signaturePlan shouldBe SweepSignaturePlan.AppAndServer
    result.first().sourceKeyset.f8eSpendingKeyset.keysetId shouldBe "old-multisig-server"

    processorMock.processBatchCalls.awaitItem()
    wallets.getValue(activeKeyset.localId).syncCalls.awaitItem()
    wallets.getValue(lostHwKeyset1.localId).syncCalls.awaitItem()
  }

  test("post-migration sweep detected and missing app uses HardwareAndServer") {
    // Use a keyset without app key in dao to simulate lost app scenario
    val oldMultisigKeyset = lostAppKeyset1.copy(
      f8eSpendingKeyset = F8eSpendingKeyset(
        keysetId = "old-multisig-server",
        spendingPublicKey = F8eSpendingPublicKey(DescriptorPublicKeyMock("server-dpub-old")),
        privateWalletRootXpub = null // Legacy keyset
      )
    )

    val privateActiveKeyset = activeKeyset.copy(
      f8eSpendingKeyset = F8eSpendingKeyset(
        keysetId = "private-active-server",
        spendingPublicKey = F8eSpendingPublicKey(DescriptorPublicKeyMock("server-dpub-private")),
        privateWalletRootXpub = "xpub-private" // Private keyset
      )
    )

    val keyboxAfterMigration = activeKeybox.copy(
      activeSpendingKeyset = privateActiveKeyset,
      keysets = listOf(privateActiveKeyset, oldMultisigKeyset)
    )

    wallets.getValue(activeKeyset.localId).createPsbtResult = Ok(psbtMock)
    wallets.getValue(lostAppKeyset1.localId).createPsbtResult = Ok(psbtMock)

    val result = sweepGenerator.generateSweep(keyboxAfterMigration).shouldBeOkOfType<List<SweepPsbt>>()

    result.shouldHaveSize(1)
    result.first().signaturePlan shouldBe SweepSignaturePlan.HardwareAndServer
    result.first().sourceKeyset.f8eSpendingKeyset.keysetId shouldBe "old-multisig-server"

    processorMock.processBatchCalls.awaitItem()
    wallets.getValue(activeKeyset.localId).syncCalls.awaitItem()
    wallets.getValue(lostAppKeyset1.localId).syncCalls.awaitItem()
  }

  test("private wallet without local keysets returns error") {
    val privateActiveKeyset = activeKeyset.copy(
      f8eSpendingKeyset = F8eSpendingKeyset(
        keysetId = "private-active-server",
        spendingPublicKey = F8eSpendingPublicKey(DescriptorPublicKeyMock("server-dpub-private")),
        privateWalletRootXpub = "xpub-private"
      )
    )

    val privateKeyboxWithoutLocal = activeKeybox.copy(
      activeSpendingKeyset = privateActiveKeyset,
      keysets = listOf(privateActiveKeyset),
      canUseKeyboxKeysets = false // This is the invalid state
    )

    val result = sweepGenerator.generateSweep(privateKeyboxWithoutLocal)

    result.shouldBeErr(SweepGeneratorError.PrivateWalletMissingLocalKeysets)
  }

  test("checks for descriptor backup before address generation") {
    descriptorBackupService.checkBackupForPrivateKeysetResult = Err(IllegalStateException("Backup not found"))

    val keybox = activeKeybox.copy(
      activeSpendingKeyset = PrivateSpendingKeysetMock,
      keysets = listOf(PrivateSpendingKeysetMock, lostHwKeyset1)
    )
    wallets.getValue(lostAppKeyset1.localId).createPsbtResult = Ok(psbtMock)

    val result = sweepGenerator.generateSweep(keybox)
    result.shouldBeErrOfType<SweepGeneratorError.FailedToGenerateDestinationAddress>()
  }

  test("skips address upload when context is Estimate") {
    val keyboxWithLostAppKeyset = activeKeybox.copy(
      keysets = listOf(activeKeyset, lostAppKeyset1, lostAppKeyset2)
    )

    wallets.getValue(activeKeyset.localId).createPsbtResult = Ok(psbtMock)
    wallets.getValue(lostAppKeyset1.localId).createPsbtResult = Ok(psbtMock)
    wallets.getValue(lostAppKeyset2.localId).createPsbtResult = Ok(psbtMock)

    // Generate sweep with Estimate context
    val result = sweepGenerator.generateSweep(
      keyboxWithLostAppKeyset,
      SweepGenerationContext.Estimate
    ).shouldBeOkOfType<List<SweepPsbt>>()

    // Should still generate PSBTs successfully
    result.shouldHaveSize(2)
    result.shouldBe(
      listOf(
        SweepPsbt(
          psbtMock,
          SweepSignaturePlan.HardwareAndServer,
          lostAppKeyset1,
          destinationAddress
        ),
        SweepPsbt(
          psbtMock,
          SweepSignaturePlan.HardwareAndServer,
          lostAppKeyset2,
          destinationAddress
        )
      )
    )

    // Verify no address registration calls were made
    processorMock.processBatchCalls.expectNoEvents()

    // Verify wallets were still synced
    wallets.getValue(activeKeyset.localId).syncCalls.awaitItem()
    wallets.getValue(activeKeyset.localId).syncCalls.awaitItem()
    wallets.getValue(lostAppKeyset1.localId).syncCalls.awaitItem()
    wallets.getValue(lostAppKeyset2.localId).syncCalls.awaitItem()
  }
})
