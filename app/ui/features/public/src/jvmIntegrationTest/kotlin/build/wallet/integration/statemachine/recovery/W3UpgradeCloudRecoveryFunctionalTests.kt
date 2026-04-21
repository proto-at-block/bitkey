package build.wallet.integration.statemachine.recovery

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import bitkey.account.FullAccountConfig
import bitkey.account.HardwareType
import bitkey.relationships.Relationships
import bitkey.ui.screens.securityhub.SecurityHubBodyModel
import build.wallet.analytics.events.screen.id.PairHardwareEventTrackerScreenId.HW_ACTIVATION_INSTRUCTIONS_V2
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.bitkey.relationships.TrustedContactAuthenticationState
import build.wallet.cloud.backup.CloudBackupV2
import build.wallet.cloud.backup.CloudBackupV3
import build.wallet.cloud.store.CloudStoreAccount
import build.wallet.cloud.store.CloudStoreAccountFake.Companion.ProtectedCustomerFake
import build.wallet.integration.statemachine.create.restoreButton
import build.wallet.integration.statemachine.send.clickApprove
import build.wallet.money.BitcoinMoney.Companion.sats
import build.wallet.statemachine.account.ChooseAccountAccessModel
import build.wallet.statemachine.account.create.full.hardware.CompleteTwoTapBodyModel
import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareBodyModel
import build.wallet.statemachine.cloud.CloudSignInModelFake
import build.wallet.statemachine.cloud.SaveBackupInstructionsBodyModel
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.moneyhome.MoneyHomeBodyModel
import build.wallet.statemachine.nfc.PromptSelectionFormBodyModel
import build.wallet.statemachine.recovery.cloud.CloudBackupFoundModel
import build.wallet.statemachine.send.TransferConfirmationScreenModel
import build.wallet.statemachine.send.TransferInitiatedBodyModel
import build.wallet.statemachine.send.hardwareconfirmation.HardwareConfirmationScreenModel
import build.wallet.statemachine.settings.full.device.DeviceSettingsFormBodyModel
import build.wallet.statemachine.ui.awaitUntilBody
import build.wallet.statemachine.ui.awaitUntilScreenWithBody
import build.wallet.statemachine.ui.clickPrimaryButton
import build.wallet.statemachine.ui.robots.clickBitkeyDevice
import build.wallet.statemachine.ui.robots.clickMoreOptionsButton
import build.wallet.statemachine.walletmigration.W3UpgradeCompleteBodyModel
import build.wallet.statemachine.walletmigration.W3UpgradeDeviceReadyBodyModel
import build.wallet.statemachine.walletmigration.W3UpgradeIntroBodyModel
import build.wallet.statemachine.walletmigration.W3UpgradeNewHardwareAuthRotationInstructionsBodyModel
import build.wallet.statemachine.walletmigration.W3UpgradeOldHardwareAuthRotationInstructionsBodyModel
import build.wallet.statemachine.walletmigration.W3UpgradeOldHardwareInstructionsBodyModel
import build.wallet.testing.AppTester
import build.wallet.testing.AppTester.Companion.launchLegacyWalletApp
import build.wallet.testing.AppTester.Companion.launchNewApp
import build.wallet.testing.ext.addSomeFunds
import build.wallet.testing.ext.awaitRelationships
import build.wallet.testing.ext.awaitTcIsVerifiedAndBackedUp
import build.wallet.testing.ext.createTcInvite
import build.wallet.testing.ext.decryptCloudBackupKeys
import build.wallet.testing.ext.getActiveFullAccount
import build.wallet.testing.ext.getActiveHwAuthKey
import build.wallet.testing.ext.onboardFullAccountWithFakeHardware
import build.wallet.testing.ext.onboardLiteAccountFromInvitation
import build.wallet.testing.ext.readCloudBackup
import build.wallet.testing.ext.returnFundsToTreasury
import build.wallet.testing.ext.waitForFunds
import build.wallet.wallet.migration.MigrationType
import com.github.michaelbull.result.get
import com.github.michaelbull.result.getOrThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeTypeOf
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.seconds

