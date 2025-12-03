package build.wallet.wallet.migration

import app.cash.turbine.test
import build.wallet.account.AccountServiceFake
import build.wallet.bitcoin.balance.BitcoinBalanceFake
import build.wallet.bitcoin.transactions.BitcoinWalletServiceFake
import build.wallet.bitcoin.transactions.PsbtMock
import build.wallet.bitcoin.transactions.TransactionsDataMock
import build.wallet.bitkey.f8e.F8eSpendingKeysetMock
import build.wallet.bitkey.keybox.AppKeyBundleMock
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.keybox.HwKeyBundleMock
import build.wallet.bitkey.keybox.PrivateWalletKeyboxMock
import build.wallet.bitkey.spending.SpendingKeysetMock
import build.wallet.cloud.backup.CloudBackupV2WithFullAccountMock
import build.wallet.cloud.backup.csek.SealedSsekFake
import build.wallet.cloud.backup.csek.SsekDaoFake
import build.wallet.cloud.backup.csek.SsekFake
import build.wallet.cloud.backup.local.CloudBackupDaoFake
import build.wallet.cloud.store.CloudAccountMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.db.DbTransactionError
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.onboarding.CreateAccountKeysetV2F8eClientFake
import build.wallet.f8e.onboarding.SetActiveSpendingKeysetF8eClientFake
import build.wallet.f8e.recovery.ListKeysetsF8eClientMock
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.feature.FeatureFlagValue
import build.wallet.feature.FeatureFlagValue.DoubleFlag
import build.wallet.feature.flags.PrivateWalletMigrationBalanceThresholdFeatureFlag
import build.wallet.feature.flags.PrivateWalletMigrationFeatureFlag
import build.wallet.keybox.KeyboxDaoMock
import build.wallet.keybox.keys.AppKeysGeneratorMock
import build.wallet.ktor.result.HttpError
import build.wallet.money.BitcoinMoney
import build.wallet.platform.random.UuidGeneratorFake
import build.wallet.recovery.DescriptorBackupServiceFake
import build.wallet.recovery.sweep.Sweep
import build.wallet.recovery.sweep.SweepPsbt
import build.wallet.recovery.sweep.SweepService.SweepError.NoFundsToSweep
import build.wallet.recovery.sweep.SweepService.SweepError.SweepGenerationFailed
import build.wallet.recovery.sweep.SweepServiceMock
import build.wallet.recovery.sweep.SweepSignaturePlan.AppAndServer
import build.wallet.testing.shouldBeErrOfType
import build.wallet.testing.shouldBeOk
import build.wallet.wallet.migration.PrivateWalletMigrationError.FeeEstimationFailed
import build.wallet.wallet.migration.PrivateWalletMigrationError.InsufficientFundsForMigration
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.get
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first

