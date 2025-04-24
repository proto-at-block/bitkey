package build.wallet.integration.statemachine.inheritance

import build.wallet.analytics.events.screen.context.AuthKeyRotationEventTrackerScreenIdContext
import build.wallet.analytics.events.screen.id.InactiveAppEventTrackerScreenId
import build.wallet.integration.statemachine.inheritance.robots.advanceThroughClaimStart
import build.wallet.integration.statemachine.inheritance.robots.launchInheritanceApps
import build.wallet.integration.statemachine.inheritance.robots.skipDelayPeriod
import build.wallet.integration.statemachine.recovery.cloud.screenDecideIfShouldRotate
import build.wallet.money.BitcoinMoney
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.inheritance.*
import build.wallet.statemachine.inheritance.claims.complete.EmptyBenefactorWalletScreenModel
import build.wallet.statemachine.inheritance.claims.complete.InheritanceTransferSuccessScreenModel
import build.wallet.statemachine.moneyhome.MoneyHomeBodyModel
import build.wallet.statemachine.settings.SettingsBodyModel
import build.wallet.statemachine.ui.awaitUntilBody
import build.wallet.statemachine.ui.awaitUntilSheet
import build.wallet.statemachine.ui.robots.advanceUntilScreenWithBody
import build.wallet.testing.ext.addSomeFunds
import build.wallet.testing.ext.getActiveWallet
import build.wallet.testing.ext.returnFundsToTreasury
import build.wallet.testing.shouldBeOk
import build.wallet.ui.model.list.ListItemAccessory
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeTypeOf
import kotlinx.coroutines.flow.first
import kotlin.time.Duration.Companion.seconds