class W3UpgradeCloudRecoveryFunctionalTests : FunSpec({
  test("W3 upgrade resumed from pre-upgrade cloud restore succeeds with trusted contacts and funds") {
    val fixture = prepareW3CloudRecoveryFixture()
    val customerApp = fixture.customerApp

    customerApp.appUiStateMachine.test(
      props = Unit,
      testTimeout = 240.seconds,
      turbineTimeout = 120.seconds
    ) {
      navigateToW3Upgrade()
      advanceThroughIntroPhase()
      advanceThroughPairingPhase()

      awaitUntilBody<W3UpgradeOldHardwareAuthRotationInstructionsBodyModel>()
        .onContinue()
      awaitUntilBody<W3UpgradeNewHardwareAuthRotationInstructionsBodyModel>()
        .onContinue()
      approveW3Confirmation()
      approveW3Confirmation()

      awaitUntilScreenWithBody<BodyModel>(
        matchingScreen = { screen ->
          screen.body is HardwareConfirmationScreenModel ||
            screen.bottomSheetModel?.body is PromptSelectionFormBodyModel
        }
      )

      customerApp.awaitW3UpgradeServerHandoff(
        oldHwAuthPublicKey = fixture.preUpgradeHwAuthPublicKey
      )
      customerApp.closeForUninstall()
      cancelAndIgnoreRemainingEvents()
    }

    customerApp.listCloudRecoveryServerKeysets(fixture.accountId)
      .descriptorBackups
      .map { it.keysetId }
      .toSet()
      .shouldBe(fixture.preUpgradeCloudBackup.keysetIds)

    customerApp.snapshotCloudRecoveryBackup(ProtectedCustomerFake)
      .shouldBe(fixture.preUpgradeCloudBackup)

    val cloudStoreAccountRepository = customerApp.cloudStoreAccountRepository
    val cloudBackupStore = customerApp.cloudBackupStore
    val w1HardwareSeed = customerApp.w1FakeHardwareKeyStore.getSeed()
    val w3HardwareSeed = customerApp.w3FakeHardwareKeyStore.getSeed()

    val recoveringApp = launchLegacyRecoveringApp(
      hardwareType = fixture.preUpgradeCloudBackup.hardwareType,
      cloudStoreAccountRepository = cloudStoreAccountRepository,
      cloudBackupStore = cloudBackupStore,
      w1HardwareSeed = w1HardwareSeed,
      w3HardwareSeed = w3HardwareSeed
    )

    recoveringApp.appUiStateMachine.test(
      props = Unit,
      testTimeout = 240.seconds,
      turbineTimeout = 120.seconds
    ) {
      val resumedIntro = restoreFromCloudToResumedW3Intro(recoveringApp).also {
        it.onBack.shouldBeNull()
      }

      recoveringApp.getActiveFullAccount()
        .run {
          config.hardwareType.shouldBe(HardwareType.W1)
          keybox.activeSpendingKeyset.f8eSpendingKeyset.keysetId.shouldBe(
            fixture.preUpgradeCloudBackup.activeKeysetId
          )
          keybox.keysets.map { it.f8eSpendingKeyset.keysetId }.toSet()
            .shouldBe(fixture.preUpgradeCloudBackup.keysetIds)
        }

      advanceThroughIntroPhase(resumedIntro)
      advanceThroughPairingPhase()
      advanceThroughResumedAuthAndKeyRotation()
      val oldHardwareInstructions = advanceThroughCloudBackup(ProtectedCustomerFake)
      completeSweepAndFinish(oldHardwareInstructions)

      cancelAndIgnoreRemainingEvents()
    }

    recoveringApp.verifyCloudRecoveryW3UpgradeState()
    recoveringApp.waitForFunds()

    val finalAccount = recoveringApp.getActiveFullAccount()
    val finalLocalKeysetIds = finalAccount.keybox.keysets.map { it.f8eSpendingKeyset.keysetId }.toSet()
    finalLocalKeysetIds.shouldContain(fixture.preUpgradeCloudBackup.activeKeysetId)
    finalAccount.keybox.activeSpendingKeyset.f8eSpendingKeyset.keysetId.shouldNotBe(
      fixture.preUpgradeCloudBackup.activeKeysetId
    )

    val finalDescriptorBackupIds =
      recoveringApp.listCloudRecoveryServerKeysets(fixture.accountId).descriptorBackups.map { it.keysetId }.toSet()
    finalDescriptorBackupIds.shouldBe(finalLocalKeysetIds)

    val finalCloudBackup = recoveringApp.snapshotCloudRecoveryBackup(ProtectedCustomerFake)
    finalCloudBackup.hardwareType.shouldBe(HardwareType.W3)
    finalCloudBackup.keysetIds.shouldBe(finalLocalKeysetIds)
    finalCloudBackup.activeKeysetId.shouldBe(
      finalAccount.keybox.activeSpendingKeyset.f8eSpendingKeyset.keysetId
    )

    recoveringApp.awaitCloudRecoveryVerifiedTrustedContact(fixture.tcRelationshipId)
    recoveringApp.returnFundsToTreasury()
  }

  test("W3 upgrade resumed from pre-upgrade cloud restore repairs missing historical descriptor backups") {
    val fixture = prepareW3CloudRecoveryFixture()
    val customerApp = fixture.customerApp

    customerApp.appUiStateMachine.test(
      props = Unit,
      testTimeout = 240.seconds,
      turbineTimeout = 120.seconds
    ) {
      navigateToW3Upgrade()
      advanceThroughIntroPhase()
      advanceThroughPairingPhase()
      advanceThroughAuthAndKeyRotation()

      awaitUntilBody<BodyModel>(
        matching = { it is CloudSignInModelFake || it is SaveBackupInstructionsBodyModel }
      )

      customerApp.closeForUninstall()
      cancelAndIgnoreRemainingEvents()
    }

    val abandonedAttemptState = customerApp.listCloudRecoveryServerKeysets(fixture.accountId)
    val abandonedDescriptorBackupIds = abandonedAttemptState.descriptorBackups.map { it.keysetId }.toSet()
    abandonedDescriptorBackupIds.shouldHaveSize(2)
    abandonedDescriptorBackupIds.shouldContain(fixture.preUpgradeCloudBackup.activeKeysetId)
    val abandonedKeysetId =
      (abandonedDescriptorBackupIds - fixture.preUpgradeCloudBackup.keysetIds).single()

    customerApp.snapshotCloudRecoveryBackup(ProtectedCustomerFake)
      .shouldBe(fixture.preUpgradeCloudBackup)

    val cloudStoreAccountRepository = customerApp.cloudStoreAccountRepository
    val cloudBackupStore = customerApp.cloudBackupStore
    val w1HardwareSeed = customerApp.w1FakeHardwareKeyStore.getSeed()
    val w3HardwareSeed = customerApp.w3FakeHardwareKeyStore.getSeed()

    val recoveringApp = launchLegacyRecoveringApp(
      hardwareType = fixture.preUpgradeCloudBackup.hardwareType,
      cloudStoreAccountRepository = cloudStoreAccountRepository,
      cloudBackupStore = cloudBackupStore,
      w1HardwareSeed = w1HardwareSeed,
      w3HardwareSeed = w3HardwareSeed
    )

    recoveringApp.appUiStateMachine.test(
      props = Unit,
      testTimeout = 240.seconds,
      turbineTimeout = 120.seconds
    ) {
      val resumedIntro = restoreFromCloudToResumedW3Intro(recoveringApp).also {
        it.onBack.shouldBeNull()
      }

      recoveringApp.getActiveFullAccount()
        .run {
          config.hardwareType.shouldBe(HardwareType.W1)
          keybox.activeSpendingKeyset.f8eSpendingKeyset.keysetId.shouldBe(
            fixture.preUpgradeCloudBackup.activeKeysetId
          )
          keybox.keysets.map { it.f8eSpendingKeyset.keysetId }.toSet()
            .shouldBe(fixture.preUpgradeCloudBackup.keysetIds)
        }

      advanceThroughIntroPhase(resumedIntro)
      advanceThroughPairingPhase()
      advanceThroughResumedAuthAndKeyRotation()
      val oldHardwareInstructions = advanceThroughCloudBackup(ProtectedCustomerFake)
      completeSweepAndFinish(oldHardwareInstructions)

      cancelAndIgnoreRemainingEvents()
    }

    recoveringApp.verifyCloudRecoveryW3UpgradeState()
    recoveringApp.waitForFunds()

    val finalAccount = recoveringApp.getActiveFullAccount()
    val finalLocalKeysetIds = finalAccount.keybox.keysets.map { it.f8eSpendingKeyset.keysetId }.toSet()
    val finalActiveKeysetId = finalAccount.keybox.activeSpendingKeyset.f8eSpendingKeyset.keysetId

    finalLocalKeysetIds.shouldHaveSize(3)
    finalLocalKeysetIds.shouldContain(fixture.preUpgradeCloudBackup.activeKeysetId)
    finalLocalKeysetIds.shouldContain(abandonedKeysetId)
    finalActiveKeysetId.shouldNotBe(fixture.preUpgradeCloudBackup.activeKeysetId)
    finalActiveKeysetId.shouldNotBe(abandonedKeysetId)

    val finalServerState = recoveringApp.listCloudRecoveryServerKeysets(fixture.accountId)
    finalServerState.activeKeysetId.shouldBe(finalActiveKeysetId)
    finalServerState.descriptorBackups.map { it.keysetId }.toSet().shouldBe(finalLocalKeysetIds)

    val finalCloudBackup = recoveringApp.snapshotCloudRecoveryBackup(ProtectedCustomerFake)
    finalCloudBackup.hardwareType.shouldBe(HardwareType.W3)
    finalCloudBackup.activeKeysetId.shouldBe(finalActiveKeysetId)
    finalCloudBackup.keysetIds.shouldBe(finalLocalKeysetIds)

    recoveringApp.awaitCloudRecoveryVerifiedTrustedContact(fixture.tcRelationshipId)
    recoveringApp.returnFundsToTreasury()
  }
})

