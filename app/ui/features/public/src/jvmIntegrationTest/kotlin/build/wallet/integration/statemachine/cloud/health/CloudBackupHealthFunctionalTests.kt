package build.wallet.integration.statemachine.cloud.health

import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId.*
import build.wallet.analytics.events.screen.id.MoneyHomeEventTrackerScreenId.MONEY_HOME
import build.wallet.analytics.events.screen.id.NfcEventTrackerScreenId.NFC_DETECTED
import build.wallet.analytics.events.screen.id.NfcEventTrackerScreenId.NFC_INITIATE
import build.wallet.analytics.events.screen.id.SettingsEventTrackerScreenId.SETTINGS
import build.wallet.cloud.store.CloudFileStoreFake
import build.wallet.cloud.store.CloudFileStoreResult
import build.wallet.cloud.store.CloudStoreAccountFake.Companion.CloudStoreAccount1Fake
import build.wallet.platform.data.MimeType
import build.wallet.statemachine.cloud.CloudSignInModelFake
import build.wallet.statemachine.cloud.health.CloudBackupHealthDashboardBodyModel
import build.wallet.statemachine.core.*
import build.wallet.statemachine.moneyhome.MoneyHomeBodyModel
import build.wallet.statemachine.nfc.NfcBodyModel
import build.wallet.statemachine.settings.SettingsBodyModel
import build.wallet.statemachine.ui.awaitUntilBody
import build.wallet.statemachine.ui.matchers.shouldHaveCard
import build.wallet.statemachine.ui.robots.clickSettings
import build.wallet.statemachine.ui.robots.clickTrailingButton
import build.wallet.testing.AppTester
import build.wallet.testing.AppTester.Companion.launchNewApp
import build.wallet.testing.ext.onboardFullAccountWithFakeHardware
import build.wallet.ui.model.icon.IconImage
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconTint
import io.kotest.core.NamedTag
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.spec.style.scopes.RootTestWithConfigBuilder
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestScope
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import okio.ByteString.Companion.toByteString

private const val CLOUD_ACCESS_FAILURE = "cloud-access-failure"

