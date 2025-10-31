package build.wallet.bitcoin.address

import bitkey.notifications.NotificationChannel
import bitkey.notifications.NotificationPreferences
import bitkey.notifications.NotificationsPreferencesCachedProviderMock
import build.wallet.account.AccountServiceFake
import build.wallet.bdk.bindings.BdkError.Generic
import build.wallet.bitcoin.transactions.BitcoinWalletServiceFake
import build.wallet.bitcoin.wallet.SpendingWalletMock
import build.wallet.bitkey.f8e.F8eSpendingKeysetMock
import build.wallet.bitkey.f8e.FullAccountIdMock
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.keybox.KeyboxMock
import build.wallet.bitkey.keybox.PrivateWalletKeyboxMock
import build.wallet.coroutines.createBackgroundScope
import build.wallet.coroutines.turbine.turbines
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.feature.FeatureFlagValue.BooleanFlag
import build.wallet.feature.flags.DescriptorBackupFailsafeFeatureFlag
import build.wallet.notifications.RegisterWatchAddressContext
import build.wallet.notifications.RegisterWatchAddressProcessor
import build.wallet.queueprocessor.Processor
import build.wallet.queueprocessor.ProcessorMock
import build.wallet.recovery.DescriptorBackupServiceFake
import build.wallet.testing.shouldBeErrOfType
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.launch

class BitcoinAddressServiceImplTests : FunSpec({

  val spendingWallet = SpendingWalletMock(turbines::create)
  val processorMock = ProcessorMock<RegisterWatchAddressContext>(turbines::create)
  val registerWatchAddressProcessor = object :
    RegisterWatchAddressProcessor,
    Processor<RegisterWatchAddressContext> by processorMock {}
  val transactionService = BitcoinWalletServiceFake()
  val accountService = AccountServiceFake()
  val descriptorBackupService = DescriptorBackupServiceFake()
  val featureFlagDao = FeatureFlagDaoFake()
  val descriptorBackupFailsafeFeatureFlag = DescriptorBackupFailsafeFeatureFlag(featureFlagDao)

  fun createService(
    notificationPreferences: NotificationPreferences = NotificationPreferences(
      moneyMovement = emptySet(),
      productMarketing = emptySet()
    ),
  ) = BitcoinAddressServiceImpl(
    registerWatchAddressProcessor = registerWatchAddressProcessor,
    bitcoinWalletService = transactionService,
    accountService = accountService,
    notificationsPreferencesCachedProvider = NotificationsPreferencesCachedProviderMock(
      getNotificationPreferencesResult = Ok(notificationPreferences)
    ),
    descriptorBackupService = descriptorBackupService
  )

  beforeTest {
    spendingWallet.reset()
    processorMock.reset()
    transactionService.reset()
    transactionService.spendingWallet.value = spendingWallet
    accountService.reset()
    descriptorBackupService.reset()
    featureFlagDao.reset()
  }

  test("generate new address successfully - money movement notifications enabled") {
    accountService.setActiveAccount(FullAccountMock)

    val service = createService(
      notificationPreferences = NotificationPreferences(
        moneyMovement = setOf(NotificationChannel.Push),
        productMarketing = emptySet()
      )
    )

    createBackgroundScope().launch {
      service.executeWork()
    }

    spendingWallet.newAddressResult = Ok(someBitcoinAddress)
    processorMock.processBatchReturnValues = listOf(Ok(Unit))

    val addressResult = service.generateAddress()
    addressResult.shouldBe(Ok(someBitcoinAddress))

    processorMock.processBatchCalls.awaitItem().shouldBe(
      listOf(
        RegisterWatchAddressContext(
          someBitcoinAddress,
          F8eSpendingKeysetMock,
          FullAccountIdMock.serverId,
          KeyboxMock.config.f8eEnvironment
        )
      )
    )
  }

  test("generate new address successfully - money movement notifications disabled") {
    accountService.setActiveAccount(FullAccountMock)

    val service = createService(
      notificationPreferences = NotificationPreferences(
        moneyMovement = emptySet(),
        productMarketing = emptySet()
      )
    )

    createBackgroundScope().launch {
      service.executeWork()
    }

    spendingWallet.newAddressResult = Ok(someBitcoinAddress)
    processorMock.processBatchReturnValues = listOf(Ok(Unit))

    val addressResult = service.generateAddress()
    addressResult.shouldBe(Ok(someBitcoinAddress))

    // Watch address should not be registered
    processorMock.processBatchCalls.expectNoEvents()
  }

  test("generate new address - address generation failure") {
    accountService.setActiveAccount(FullAccountMock)

    val service = createService()

    val error = Err(Generic(Exception("failed to generate address"), null))
    spendingWallet.newAddressResult = error

    val addressResult = service.generateAddress()
    addressResult.shouldBe(error)

    processorMock.processBatchCalls.expectNoEvents()
  }

  test("checks descriptor backup prior to address generation") {
    accountService.setActiveAccount(FullAccountMock.copy(keybox = PrivateWalletKeyboxMock))
    descriptorBackupService.checkBackupForPrivateKeysetResult = Err(IllegalStateException("No descriptor backup exists"))
    descriptorBackupFailsafeFeatureFlag.setFlagValue(BooleanFlag(true))

    val service = createService()
    spendingWallet.newAddressResult = Ok(someBitcoinAddress)

    service.generateAddress().shouldBeErrOfType<IllegalStateException>()
  }
})