private data class CloudRecoveryBackupSnapshot(
  val activeKeysetId: String,
  val keysetIds: Set<String>,
  val hardwareType: HardwareType,
)

private data class W3CloudRecoveryFixture(
  val customerApp: AppTester,
  val accountId: FullAccountId,
  val tcRelationshipId: String,
  val preUpgradeHwAuthPublicKey: HwAuthPublicKey,
  val preUpgradeCloudBackup: CloudRecoveryBackupSnapshot,
)

private suspend fun io.kotest.core.test.TestScope.prepareW3CloudRecoveryFixture(): W3CloudRecoveryFixture {
  val customerApp = launchLegacyWalletApp()
  customerApp.onboardFullAccountWithFakeHardware(
    cloudStoreAccountForBackup = ProtectedCustomerFake
  )

  val tcApp = launchNewApp()
  val invite = customerApp.createTcInvite("bob")
  tcApp.onboardLiteAccountFromInvitation(
    inviteCode = invite.inviteCode,
    protectedCustomerName = "alice"
  )
  customerApp.awaitTcIsVerifiedAndBackedUp(invite.invitation.relationshipId)
  tcApp.closeForUninstall()

  customerApp.addSomeFunds(sats(10_000L))

  return W3CloudRecoveryFixture(
    customerApp = customerApp,
    accountId = customerApp.getActiveFullAccount().accountId,
    tcRelationshipId = invite.invitation.relationshipId,
    preUpgradeHwAuthPublicKey = customerApp.getActiveHwAuthKey().publicKey,
    preUpgradeCloudBackup = customerApp.snapshotCloudRecoveryBackup(ProtectedCustomerFake)
  ).also {
    customerApp.cloudStoreAccountRepository.clear().getOrThrow()
  }
}