class CloudBackupHealthFunctionalTests : FunSpec({

  suspend fun TestScope.launchAppAndOnboard(): AppTester {
    return launchNewApp().apply {
      onboardFullAccountWithFakeHardware(
        cloudStoreAccountForBackup = CloudStoreAccount1Fake.takeIf {
          !testCase.hasNamedTag(CLOUD_ACCESS_FAILURE)
        }
      )
    }
  }

  test("Cloud backup health dashboard is visible with warning icon")
    .withTags(CLOUD_ACCESS_FAILURE) {
      val app = launchAppAndOnboard()
      app.appUiStateMachine.test(props = Unit) {
        awaitUntilBody<MoneyHomeBodyModel>(MONEY_HOME) {
          clickSettings()
        }
        awaitUntilBody<SettingsBodyModel>(SETTINGS) {
          cloudBackupHealthRow.shouldNotBeNull()
            .specialTrailingIconModel.shouldNotBeNull().run {
              iconImage.shouldBe(IconImage.LocalImage(Icon.SmallIconInformationFilled))
              iconTint.shouldBe(IconTint.Warning)
              iconSize.shouldBe(IconSize.Small)
            }
        }
        cancelAndIgnoreRemainingEvents()
      }
    }

  test("Cloud backup health dashboard is visible") {
    val app = launchAppAndOnboard()
    app.appUiStateMachine.test(props = Unit) {
      shouldNavigateToCloudBackupHealthDashboard()
      cancelAndIgnoreRemainingEvents()
    }
  }

  test("Cloud backup health dashboard with healthy cloud backup") {
    val app = launchAppAndOnboard()
    app.appUiStateMachine.test(props = Unit) {
      shouldNavigateToCloudBackupHealthDashboard {
        appKeyBackupStatusCard.backupStatus.title.shouldBe("Fake Cloud Store backup")
        appKeyBackupStatusCard.backupStatusActionButton.shouldBeNull()
      }
      cancelAndIgnoreRemainingEvents()
    }
  }

  test("Cloud backup health dashboard with cloud access failure")
    .withTags(CLOUD_ACCESS_FAILURE) {
      val app = launchAppAndOnboard()
      app.appUiStateMachine.test(props = Unit) {
        shouldNavigateToCloudBackupHealthDashboard {
          appKeyBackupStatusCard.backupStatus.title
            .shouldBe("Problem with Fake Cloud Store\naccount access")
          appKeyBackupStatusCard.backupStatus.onClick.shouldBeNull()
          appKeyBackupStatusCard.backupStatusActionButton.shouldNotBeNull().onClick()
        }
        awaitUntilBody<LoadingSuccessBodyModel>(CLOUD_SIGN_IN_LOADING)
        awaitUntilBody<CloudSignInModelFake>()
        cancelAndIgnoreRemainingEvents()
      }
    }

  test("Cloud backup health dashboard repair App Key backup") {
    val app = launchAppAndOnboard()
    app.appUiStateMachine.test(props = Unit) {
      app.cloudBackupRepository.clear(CloudStoreAccount1Fake, false)
      shouldNavigateToCloudBackupHealthDashboard {
        appKeyBackupStatusCard.backupStatus.title
          .shouldBe("Problem with App Key\nBackup")
        appKeyBackupStatusCard.backupStatus.onClick.shouldBeNull()
        appKeyBackupStatusCard.backupStatusActionButton.shouldNotBeNull().onClick()
      }
      awaitUntilBody<LoadingSuccessBodyModel>(CLOUD_SIGN_IN_LOADING)
      awaitUntilBody<NfcBodyModel>(NFC_INITIATE)
      awaitUntilBody<NfcBodyModel>(NFC_DETECTED)
      awaitUntilBody<LoadingSuccessBodyModel>(CREATING_CLOUD_BACKUP)
      awaitUntilBody<LoadingSuccessBodyModel>(PREPARING_CLOUD_BACKUP)
      awaitUntilBody<LoadingSuccessBodyModel>(UPLOADING_CLOUD_BACKUP)

      awaitUntilBody<CloudBackupHealthDashboardBodyModel>(
        matching = {
          it.appKeyBackupStatusCard.backupStatus.secondaryText == "Successfully backed up"
        }
      )
      cancelAndIgnoreRemainingEvents()
    }
  }

  test("Cloud backup health automatically repairs EEK backup") {
    val app = launchAppAndOnboard()
    app.appUiStateMachine.test(props = Unit) {
      val cloudFileStoreFake = app.cloudFileStore as CloudFileStoreFake
      cloudFileStoreFake.clear()
      shouldNavigateToCloudBackupHealthDashboard {
        eekBackupStatusCard.shouldNotBeNull().backupStatus.title
          .shouldBe("Fake Cloud Store backup")
        eekBackupStatusCard.shouldNotBeNull().backupStatusActionButton.shouldBeNull()
      }
      cloudFileStoreFake.exists(
        account = CloudStoreAccount1Fake,
        fileName = "Emergency Exit Kit.pdf"
      ).shouldBe(CloudFileStoreResult.Ok(true))
      cancelAndIgnoreRemainingEvents()
    }
  }

  test("Cloud backup health automatically repairs EAK -> EEK") {
    val app = launchAppAndOnboard()
    app.appUiStateMachine.test(props = Unit) {
      val cloudFileStoreFake = app.cloudFileStore as CloudFileStoreFake
      cloudFileStoreFake.clear()
      cloudFileStoreFake.write(
        CloudStoreAccount1Fake,
        bytes = "dummy pdf data".encodeToByteArray().toByteString(),
        fileName = "Emergency Access Kit.pdf",
        mimeType = MimeType.PDF
      )
      shouldNavigateToCloudBackupHealthDashboard {
        eekBackupStatusCard.shouldNotBeNull().backupStatus.title
          .shouldBe("Fake Cloud Store backup")
        eekBackupStatusCard.shouldNotBeNull().backupStatusActionButton.shouldBeNull()
      }
      cloudFileStoreFake.exists(
        account = CloudStoreAccount1Fake,
        fileName = "Emergency Exit Kit.pdf"
      ).shouldBe(CloudFileStoreResult.Ok(true))
      cloudFileStoreFake.exists(
        account = CloudStoreAccount1Fake,
        fileName = "Emergency Access Kit.pdf"
      ).shouldBe(CloudFileStoreResult.Ok(false))
      cancelAndIgnoreRemainingEvents()
    }
  }

  test("Cloud backup health card appears on money home screen")
    .withTags(CLOUD_ACCESS_FAILURE) {
      val app = launchAppAndOnboard()
      app.appUiStateMachine.test(props = Unit) {
        awaitUntilBody<MoneyHomeBodyModel>(
          id = MONEY_HOME,
          matching = { body ->
            body.cardsModel.cards.any {
              it.title?.string?.contains("Problem with Fake Cloud Store") == true
            }
          }
        ) {
          cardsModel
            .shouldHaveCard("Problem with Fake Cloud Store\naccount access")
            .clickTrailingButton()
          awaitUntilBody<LoadingSuccessBodyModel>(CLOUD_SIGN_IN_LOADING)
          awaitUntilBody<CloudSignInModelFake>()
        }
        cancelAndIgnoreRemainingEvents()
      }
    }

  test("Cloud backup health card does not appear on money home screen") {
    val app = launchAppAndOnboard()
    app.appUiStateMachine.test(props = Unit) {
      awaitUntilBody<MoneyHomeBodyModel>(
        MONEY_HOME,
        matching = { body ->
          body.cardsModel.cards.none {
            it.title?.string?.contains("Problem with Fake Cloud Store") == true
          }
        }
      )
      cancelAndIgnoreRemainingEvents()
    }
  }
})

private val SettingsBodyModel.cloudBackupHealthRow
  get() =
    sectionModels
      .flatMap { it.rowModels }
      .firstOrNull { it.title == "Cloud Backup" }

private fun namedTags(vararg tagNames: String) = tagNames.map(::NamedTag).toSet()

private fun TestCase.hasNamedTag(tagName: String) = config.tags.contains(NamedTag(tagName))

private fun RootTestWithConfigBuilder.withTags(
  vararg tagNames: String,
  test: suspend TestScope.() -> Unit,
) = config(tags = namedTags(*tagNames), test = test)

private suspend inline fun StateMachineTester<Unit, ScreenModel>.shouldNavigateToCloudBackupHealthDashboard(
  validate: CloudBackupHealthDashboardBodyModel.() -> Unit = {},
): CloudBackupHealthDashboardBodyModel {
  awaitUntilBody<MoneyHomeBodyModel>(MONEY_HOME) {
    clickSettings()
  }
  awaitUntilBody<SettingsBodyModel>(SETTINGS) {
    cloudBackupHealthRow.shouldNotBeNull().onClick()
  }
  return awaitUntilBody<CloudBackupHealthDashboardBodyModel>(validate = validate)
}