class InheritanceClaimE2eTests : FunSpec({
  test("Start Inheritance Claim") {
    val apps = launchInheritanceApps()
    apps.advanceThroughClaimStart()

    // Assert that Claim enters pending state:
    apps.beneficiary.app.inheritanceManagementUiStateMachine.test(
      turbineTimeout = 60.seconds,
      props = InheritanceManagementUiProps(
        account = apps.beneficiary.account(),
        selectedTab = ManagingInheritanceTab.Beneficiaries,
        onBack = { error("No exit calls expected") },
        onGoToUtxoConsolidation = { error("No UTXO consolidation expected") }
      )
    ) {
      awaitUntilBody<ManagingInheritanceBodyModel>(
        matching = { model ->
          model.benefactors.items.any { it.title == apps.benefactor.name } &&
            model.benefactors.items.single { it.title == apps.benefactor.name }.secondaryText == "Claim pending"
        }
      )
      cancelAndIgnoreRemainingEvents()
    }
  }

  test("Complete Inheritance Claim") {
    val apps = launchInheritanceApps()
    apps.benefactor.app.addSomeFunds(
      amount = BitcoinMoney.sats(10_000L),
      waitForConfirmation = true
    )
    apps.advanceThroughClaimStart()
    apps.skipDelayPeriod()

    apps.beneficiary.app.inheritanceManagementUiStateMachine.test(
      turbineTimeout = 60.seconds,
      props = InheritanceManagementUiProps(
        account = apps.beneficiary.account(),
        selectedTab = ManagingInheritanceTab.Beneficiaries,
        onBack = { error("No exit calls expected") },
        onGoToUtxoConsolidation = { error("No UTXO consolidation expected") }
      )
    ) {
      awaitUntilBody<ManagingInheritanceBodyModel>(
        matching = { model ->
          model.benefactors.items.any { it.title == apps.benefactor.name } &&
            model.benefactors.items.single {
              it.title == apps.benefactor.name
            }.secondaryText == "Claim approved"
        }
      ) {
        benefactors.items.single { it.title == apps.benefactor.name }
          .trailingAccessory
          .shouldBeInstanceOf<ListItemAccessory.ButtonAccessory>()
          .model
          .onClick()
      }
      awaitUntilSheet<ManageInheritanceContactBodyModel>(
        matching = { it.claimControls is ManageInheritanceContactBodyModel.ClaimControls.Complete }
      ) {
        claimControls.shouldBeInstanceOf<ManageInheritanceContactBodyModel.ClaimControls.Complete>()
          .onClick()
      }
      advanceUntilScreenWithBody<InheritanceTransferSuccessScreenModel>()
        .onDone()
      awaitUntilBody<ManagingInheritanceBodyModel>(
        matching = { model ->
          model.benefactors.items.any { it.title == apps.benefactor.name } &&
            model.benefactors.items.single {
              it.title == apps.benefactor.name
            }.secondaryText == "Claim approved"
        }
      )
      cancelAndIgnoreRemainingEvents()
    }
    apps.beneficiary.app.getActiveWallet().run {
      sync().shouldBeOk()
      balance().first().total.value.isZero().shouldBeFalse()
    }
    apps.benefactor.app.getActiveWallet().run {
      sync().shouldBeOk()
      balance().first().total.value.isZero().shouldBeTrue()
    }
    apps.beneficiary.app.getActiveWallet()
    apps.beneficiary.app.returnFundsToTreasury()
  }

  test("Complete Inheritance Claim -- empty wallet") {
    val apps = launchInheritanceApps()
    apps.advanceThroughClaimStart()
    apps.skipDelayPeriod()

    apps.beneficiary.app.inheritanceManagementUiStateMachine.test(
      turbineTimeout = 60.seconds,
      props = InheritanceManagementUiProps(
        account = apps.beneficiary.account(),
        selectedTab = ManagingInheritanceTab.Beneficiaries,
        onBack = { error("No exit calls expected") },
        onGoToUtxoConsolidation = { error("No UTXO consolidation expected") }
      )
    ) {
      awaitUntilBody<ManagingInheritanceBodyModel>(
        matching = { model ->
          model.benefactors.items.any { it.title == apps.benefactor.name } &&
            model.benefactors.items.single {
              it.title == apps.benefactor.name
            }.secondaryText == "Claim approved"
        }
      ) {
        benefactors.items.single { it.title == apps.benefactor.name }
          .trailingAccessory
          .shouldBeInstanceOf<ListItemAccessory.ButtonAccessory>()
          .model
          .onClick()
      }
      awaitUntilSheet<ManageInheritanceContactBodyModel>(
        matching = { it.claimControls is ManageInheritanceContactBodyModel.ClaimControls.Complete }
      ) {
        claimControls.shouldBeInstanceOf<ManageInheritanceContactBodyModel.ClaimControls.Complete>()
          .onClick()
      }
      advanceUntilScreenWithBody<EmptyBenefactorWalletScreenModel>()
        .onClose()
      awaitUntilBody<ManagingInheritanceBodyModel>(
        matching = { model ->
          model.benefactors.items.any { it.title == apps.benefactor.name } &&
            model.benefactors.items.single {
              it.title == apps.benefactor.name
            }.secondaryText == "Claim approved"
        }
      )
      cancelAndIgnoreRemainingEvents()
    }
  }

  test("Beneficiary cancels inheritance claim") {
    val apps = launchInheritanceApps()
    apps.advanceThroughClaimStart()

    apps.beneficiary.app.inheritanceManagementUiStateMachine.test(
      turbineTimeout = 60.seconds,
      props = InheritanceManagementUiProps(
        account = apps.beneficiary.account(),
        selectedTab = ManagingInheritanceTab.Beneficiaries,
        onBack = { error("No exit calls expected") },
        onGoToUtxoConsolidation = { error("No UTXO consolidation expected") }
      )
    ) {
      awaitUntilBody<ManagingInheritanceBodyModel>(
        matching = { model ->
          model.benefactors.items.any { it.title == apps.benefactor.name } &&
            model.benefactors.items.single { it.title == apps.benefactor.name }.secondaryText == "Claim pending"
        }
      ) {
        benefactors.items.single { it.title == apps.benefactor.name }
          .trailingAccessory
          .shouldBeInstanceOf<ListItemAccessory.ButtonAccessory>()
          .model
          .onClick()
      }
      awaitUntilSheet<ManageInheritanceContactBodyModel>(
        matching = { it.claimControls is ManageInheritanceContactBodyModel.ClaimControls.Cancel }
      ) {
        claimControls.shouldBeInstanceOf<ManageInheritanceContactBodyModel.ClaimControls.Cancel>()
          .onClick()
      }
      awaitUntilSheet<DestructiveInheritanceActionBodyModel> {
        onPrimaryClick()
      }
      awaitUntilBody<ManagingInheritanceBodyModel>(
        matching = { model ->
          model.benefactors.items.any { it.title == apps.benefactor.name } &&
            model.benefactors.items.single { it.title == apps.benefactor.name }.secondaryText == "Active"
        }
      )
      cancelAndIgnoreRemainingEvents()
    }
  }

  test("Benefactor cancels inheritance claim") {
    val apps = launchInheritanceApps()
    apps.advanceThroughClaimStart()

    apps.benefactor.app.inheritanceManagementUiStateMachine.test(
      turbineTimeout = 60.seconds,
      props = InheritanceManagementUiProps(
        account = apps.beneficiary.account(),
        selectedTab = ManagingInheritanceTab.Beneficiaries,
        onBack = { error("No exit calls expected") },
        onGoToUtxoConsolidation = { error("No UTXO consolidation expected") }
      )
    ) {
      awaitUntilBody<ManagingInheritanceBodyModel>(
        matching = { model ->
          model.beneficiaries.items.any { it.title == apps.beneficiary.name } &&
            model.beneficiaries.items.single {
              it.title == apps.beneficiary.name
            }.secondaryText == "Claim pending"
        }
      ) {
        beneficiaries.items.single { it.title == apps.beneficiary.name }
          .trailingAccessory
          .shouldBeInstanceOf<ListItemAccessory.ButtonAccessory>()
          .model
          .onClick()
      }
      awaitUntilSheet<ManageInheritanceContactBodyModel>(
        matching = { it.claimControls is ManageInheritanceContactBodyModel.ClaimControls.Cancel }
      ) {
        claimControls.shouldBeInstanceOf<ManageInheritanceContactBodyModel.ClaimControls.Cancel>()
          .onClick()
      }
      advanceUntilScreenWithBody<ManagingInheritanceBodyModel>(
        stopCondition = { model ->
          model.beneficiaries.items.any { it.title == apps.beneficiary.name } &&
            model.beneficiaries.items.single { it.title == apps.beneficiary.name }.secondaryText == "Active"
        }
      )
      cancelAndIgnoreRemainingEvents()
    }
  }

  test("Beneficiary key rotation during claim") {
    val apps = launchInheritanceApps()
    apps.advanceThroughClaimStart()
    apps.beneficiary.app.appUiStateMachine.test(
      turbineTimeout = 60.seconds,
      props = Unit
    ) {
      awaitUntilBody<MoneyHomeBodyModel> {
        trailingToolbarAccessoryModel.shouldBeTypeOf<ToolbarAccessoryModel.IconAccessory>()
          .model.onClick.invoke()
      }

      awaitUntilBody<SettingsBodyModel> {
        val mobileDevicesRow = sectionModels.firstNotNullOfOrNull { section ->
          section.rowModels.firstOrNull {
            it.title.equals("Mobile devices", ignoreCase = true)
          }
        }

        mobileDevicesRow.shouldNotBeNull()
          .onClick.invoke()
      }

      screenDecideIfShouldRotate {
        eventTrackerContext shouldBe AuthKeyRotationEventTrackerScreenIdContext.SETTINGS
        this.primaryButton.shouldNotBeNull().onClick.invoke()
      }

      awaitUntilBody<LoadingSuccessBodyModel>(InactiveAppEventTrackerScreenId.ROTATING_AUTH) {
        eventTrackerContext shouldBe AuthKeyRotationEventTrackerScreenIdContext.SETTINGS
      }

      awaitUntilBody<FormBodyModel>(InactiveAppEventTrackerScreenId.SUCCESSFULLY_ROTATED_AUTH) {
        eventTrackerContext shouldBe AuthKeyRotationEventTrackerScreenIdContext.SETTINGS
        this.primaryButton.shouldNotBeNull().onClick.invoke()
      }

      awaitUntilBody<SettingsBodyModel> {
        onBack()
      }

      awaitUntilBody<MoneyHomeBodyModel>()
      cancelAndIgnoreRemainingEvents()
    }
    apps.beneficiary.app.inheritanceManagementUiStateMachine.test(
      turbineTimeout = 60.seconds,
      props = InheritanceManagementUiProps(
        account = apps.beneficiary.account(),
        selectedTab = ManagingInheritanceTab.Beneficiaries,
        onBack = { error("No exit calls expected") },
        onGoToUtxoConsolidation = { }
      )
    ) {
      awaitUntilBody<ManagingInheritanceBodyModel>(
        matching = { model ->
          model.benefactors.items.any { it.title == apps.benefactor.name } &&
            model.benefactors.items.single { it.title == apps.benefactor.name }.secondaryText == "Claim pending"
        }
      ) {
        this.benefactors.items.single { it.title == apps.benefactor.name }
          .trailingAccessory.shouldBeInstanceOf<ListItemAccessory.ButtonAccessory>()
          .model.onClick()
      }
      awaitUntilSheet<ManageInheritanceContactBodyModel> {
        this.claimControls.shouldBeInstanceOf<ManageInheritanceContactBodyModel.ClaimControls.Cancel>()
          .onClick()
      }
      awaitUntilSheet<DestructiveInheritanceActionBodyModel> {
        this.primaryButton.shouldNotBeNull().onClick()
      }
      awaitUntilBody<ManagingInheritanceBodyModel>(
        matching = { model ->
          model.benefactors.items.any { it.title == apps.benefactor.name } &&
            model.benefactors.items.single { it.title == apps.benefactor.name }.secondaryText == "Active"
        }
      )
      cancelAndIgnoreRemainingEvents()
    }
  }
})