private suspend fun io.kotest.core.test.TestScope.launchLegacyRecoveringApp(
  hardwareType: HardwareType,
  cloudStoreAccountRepository: build.wallet.cloud.store.CloudStoreAccountRepository,
  cloudBackupStore: build.wallet.cloud.backup.CloudBackupStore,
  w1HardwareSeed: build.wallet.nfc.FakeHardwareKeyStore.Seed,
  w3HardwareSeed: build.wallet.nfc.FakeHardwareKeyStore.Seed,
): AppTester {
  val app = launchLegacyWalletApp(
    cloudStoreAccountRepository = cloudStoreAccountRepository,
    cloudBackupStore = cloudBackupStore,
    hardwareSeed = w1HardwareSeed,
    w3HardwareSeed = w3HardwareSeed
  )

  app.accountConfigService.setHardwareType(hardwareType).getOrThrow()

  return app
}

private suspend fun ReceiveTurbine<ScreenModel>.navigateToW3Upgrade() {
  awaitUntilBody<MoneyHomeBodyModel>()
    .onSecurityHubTabClick()
  awaitUntilBody<SecurityHubBodyModel>()
    .clickBitkeyDevice()
  awaitUntilBody<DeviceSettingsFormBodyModel>(
    matching = { it.onUpgradeDevice != null }
  ).onUpgradeDevice.shouldNotBeNull().invoke()
}