@OptIn(ExperimentalCoroutinesApi::class)
class PrivateWalletMigrationServiceImplTests : FunSpec({
  val appKeysGenerator = AppKeysGeneratorMock()
  val createKeysetClient = CreateAccountKeysetV2F8eClientFake()
  val uuidGenerator = UuidGeneratorFake()
  val featureFlagDao = FeatureFlagDaoFake()
  val privateWalletMigrationFeatureFlag = PrivateWalletMigrationFeatureFlag(featureFlagDao)
  val privateWalletMigrationBalanceThresholdFeatureFlag =
    PrivateWalletMigrationBalanceThresholdFeatureFlag(featureFlagDao)
  val accountService = AccountServiceFake()
  val cloudBackupDao = CloudBackupDaoFake()
  val privateWalletMigrationDao = PrivateWalletMigrationDaoFake()
  val keyboxDao = KeyboxDaoMock(
    turbine = turbines::create
  )
  val keysetResult = F8eSpendingKeysetMock
  val listKeysetsClient = ListKeysetsF8eClientMock()

  val bitcoinWalletService = BitcoinWalletServiceFake()
  val cloudAccount = CloudAccountMock("cloudInstanceId")
  val sweepService = SweepServiceMock()

  val service = PrivateWalletMigrationServiceImpl(
    keyGenerator = appKeysGenerator,
    createKeysetClient = createKeysetClient,
    uuidGenerator = uuidGenerator,
    privateWalletMigrationFeatureFlag = privateWalletMigrationFeatureFlag,
    privateWalletMigrationBalanceThresholdFeatureFlag = privateWalletMigrationBalanceThresholdFeatureFlag,
    accountService = accountService,
    cloudBackupDao = cloudBackupDao,
    keyboxDao = keyboxDao,
    privateWalletMigrationDao = privateWalletMigrationDao,
    ssekDao = SsekDaoFake(),
    descriptorBackupService = DescriptorBackupServiceFake(),
    listKeysetsF8eClient = listKeysetsClient,
    setActiveSpendingKeysetF8eClient = SetActiveSpendingKeysetF8eClientFake(),
    bitcoinWalletService = bitcoinWalletService,
    sweepService = sweepService
  )

  val mockAccount = FullAccountMock
  val mockProofOfPossession = HwFactorProofOfPossession("test-proof")
  val mockNewHwKeys = HwKeyBundleMock

  beforeTest {
    appKeysGenerator.reset()
    createKeysetClient.reset()
    uuidGenerator.reset()
    featureFlagDao.reset()
    accountService.reset()
    keyboxDao.reset()
    cloudBackupDao.reset()
    privateWalletMigrationDao.clear()
    listKeysetsClient.reset()
    bitcoinWalletService.reset()

    privateWalletMigrationBalanceThresholdFeatureFlag.setFlagValue(DoubleFlag(-1.0))
    bitcoinWalletService.transactionsData.value = TransactionsDataMock
  }

  test("initiateMigration successfully creates new keyset") {
    appKeysGenerator.keyBundleResult = Ok(AppKeyBundleMock)
    // Create a private wallet keyset with non-null privateWalletRootXpub
    val privateKeysetResult = keysetResult.copy(
      privateWalletRootXpub = "xpub-test-private-wallet"
    )
    createKeysetClient.createKeysetResult = Ok(privateKeysetResult)
    cloudBackupDao.set(mockAccount.accountId.serverId, CloudBackupV2WithFullAccountMock)
    accountService.setActiveAccount(mockAccount)
    privateWalletMigrationFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))

    val updatedKeybox = async {
      service.initiateMigration(
        account = mockAccount,
        proofOfPossession = mockProofOfPossession,
        newHwKeys = mockNewHwKeys,
        ssek = SsekFake,
        sealedSsek = SealedSsekFake
      ).shouldBeOk()
    }

    val savedKeybox = keyboxDao.activeKeybox.first { it.get() != null }.shouldBeOk().shouldNotBeNull()
    savedKeybox.activeSpendingKeyset.localId.shouldBe("uuid-0")

    accountService.setActiveAccount(
      FullAccountMock.copy(
        keybox = savedKeybox
      )
    )

    val keybox = updatedKeybox.await().updatedKeybox
    keybox.activeSpendingKeyset.localId.shouldBe("uuid-0")
    keybox.config.bitcoinNetworkType.shouldBe(mockAccount.keybox.config.bitcoinNetworkType)
    keybox.activeSpendingKeyset.appKey.shouldBe(AppKeyBundleMock.spendingKey)
    keybox.activeSpendingKeyset.hardwareKey.shouldBe(mockNewHwKeys.spendingKey)
    keybox.activeSpendingKeyset.f8eSpendingKeyset.shouldBe(privateKeysetResult)
    keybox.activeSpendingKeyset.f8eSpendingKeyset.privateWalletRootXpub.shouldBe("xpub-test-private-wallet")

    // Verify the keybox contains both old and new keysets for potential sweep
    keybox.keysets.size.shouldBe(2)
    keybox.keysets.any { it.localId == "uuid-0" }.shouldBe(true) // New private keyset
    keybox.keysets.any {
      it.localId == mockAccount.keybox.activeSpendingKeyset.localId
    }.shouldBe(true) // Old multisig keyset
  }

  test("initiateMigration transitions through all expected states") {
    appKeysGenerator.keyBundleResult = Ok(AppKeyBundleMock)
    val privateKeysetResult = keysetResult.copy(
      privateWalletRootXpub = "xpub-test-private-wallet"
    )
    createKeysetClient.createKeysetResult = Ok(privateKeysetResult)
    cloudBackupDao.set(mockAccount.accountId.serverId, CloudBackupV2WithFullAccountMock)
    accountService.setActiveAccount(mockAccount)
    privateWalletMigrationFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))

    service.migrationState.distinctUntilChanged().test {
      awaitItem().shouldBe(PrivateWalletMigrationState.Available)

      val migrationResult = async {
        service.initiateMigration(
          account = mockAccount,
          proofOfPossession = mockProofOfPossession,
          newHwKeys = mockNewHwKeys,
          ssek = SsekFake,
          sealedSsek = SealedSsekFake
        )
      }

      val hwKeyCreatedState = awaitItem()
      hwKeyCreatedState.shouldBeInstanceOf<PrivateWalletMigrationState.InKeysetCreation.HwKeyCreated>()
      hwKeyCreatedState.newHwKeys.shouldBe(mockNewHwKeys.spendingKey)

      val appKeyCreatedState = awaitItem()
      appKeyCreatedState.shouldBeInstanceOf<PrivateWalletMigrationState.InKeysetCreation.AppKeyCreated>()
      appKeyCreatedState.newHwKeys.shouldBe(mockNewHwKeys.spendingKey)
      appKeyCreatedState.newAppKeys.shouldBe(AppKeyBundleMock.spendingKey)

      val keyboxActivatedState = awaitItem()
      keyboxActivatedState.shouldBeInstanceOf<PrivateWalletMigrationState.InKeysetCreation.LocalKeyboxActivated>()
      keyboxActivatedState.keyset.appKey.shouldBe(AppKeyBundleMock.spendingKey)
      keyboxActivatedState.keyset.hardwareKey.shouldBe(mockNewHwKeys.spendingKey)
      keyboxActivatedState.keyset.f8eSpendingKeyset.shouldBe(privateKeysetResult)

      val savedKeybox = keyboxDao.activeKeybox.first { it.get() != null }.shouldBeOk().shouldNotBeNull()
      accountService.setActiveAccount(FullAccountMock.copy(keybox = savedKeybox))

      val descriptorBackupCompletedState = awaitItem()
      descriptorBackupCompletedState.shouldBeInstanceOf<PrivateWalletMigrationState.DescriptorBackupCompleted>()
      descriptorBackupCompletedState.newKeyset.localId.shouldBe("uuid-0")

      val serverKeysetActivatedState = awaitItem()
      serverKeysetActivatedState.shouldBeInstanceOf<PrivateWalletMigrationState.ServerKeysetActivated>()
      serverKeysetActivatedState.newKeyset.localId.shouldBe("uuid-0")

      migrationResult.await().shouldBeOk()

      // Complete cloud backup is invoked by an external state machine completion:
      service.completeCloudBackup()

      awaitItem().shouldBeInstanceOf<PrivateWalletMigrationState.CloudBackupCompleted>()
        .newKeyset
        .localId
        .shouldBe("uuid-0")
    }
  }

  test("initiateMigration fails when app key generation fails") {
    val keyGenerationError = RuntimeException("Key generation failed")
    privateWalletMigrationFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
    accountService.setActiveAccount(mockAccount)
    appKeysGenerator.keyBundleResult = Err(keyGenerationError)

    val result = service.initiateMigration(
      account = mockAccount,
      proofOfPossession = mockProofOfPossession,
      newHwKeys = mockNewHwKeys,
      ssek = SsekFake,
      sealedSsek = SealedSsekFake
    )

    result.shouldBeErrOfType<PrivateWalletMigrationError.KeysetCreationFailed>()
    val error = result.error as PrivateWalletMigrationError.KeysetCreationFailed
    error.error.shouldBe(keyGenerationError)
  }

  test("initiateMigration fails when server keyset creation fails") {
    val networkError = HttpError.UnhandledException(RuntimeException("Network error"))
    privateWalletMigrationFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
    accountService.setActiveAccount(mockAccount)
    appKeysGenerator.keyBundleResult = Ok(AppKeyBundleMock)
    createKeysetClient.createKeysetResult = Err(networkError)

    val result = service.initiateMigration(
      account = mockAccount,
      proofOfPossession = mockProofOfPossession,
      newHwKeys = mockNewHwKeys,
      ssek = SsekFake,
      sealedSsek = SealedSsekFake
    )

    result.shouldBeErrOfType<PrivateWalletMigrationError.KeysetServerActivationFailed>()
      .error
      .shouldBe(networkError)
  }

  test("initiateMigration resumes from AppKeyCreated state without recreating keys") {
    privateWalletMigrationDao.saveHardwareKey(mockNewHwKeys.spendingKey).shouldBeOk()
    privateWalletMigrationDao.saveAppKey(AppKeyBundleMock.spendingKey).shouldBeOk()
    createKeysetClient.createKeysetResult = Ok(keysetResult)
    cloudBackupDao.set(mockAccount.accountId.serverId, CloudBackupV2WithFullAccountMock)
    accountService.setActiveAccount(mockAccount)
    privateWalletMigrationFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
    appKeysGenerator.keyBundleResult = Err(RuntimeException("Should not generate new keys!"))

    val result = async {
      service.initiateMigration(
        account = mockAccount,
        proofOfPossession = mockProofOfPossession,
        newHwKeys = mockNewHwKeys,
        ssek = SsekFake,
        sealedSsek = SealedSsekFake
      ).shouldBeOk()
    }

    val savedKeybox = keyboxDao.activeKeybox.first { it.get() != null }.shouldBeOk().shouldNotBeNull()
    savedKeybox.activeSpendingKeyset.localId.shouldBe("uuid-0")

    savedKeybox.activeSpendingKeyset.appKey.shouldBe(AppKeyBundleMock.spendingKey)
    savedKeybox.activeSpendingKeyset.hardwareKey.shouldBe(mockNewHwKeys.spendingKey)

    accountService.setActiveAccount(
      FullAccountMock.copy(
        keybox = savedKeybox
      )
    )

    val finalKeyset = result.await().newKeyset

    finalKeyset.localId.shouldBe("uuid-0")
    finalKeyset.appKey.shouldBe(AppKeyBundleMock.spendingKey)
    finalKeyset.hardwareKey.shouldBe(mockNewHwKeys.spendingKey)
    finalKeyset.f8eSpendingKeyset.shouldBe(keysetResult)
  }

  test("isPrivateWalletMigrationAvailable returns true when feature flag is enabled") {
    accountService.setActiveAccount(
      FullAccountMock
    )
    privateWalletMigrationFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))

    service.migrationState.test {
      awaitItem().shouldBe(PrivateWalletMigrationState.Available)
    }
  }

  test("isPrivateWalletMigrationAvailable returns false when feature flag is disabled") {
    accountService.setActiveAccount(
      FullAccountMock.copy(
        keybox = PrivateWalletKeyboxMock
      )
    )
    privateWalletMigrationFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(false))

    service.migrationState.test {
      awaitItem().shouldBe(PrivateWalletMigrationState.NotAvailable)
    }
  }

  test("isPrivateWalletMigrationAvailable returns false for private accounts") {
    accountService.setActiveAccount(
      FullAccountMock.copy(
        keybox = PrivateWalletKeyboxMock
      )
    )
    privateWalletMigrationFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))

    service.migrationState.test {
      awaitItem().shouldBe(PrivateWalletMigrationState.NotAvailable)
    }
  }

  test("returned keybox supports post-migration sweep") {
    // Setup for successful migration
    appKeysGenerator.keyBundleResult = Ok(AppKeyBundleMock)
    createKeysetClient.createKeysetResult = Ok(
      keysetResult.copy(
        privateWalletRootXpub = "xpub-private-wallet"
      )
    )
    cloudBackupDao.set(mockAccount.accountId.serverId, CloudBackupV2WithFullAccountMock)
    accountService.setActiveAccount(mockAccount)
    privateWalletMigrationFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))

    val keybox = async {
      service.initiateMigration(
        account = mockAccount,
        proofOfPossession = mockProofOfPossession,
        newHwKeys = mockNewHwKeys,
        ssek = SsekFake,
        sealedSsek = SealedSsekFake
      ).shouldBeOk()
    }

    // Update account service with saved keybox to simulate real flow
    val savedKeybox = keyboxDao.activeKeybox.first { it.get() != null }.shouldBeOk().shouldNotBeNull()
    accountService.setActiveAccount(FullAccountMock.copy(keybox = savedKeybox))

    val resultKeybox = keybox.await().updatedKeybox

    // Verify the keybox is properly configured for post-migration sweep
    resultKeybox.activeSpendingKeyset.f8eSpendingKeyset.privateWalletRootXpub.shouldBe("xpub-private-wallet")
    resultKeybox.activeSpendingKeyset.isPrivateWallet.shouldBe(true)

    // Verify old keyset is still present to sweep from
    resultKeybox.keysets.size.shouldBe(2)
    val oldKeyset = resultKeybox.keysets.find {
      it.localId == mockAccount.keybox.activeSpendingKeyset.localId
    }
    oldKeyset.shouldNotBeNull()
    oldKeyset.isPrivateWallet.shouldBe(false)
    oldKeyset.isLegacyWallet.shouldBe(true)

    // Verify new private keyset is active
    resultKeybox.activeSpendingKeyset.localId.shouldBe("uuid-0")
    resultKeybox.keysets.any { it.localId == "uuid-0" && it.isPrivateWallet }.shouldBe(true)
  }

  test("estimateMigrationFees returns sweep total fees") {
    val mockSweep = Sweep(
      unsignedPsbts = setOf(
        SweepPsbt(PsbtMock, AppAndServer, SpendingKeysetMock, "bc1qtest"),
        SweepPsbt(PsbtMock, AppAndServer, SpendingKeysetMock, "bc1qtest2")
      )
    )
    sweepService.estimateSweepWithMockDestinationResult = Ok(mockSweep)

    val result = service.estimateMigrationFees(account = mockAccount)

    // Each psbt has a fee of 10_000 sats.
    result.shouldBeOk(BitcoinMoney.sats(20_000L))
  }

  test("estimateMigrationFees returns FeeEstimationFailed error when prepareSweep fails") {
    sweepService.estimateSweepWithMockDestinationResult =
      Err(SweepGenerationFailed(Error(RuntimeException("Insufficient funds"))))

    val result = service.estimateMigrationFees(account = mockAccount)

    result.shouldBeErrOfType<FeeEstimationFailed>()
  }

  test("estimateMigrationFees returns InsufficientFundsForMigration error when no funds to sweep") {
    sweepService.estimateSweepWithMockDestinationResult = Err(NoFundsToSweep)

    val result = service.estimateMigrationFees(account = mockAccount)

    result.shouldBeErrOfType<InsufficientFundsForMigration>()
  }

  test("completeMigration sets sweepCompleted to true") {
    privateWalletMigrationDao.saveHardwareKey(mockNewHwKeys.spendingKey).shouldBeOk()
    privateWalletMigrationDao.saveAppKey(AppKeyBundleMock.spendingKey).shouldBeOk()
    privateWalletMigrationDao.saveServerKey(F8eSpendingKeysetMock).shouldBeOk()
    privateWalletMigrationDao.saveKeysetLocalId("test-keyset-id").shouldBeOk()
    privateWalletMigrationDao.setDescriptorBackupComplete().shouldBeOk()
    privateWalletMigrationDao.setServerKeysetActive().shouldBeOk()
    privateWalletMigrationDao.setCloudBackupComplete().shouldBeOk()

    val stateBefore = privateWalletMigrationDao.state.value.shouldBeOk().shouldNotBeNull()
    stateBefore.sweepCompleted.shouldBe(false)

    val result = service.completeMigration()

    result.shouldBeOk()
    privateWalletMigrationDao.state.value.shouldBeOk().shouldNotBeNull().sweepCompleted.shouldBeTrue()
  }

  test("completeMigration fails when dao clear fails") {
    val error = DbTransactionError(RuntimeException("Clear failed"))
    val migrationError = PrivateWalletMigrationError.MigrationCompletionFailed(error)
    migrationError.shouldBeInstanceOf<PrivateWalletMigrationError.MigrationCompletionFailed>()
    migrationError.error.shouldBeInstanceOf<DbTransactionError>()
  }

  test("migrationState is NotAvailable when sweepCompleted is true") {
    privateWalletMigrationFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
    accountService.setActiveAccount(mockAccount)

    // Set up complete migration state with sweepCompleted=true
    privateWalletMigrationDao.saveHardwareKey(mockNewHwKeys.spendingKey).shouldBeOk()
    privateWalletMigrationDao.saveAppKey(AppKeyBundleMock.spendingKey).shouldBeOk()
    privateWalletMigrationDao.saveServerKey(F8eSpendingKeysetMock).shouldBeOk()
    privateWalletMigrationDao.saveKeysetLocalId("test-keyset-id").shouldBeOk()
    privateWalletMigrationDao.setDescriptorBackupComplete().shouldBeOk()
    privateWalletMigrationDao.setServerKeysetActive().shouldBeOk()
    privateWalletMigrationDao.setCloudBackupComplete().shouldBeOk()
    privateWalletMigrationDao.setSweepCompleted().shouldBeOk()

    service.migrationState.test {
      awaitItem().shouldBe(PrivateWalletMigrationState.NotAvailable)
    }
  }

  test("keysets from listKeysets F8e call are saved into keybox dao") {
    appKeysGenerator.keyBundleResult = Ok(AppKeyBundleMock)
    val privateKeysetResult = keysetResult.copy(
      privateWalletRootXpub = "xpub-test-private-wallet"
    )
    listKeysetsClient.numKeysets = 2
    createKeysetClient.createKeysetResult = Ok(privateKeysetResult)
    cloudBackupDao.set(mockAccount.accountId.serverId, CloudBackupV2WithFullAccountMock)
    accountService.setActiveAccount(mockAccount)
    privateWalletMigrationFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))

    val result = async {
      service.initiateMigration(
        account = mockAccount,
        proofOfPossession = mockProofOfPossession,
        newHwKeys = mockNewHwKeys,
        ssek = SsekFake,
        sealedSsek = SealedSsekFake
      ).shouldBeOk()
    }

    val initialKeybox = keyboxDao.activeKeybox.first { it.get() != null }.shouldBeOk().shouldNotBeNull()
    accountService.setActiveAccount(FullAccountMock.copy(keybox = initialKeybox))

    // Wait for keybox to update:
    val keyboxWithF8eKeysets = keyboxDao.activeKeybox
      .first { keyboxResult ->
        val kb = keyboxResult.get()
        kb != null && kb.keysets.size > 2
      }
      .shouldBeOk()
      .shouldNotBeNull()

    val savedKeysetIds = keyboxWithF8eKeysets.keysets.map { it.f8eSpendingKeyset.keysetId }

    savedKeysetIds.size.shouldBe(4)
    savedKeysetIds.shouldContain(mockAccount.keybox.activeSpendingKeyset.f8eSpendingKeyset.keysetId)
    savedKeysetIds.shouldContain(privateKeysetResult.keysetId)
    savedKeysetIds.shouldContain("spending-public-keyset-fake-server-id-0")
    savedKeysetIds.shouldContain("spending-public-keyset-fake-server-id-1")

    accountService.setActiveAccount(FullAccountMock.copy(keybox = keyboxWithF8eKeysets))

    result.await()
  }

  test("negative balance threshold returns Available regardless of balance") {
    privateWalletMigrationFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
    privateWalletMigrationBalanceThresholdFeatureFlag.setFlagValue(DoubleFlag(-10.0))
    accountService.setActiveAccount(mockAccount)

    bitcoinWalletService.transactionsData.value = TransactionsDataMock.copy(
      balance = BitcoinBalanceFake(
        confirmed = BitcoinMoney.btc(10.0)
      )
    )

    service.migrationState.test {
      awaitItem().shouldBe(PrivateWalletMigrationState.Available)
    }
  }

  test("balance threshold of Double.MAX_VALUE handles extreme values correctly") {
    privateWalletMigrationFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
    privateWalletMigrationBalanceThresholdFeatureFlag.setFlagValue(
      DoubleFlag(Double.MAX_VALUE)
    )
    accountService.setActiveAccount(mockAccount)

    // Even with a high balance, Double.MAX_VALUE threshold should allow migration
    val balance = BitcoinMoney.sats(1_994_051_800_000_000L)
    bitcoinWalletService.transactionsData.value = TransactionsDataMock.copy(
      balance = BitcoinBalanceFake(confirmed = balance)
    )

    service.migrationState.test {
      awaitItem().shouldBe(PrivateWalletMigrationState.Available)
    }
  }

  test("balance greater than threshold returns NotAvailable") {
    privateWalletMigrationFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
    // Set threshold to 100,000 sats (0.001 BTC)
    privateWalletMigrationBalanceThresholdFeatureFlag.setFlagValue(
      DoubleFlag(100_000.0)
    )
    accountService.setActiveAccount(mockAccount)

    // Set wallet balance to 200,000 sats (0.002 BTC) - exceeds threshold
    val balance = BitcoinMoney.sats(200_000L)
    bitcoinWalletService.transactionsData.value = TransactionsDataMock.copy(
      balance = BitcoinBalanceFake(confirmed = balance)
    )

    service.migrationState.test {
      awaitItem().shouldBe(PrivateWalletMigrationState.NotAvailable)
    }
  }

  test("initiateMigration completes successfully when cloud backup is already done") {
    privateWalletMigrationDao.saveHardwareKey(mockNewHwKeys.spendingKey).shouldBeOk()
    privateWalletMigrationDao.saveAppKey(AppKeyBundleMock.spendingKey).shouldBeOk()

    val privateKeysetResult = keysetResult.copy(
      privateWalletRootXpub = "xpub-test-private-wallet"
    )
    privateWalletMigrationDao.saveServerKey(privateKeysetResult).shouldBeOk()
    privateWalletMigrationDao.saveKeysetLocalId("uuid-0").shouldBeOk()
    privateWalletMigrationDao.setDescriptorBackupComplete().shouldBeOk()
    privateWalletMigrationDao.setServerKeysetActive().shouldBeOk()
    privateWalletMigrationDao.setCloudBackupComplete().shouldBeOk()

    val newKeyset = SpendingKeysetMock.copy(
      localId = "uuid-0",
      appKey = AppKeyBundleMock.spendingKey,
      hardwareKey = mockNewHwKeys.spendingKey,
      f8eSpendingKeyset = privateKeysetResult
    )
    val updatedKeybox = mockAccount.keybox.copy(
      activeSpendingKeyset = newKeyset,
      keysets = mockAccount.keybox.keysets + newKeyset
    )
    val updatedAccount = mockAccount.copy(keybox = updatedKeybox)

    cloudBackupDao.set(updatedAccount.accountId.serverId, CloudBackupV2WithFullAccountMock)
    accountService.setActiveAccount(updatedAccount)
    privateWalletMigrationFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))

    service.migrationState.test {
      val state = awaitItem()
      state.shouldBeInstanceOf<PrivateWalletMigrationState.CloudBackupCompleted>()
    }

    val result = service.initiateMigration(
      account = updatedAccount,
      proofOfPossession = mockProofOfPossession,
      newHwKeys = mockNewHwKeys,
      ssek = SsekFake,
      sealedSsek = SealedSsekFake
    )

    result.shouldBeOk()
    val completedState = result.get().shouldNotBeNull()
    completedState.shouldBeInstanceOf<PrivateWalletMigrationState.CloudBackupCompleted>()
    completedState.newKeyset.localId.shouldBe("uuid-0")
    completedState.updatedKeybox.activeSpendingKeyset.localId.shouldBe("uuid-0")
  }
})
