package build.wallet.integration.statemachine.cloud.health

import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId.CLOUD_SIGN_IN_LOADING
import build.wallet.analytics.events.screen.id.MoneyHomeEventTrackerScreenId.MONEY_HOME
import build.wallet.analytics.events.screen.id.NfcEventTrackerScreenId.NFC_DETECTED
import build.wallet.analytics.events.screen.id.NfcEventTrackerScreenId.NFC_INITIATE
import build.wallet.analytics.events.screen.id.NfcEventTrackerScreenId.NFC_SUCCESS
import build.wallet.analytics.events.screen.id.SettingsEventTrackerScreenId.SETTINGS
import build.wallet.cloud.store.CloudFileStoreFake
import build.wallet.cloud.store.CloudFileStoreResult
import build.wallet.cloud.store.CloudStoreAccountFake.Companion.CloudStoreAccount1Fake
import build.wallet.feature.FeatureFlagValue.BooleanFlag
import build.wallet.statemachine.cloud.CloudSignInModelFake
import build.wallet.statemachine.cloud.health.CloudBackupHealthDashboardBodyModel
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachineTester
import build.wallet.statemachine.core.test
import build.wallet.statemachine.moneyhome.MoneyHomeBodyModel
import build.wallet.statemachine.nfc.NfcBodyModel
import build.wallet.statemachine.settings.SettingsBodyModel
import build.wallet.statemachine.ui.awaitUntilScreenWithBody
import build.wallet.statemachine.ui.matchers.shouldHaveTitle
import build.wallet.statemachine.ui.robots.clickTrailingButton
import build.wallet.testing.AppTester
import build.wallet.testing.launchNewApp
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory
import io.kotest.core.NamedTag
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.spec.style.scopes.RootTestWithConfigBuilder
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestScope
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf

private const val DISABLE_CLOUD_BACKUP_HEALTH = "disable-cloud-backup-health"
private const val CLOUD_ACCESS_FAILURE = "cloud-access-failure"