private suspend fun ReceiveTurbine<ScreenModel>.advanceThroughIntroPhase(
  intro: W3UpgradeIntroBodyModel? = null,
) {
  (intro ?: awaitUntilBody<W3UpgradeIntroBodyModel>())
    .clickPrimaryButton()
  awaitUntilBody<W3UpgradeDeviceReadyBodyModel>()
    .clickPrimaryButton()
}

private suspend fun ReceiveTurbine<ScreenModel>.advanceThroughPairingPhase() {
  awaitUntilBody<PairNewHardwareBodyModel>(HW_ACTIVATION_INSTRUCTIONS_V2)
    .clickPrimaryButton()
  awaitUntilBody<CompleteTwoTapBodyModel>()
    .clickPrimaryButton()
}

private suspend fun ReceiveTurbine<ScreenModel>.advanceThroughAuthAndKeyRotation() {
  awaitUntilBody<W3UpgradeOldHardwareAuthRotationInstructionsBodyModel>()
    .onContinue()
  awaitUntilBody<W3UpgradeNewHardwareAuthRotationInstructionsBodyModel>()
    .onContinue()
  approveW3Confirmation()
  approveW3Confirmation()
  approveW3Confirmation()
  approveW3Confirmation()
}

private suspend fun ReceiveTurbine<ScreenModel>.advanceThroughResumedAuthAndKeyRotation() {
  awaitUntilBody<W3UpgradeNewHardwareAuthRotationInstructionsBodyModel>()
    .onContinue()
  approveW3Confirmation()
  approveW3Confirmation()
  approveW3Confirmation()
  approveW3Confirmation()
}

private suspend fun ReceiveTurbine<ScreenModel>.advanceThroughCloudBackup(
  cloudStoreAccount: CloudStoreAccount,
): W3UpgradeOldHardwareInstructionsBodyModel? {
  awaitUntilBody<BodyModel>(
    matching = {
      it is CloudSignInModelFake ||
        it is SaveBackupInstructionsBodyModel ||
        it is W3UpgradeOldHardwareInstructionsBodyModel
    }
  ).let { body ->
    when (body) {
      is CloudSignInModelFake -> {
        body.signInSuccess(cloudStoreAccount)
        return null
      }
      is SaveBackupInstructionsBodyModel -> {
        body.onBackupClick()
        awaitUntilBody<CloudSignInModelFake>()
          .signInSuccess(cloudStoreAccount)
        return null
      }
      is W3UpgradeOldHardwareInstructionsBodyModel -> return body
    }
  }
  @Suppress("UnreachableCode")
  error("unreachable")
}

private suspend fun ReceiveTurbine<ScreenModel>.completeSweepAndFinish(
  oldHardwareInstructions: W3UpgradeOldHardwareInstructionsBodyModel? = null,
) {
  (oldHardwareInstructions ?: awaitUntilBody<W3UpgradeOldHardwareInstructionsBodyModel>())
    .onContinue()
  awaitUntilBody<TransferConfirmationScreenModel>()
    .clickPrimaryButton()
  awaitUntilBody<TransferInitiatedBodyModel>()
    .clickPrimaryButton()
  awaitUntilBody<W3UpgradeCompleteBodyModel>()
    .onDone()
  awaitUntilBody<MoneyHomeBodyModel>()
}

private suspend fun ReceiveTurbine<ScreenModel>.restoreFromCloudToResumedW3Intro(
  recoveringApp: AppTester,
): W3UpgradeIntroBodyModel {
  awaitUntilBody<ChooseAccountAccessModel>()
    .clickMoreOptionsButton()
  awaitUntilBody<FormBodyModel>()
    .restoreButton.onClick.shouldNotBeNull().invoke()
  awaitUntilBody<CloudSignInModelFake>()
    .signInSuccess(ProtectedCustomerFake)
  awaitUntilBody<CloudBackupFoundModel>()
    .onRestore()

  val intro: W3UpgradeIntroBodyModel = withTimeout(60.seconds) {
    while (true) {
      val screen = awaitItem()

      when {
        screen.bottomSheetModel?.body is PromptSelectionFormBodyModel -> {
          (checkNotNull(screen.bottomSheetModel).body as PromptSelectionFormBodyModel).clickApprove()
        }
        screen.body is HardwareConfirmationScreenModel -> {
          (screen.body as HardwareConfirmationScreenModel).onConfirm()
        }
        screen.body is W3UpgradeIntroBodyModel -> {
          return@withTimeout screen.body as W3UpgradeIntroBodyModel
        }
      }
    }
    @Suppress("UnreachableCode")
    error("unreachable")
  }

  recoveringApp.awaitCloudRestoreCheckpoint()
  return intro
}

private suspend fun ReceiveTurbine<ScreenModel>.approveW3Confirmation() {
  awaitUntilScreenWithBody<BodyModel>(
    matchingScreen = { screen ->
      screen.body is HardwareConfirmationScreenModel ||
        screen.bottomSheetModel?.body is PromptSelectionFormBodyModel
    }
  ).let { screen ->
    when {
      screen.body is HardwareConfirmationScreenModel -> {
        (screen.body as HardwareConfirmationScreenModel).onConfirm()
      }
      screen.bottomSheetModel?.body is PromptSelectionFormBodyModel -> {
        (checkNotNull(screen.bottomSheetModel).body as PromptSelectionFormBodyModel).clickApprove()
      }
    }
  }
}

private suspend fun AppTester.verifyCloudRecoveryW3UpgradeState() {
  accountService.activeAccount().test {
    val account = awaitItem()
    account.shouldBeTypeOf<FullAccount>()
    account.keybox.canUseKeyboxKeysets.shouldBeTrue()
    account.config.shouldBeTypeOf<FullAccountConfig>()
      .hardwareType.shouldBe(HardwareType.W3)
  }

  val deviceInfo = firmwareDeviceInfoDao.getDeviceInfo().getOrThrow()
  deviceInfo.shouldNotBeNull()
  deviceInfo.hardwareType().shouldBe(HardwareType.W3)
}

private suspend fun AppTester.snapshotCloudRecoveryBackup(
  cloudStoreAccount: CloudStoreAccount,
): CloudRecoveryBackupSnapshot {
  val decryptedKeys = decryptCloudBackupKeys(cloudStoreAccount)
  val keysetIds =
    decryptedKeys.keysets.ifEmpty { listOf(decryptedKeys.activeSpendingKeyset) }
      .map { it.f8eSpendingKeyset.keysetId }
      .toSet()
  val hardwareType = when (val backup = readCloudBackup(cloudStoreAccount).shouldNotBeNull()) {
    is CloudBackupV2 -> backup.fullAccountFields.shouldNotBeNull().hardwareType
    is CloudBackupV3 -> backup.fullAccountFields.shouldNotBeNull().hardwareType
    else -> error("Unsupported cloud backup type: ${backup::class.simpleName}")
  }
  return CloudRecoveryBackupSnapshot(
    activeKeysetId = decryptedKeys.activeSpendingKeyset.f8eSpendingKeyset.keysetId,
    keysetIds = keysetIds,
    hardwareType = hardwareType
  )
}