class CloudBackupHealthFunctionalTests : FunSpec({

  lateinit var appTester: AppTester
  beforeTest { testCase ->
    appTester = launchNewApp()
    appTester.app.appComponent.cloudBackupHealthFeatureFlag.setFlagValue(
      BooleanFlag(
        !testCase.hasNamedTag(DISABLE_CLOUD_BACKUP_HEALTH)
      )
    )
    appTester
      .onboardFullAccountWithFakeHardware(
        cloudStoreAccountForBackup = CloudStoreAccount1Fake.takeIf {
          !testCase.hasNamedTag(CLOUD_ACCESS_FAILURE)
        }
      )
  }

  test("Cloud backup health dashboard is hidden")
    .withTags(DISABLE_CLOUD_BACKUP_HEALTH) {
      appTester.app.appUiStateMachine.test(
        props = Unit,
        useVirtualTime = false
      ) {
        awaitUntilScreenWithBody<MoneyHomeBodyModel>(MONEY_HOME) {
          shouldClickSettings()
        }
        awaitUntilScreenWithBody<SettingsBodyModel>(SETTINGS) {
          cloudBackupHealthRow.shouldBeNull()
        }
        cancelAndIgnoreRemainingEvents()
      }
    }

  test("Cloud backup health dashboard is visible") {
    appTester.app.appUiStateMachine.test(
      props = Unit,
      useVirtualTime = false
    ) {
      shouldNavigateToCloudBackupHealthDashboard()
      cancelAndIgnoreRemainingEvents()
    }
  }

  test("Cloud backup health dashboard with healthy cloud backup") {
    appTester.app.appUiStateMachine.test(
      props = Unit,
      useVirtualTime = false
    ) {
      shouldNavigateToCloudBackupHealthDashboard {
        mobileKeyBackupStatusCard.backupStatus.title.shouldBe("Fake Cloud Store backup")
        mobileKeyBackupStatusCard.backupStatusActionButton.shouldBeNull()
      }
      cancelAndIgnoreRemainingEvents()
    }
  }

  test("Cloud backup health dashboard with cloud access failure")
    .withTags(CLOUD_ACCESS_FAILURE) {
      appTester.app.appUiStateMachine.test(
        props = Unit,
        useVirtualTime = false
      ) {
        shouldNavigateToCloudBackupHealthDashboard {
          mobileKeyBackupStatusCard.backupStatus.title
            .shouldBe("Problem with Fake Cloud Store\naccount access")
          mobileKeyBackupStatusCard.backupStatus.onClick.shouldBeNull()
          mobileKeyBackupStatusCard.backupStatusActionButton.shouldNotBeNull().onClick()
        }
        awaitUntilScreenWithBody<LoadingSuccessBodyModel>(CLOUD_SIGN_IN_LOADING)
        awaitUntilScreenWithBody<CloudSignInModelFake>()
        cancelAndIgnoreRemainingEvents()
      }
    }

  test("Cloud backup health dashboard repair mobile key backup") {
    appTester.app.appUiStateMachine.test(
      props = Unit,
      useVirtualTime = false
    ) {
      appTester.app.cloudBackupRepository.clear(CloudStoreAccount1Fake, false)
      shouldNavigateToCloudBackupHealthDashboard {
        mobileKeyBackupStatusCard.backupStatus.title
          .shouldBe("Problem with Mobile Key\nBackup")
        mobileKeyBackupStatusCard.backupStatus.onClick.shouldBeNull()
        mobileKeyBackupStatusCard.backupStatusActionButton.shouldNotBeNull().onClick()
      }
      awaitUntilScreenWithBody<LoadingSuccessBodyModel>(CLOUD_SIGN_IN_LOADING)
      awaitUntilScreenWithBody<NfcBodyModel>(NFC_INITIATE)
      awaitUntilScreenWithBody<NfcBodyModel>(NFC_DETECTED)
      awaitUntilScreenWithBody<NfcBodyModel>(NFC_SUCCESS)

      awaitUntilScreenWithBody<CloudBackupHealthDashboardBodyModel>()
      cancelAndIgnoreRemainingEvents()
    }
  }

  test("Cloud backup health automatically repairs eak backup") {
    appTester.app.appUiStateMachine.test(
      props = Unit,
      useVirtualTime = false
    ) {
      val cloudFileStoreFake = appTester.app.cloudFileStore as CloudFileStoreFake
      cloudFileStoreFake.clear()
      shouldNavigateToCloudBackupHealthDashboard {
        eakBackupStatusCard.shouldNotBeNull().backupStatus.title
          .shouldBe("Fake Cloud Store backup")
        eakBackupStatusCard.shouldNotBeNull().backupStatusActionButton.shouldBeNull()
      }
      cloudFileStoreFake.exists(
        account = CloudStoreAccount1Fake,
        fileName = "Emergency Access Kit.pdf"
      ).shouldBe(CloudFileStoreResult.Ok(true))
      cancelAndIgnoreRemainingEvents()
    }
  }

  test("Cloud backup health card appears on money home screen")
    .withTags(CLOUD_ACCESS_FAILURE) {
      appTester.app.appUiStateMachine.test(
        props = Unit,
        useVirtualTime = false
      ) {
        awaitUntilScreenWithBody<MoneyHomeBodyModel>(
          id = MONEY_HOME,
          expectedBodyContentMatch = {
            it.cardsModel.cards.isNotEmpty()
          }
        ) {
          cardsModel.cards
            .first()
            .shouldHaveTitle("Problem with Fake Cloud Store\naccount access")
            .clickTrailingButton()
          awaitUntilScreenWithBody<LoadingSuccessBodyModel>(CLOUD_SIGN_IN_LOADING)
          awaitUntilScreenWithBody<CloudSignInModelFake>()
        }
        cancelAndIgnoreRemainingEvents()
      }
    }

  test("Cloud backup health card does not appear on money home screen") {
    appTester.app.appUiStateMachine.test(
      props = Unit,
      useVirtualTime = false
    ) {
      awaitUntilScreenWithBody<MoneyHomeBodyModel>(MONEY_HOME) {
        cardsModel.cards.shouldBeEmpty()
      }
      cancelAndIgnoreRemainingEvents()
    }
  }
})

private fun MoneyHomeBodyModel.shouldClickSettings() {
  trailingToolbarAccessoryModel
    .shouldNotBeNull()
    .shouldBeTypeOf<IconAccessory>()
    .model
    .onClick()
}

private val SettingsBodyModel.cloudBackupHealthRow
  get() = sectionModels
    .flatMap { it.rowModels }
    .firstOrNull { it.title == "Cloud Backup" }

private fun namedTags(vararg tagNames: String) = tagNames.map(::NamedTag).toSet()

private fun TestCase.hasNamedTag(tagName: String) = config.tags.contains(NamedTag(tagName))

private fun RootTestWithConfigBuilder.withTags(
  vararg tagNames: String,
  test: suspend TestScope.() -> Unit,
) = config(tags = namedTags(*tagNames), test = test)

private suspend inline fun StateMachineTester<Unit, ScreenModel>.shouldNavigateToCloudBackupHealthDashboard(
  block: CloudBackupHealthDashboardBodyModel.() -> Unit = {},
): CloudBackupHealthDashboardBodyModel {
  awaitUntilScreenWithBody<MoneyHomeBodyModel>(MONEY_HOME) {
    shouldClickSettings()
  }
  awaitUntilScreenWithBody<SettingsBodyModel>(SETTINGS) {
    cloudBackupHealthRow.shouldNotBeNull().onClick()
  }
  return awaitUntilScreenWithBody<CloudBackupHealthDashboardBodyModel>(block = block)
}