private suspend fun AppTester.listCloudRecoveryServerKeysets(
  accountId: FullAccountId,
) = listKeysetsF8eClient
  .listKeysets(
    f8eEnvironment = initialF8eEnvironment,
    fullAccountId = accountId
  ).getOrThrow()

private suspend fun AppTester.awaitCloudRecoveryVerifiedTrustedContact(
  relationshipId: String,
): Relationships {
  val relationships = awaitRelationships(timeout = 60.seconds) {
    it.endorsedTrustedContacts.any { tc ->
      tc.relationshipId == relationshipId &&
        tc.authenticationState == TrustedContactAuthenticationState.VERIFIED
    }
  }
  relationships.endorsedTrustedContacts.single { it.relationshipId == relationshipId }
    .authenticationState.shouldBe(TrustedContactAuthenticationState.VERIFIED)
  return relationships
}

private suspend fun AppTester.awaitW3UpgradeServerHandoff(
  oldHwAuthPublicKey: HwAuthPublicKey,
) {
  withTimeout(30.seconds) {
    while (
      !migrationService.isW3UpgradeInProgress(
        f8eEnvironment = initialF8eEnvironment,
        hwAuthPublicKey = oldHwAuthPublicKey
      )
    ) {
      delay(250)
    }
  }

  migrationService.resume(MigrationType.W3Upgrade).get().shouldNotBeNull().isInProgress().shouldBeTrue()
}

private suspend fun AppTester.awaitCloudRestoreCheckpoint() {
  data class Snapshot(
    val accountHardwareType: HardwareType?,
    val migrationProgress: String?,
    val migrationInProgress: Boolean?,
    val resumedFromCloudBackup: Boolean?,
  )

  val snapshot: Snapshot = withTimeout(30.seconds) {
    while (true) {
      val activeAccount = try {
        getActiveFullAccount()
      } catch (e: CancellationException) {
        throw e
      } catch (_: Throwable) {
        null
      }
      val migrationProgress = try {
        migrationService.resume(MigrationType.W3Upgrade).get()
      } catch (e: CancellationException) {
        throw e
      } catch (_: Throwable) {
        null
      }
      val snapshot = Snapshot(
        accountHardwareType = activeAccount?.config?.hardwareType,
        migrationProgress = migrationProgress?.let { it::class.simpleName },
        migrationInProgress = migrationProgress?.isInProgress(),
        resumedFromCloudBackup = migrationProgress?.wasResumedFromCloudBackupForTest()
      )
      if (activeAccount != null || migrationProgress != null) {
        return@withTimeout snapshot
      }
      delay(250)
    }
    @Suppress("UnreachableCode")
    error("unreachable")
  }

  check(snapshot.accountHardwareType == HardwareType.W1) {
    "Cloud restore did not reactivate the pre-upgrade W1 account. Snapshot=$snapshot"
  }
  check(snapshot.migrationInProgress == true && snapshot.resumedFromCloudBackup == true) {
    "Cloud restore did not persist resumed W3 upgrade state. Snapshot=$snapshot"
  }
}

private fun build.wallet.wallet.migration.MigrationProgress.wasResumedFromCloudBackupForTest(): Boolean =
  when (this) {
    is build.wallet.wallet.migration.MigrationProgress.NotStarted -> resumedFromCloudBackup
    is build.wallet.wallet.migration.MigrationProgress.CreateNewKeyset -> resumedFromCloudBackup
    is build.wallet.wallet.migration.MigrationProgress.AuthKeyRotation -> resumedFromCloudBackup
    is build.wallet.wallet.migration.MigrationProgress.DescriptorBackup -> resumedFromCloudBackup
    else -> false
  }
