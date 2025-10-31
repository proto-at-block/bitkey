package build.wallet.statemachine.settings

import app.cash.turbine.Turbine
import build.wallet.availability.AppFunctionalityServiceFake
import build.wallet.availability.AppFunctionalityStatus
import build.wallet.availability.F8eUnreachable
import build.wallet.availability.InternetUnreachable
import build.wallet.coachmark.CoachmarkServiceMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.StateMachineTester
import build.wallet.statemachine.core.test
import build.wallet.statemachine.settings.SettingsListUiProps.SettingsListRow.*
import build.wallet.statemachine.ui.awaitUntilBodyModel
import build.wallet.wallet.migration.PrivateWalletMigrationServiceFake
import build.wallet.wallet.migration.PrivateWalletMigrationState
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import kotlinx.datetime.Instant
import kotlin.reflect.KClass

class SettingsListUiStateMachineImplTests : FunSpec({

  val appFunctionalityService = AppFunctionalityServiceFake()
  val featureFlagDao = FeatureFlagDaoFake()

  val privateWalletMigrationService = PrivateWalletMigrationServiceFake()

  val stateMachine = SettingsListUiStateMachineImpl(
    appFunctionalityService = appFunctionalityService,
    coachmarkService = CoachmarkServiceMock(turbineFactory = turbines::create),
    privateWalletMigrationService = privateWalletMigrationService
  )

  val propsOnBackCalls = turbines.create<Unit>("props onBack calls")
  val propsOnClickCalls: Map<KClass<out SettingsListUiProps.SettingsListRow>, Turbine<Unit>> =
    mapOf(
      CustomElectrumServer::class to turbines.create("CustomElectrumServer onClick calls"),
      AppearancePreference::class to turbines.create("AppearancePreference onClick calls"),
      HelpCenter::class to turbines.create("HelpCenter onClick calls"),
      MobilePay::class to turbines.create("MobilePay onClick calls"),
      NotificationPreferences::class to turbines.create("Notifications onClick calls"),
      ContactUs::class to turbines.create("SendFeedback onClick calls"),
      TrustedContacts::class to turbines.create("TrustedContacts onClick calls"),
      RotateAuthKey::class to turbines.create("RotateAuthKey onClick calls"),
      InheritanceManagement::class to turbines.create("InheritanceManagement onClick calls"),
      PrivateWalletMigration::class to turbines.create("PrivateWalletMigration onClick calls")
    )

  val props =
    SettingsListUiProps(
      onBack = { propsOnBackCalls.add(Unit) },
      supportedRows =
        setOf(
          CustomElectrumServer { propsOnClickCalls[CustomElectrumServer::class]?.add(Unit) },
          AppearancePreference { propsOnClickCalls[AppearancePreference::class]?.add(Unit) },
          HelpCenter { propsOnClickCalls[HelpCenter::class]?.add(Unit) },
          MobilePay { propsOnClickCalls[MobilePay::class]?.add(Unit) },
          NotificationPreferences { propsOnClickCalls[NotificationPreferences::class]?.add(Unit) },
          ContactUs { propsOnClickCalls[ContactUs::class]?.add(Unit) },
          TrustedContacts { propsOnClickCalls[TrustedContacts::class]?.add(Unit) },
          RotateAuthKey { propsOnClickCalls[RotateAuthKey::class]?.add(Unit) },
          UtxoConsolidation { propsOnClickCalls[UtxoConsolidation::class]?.add(Unit) },
          InheritanceManagement { propsOnClickCalls[InheritanceManagement::class]?.add(Unit) },
          PrivateWalletMigration { propsOnClickCalls[PrivateWalletMigration::class]?.add(Unit) }
        ),
      onShowAlert = {},
      onDismissAlert = {},
      goToSecurityHub = {},
      isLiteAccount = false
    )

  afterEach {
    appFunctionalityService.reset()
    featureFlagDao.reset()
    privateWalletMigrationService.reset()
  }

  test("onBack calls props onBack") {
    stateMachine.test(props) {
      awaitItem().shouldBeTypeOf<SettingsBodyModel>()
        .onBack.shouldNotBeNull().invoke()
      propsOnBackCalls.awaitItem()
      cancelAndIgnoreRemainingEvents()
    }
  }

  test("list for full account") {
    stateMachine.test(props) {
      awaitUntilBodyModel<SettingsBodyModel>(matching = { it.privacyMigrationRow != null }).apply {
        sectionModels
          .map { it.sectionHeaderTitle to it.rowModels.map { row -> row.title } }
          .shouldBe(
            listOf(
              "General" to listOf(
                "Transfers",
                "Appearance",
                "Notifications",
                "Mobile Devices",
                "Inheritance"
              ),
              "Advanced" to listOf("Custom Electrum Server", "UTXO Consolidation", "Private Wallet Update"),
              "Support" to listOf("Contact Us", "Help Center")
            )
          )
      }
    }
  }

  test("list for lite account") {
    stateMachine.test(props.copy(isLiteAccount = true)) {
      // *NOTE* These are filtered out in the LiteSettingsHomeUiStateMachine and don't reflect how it
      // would be shown in the UI.
      awaitItem().shouldBeTypeOf<SettingsBodyModel>().apply {
        sectionModels
          .map { it.sectionHeaderTitle to it.rowModels.map { row -> row.title } }
          .shouldBe(
            listOf(
              "General" to listOf(
                "Transfers",
                "Appearance",
                "Notifications",
                "Mobile Devices",
                "Inheritance"
              ),
              "Security & Recovery" to listOf(
                "Recovery Contacts"
              ),
              "Advanced" to listOf("Custom Electrum Server", "UTXO Consolidation"),
              "Support" to listOf("Contact Us", "Help Center")
            )
          )
      }
      cancelAndIgnoreRemainingEvents()
    }
  }

  test("Transfer settings updates state") {
    stateMachine
      .testRowOnClickCallsProps<MobilePay>("Transfers", props, propsOnClickCalls)
  }

  test("Currency updates state") {
    stateMachine
      .testRowOnClickCallsProps<AppearancePreference>("Appearance", props, propsOnClickCalls)
  }

  test("Notifications updates state") {
    stateMachine
      .testRowOnClickCallsProps<NotificationPreferences>("Notifications", props, propsOnClickCalls)
  }

  test("Custom Electrum Server updates state") {
    stateMachine
      .testRowOnClickCallsProps<CustomElectrumServer>(
        "Custom Electrum Server",
        props,
        propsOnClickCalls
      )
  }

  test("Contact Us updates state") {
    stateMachine
      .testRowOnClickCallsProps<ContactUs>("Contact Us", props, propsOnClickCalls)
  }

  test("Help Center updates state") {
    stateMachine
      .testRowOnClickCallsProps<HelpCenter>("Help Center", props, propsOnClickCalls)
  }

  test("Mobile Devices updates state") {
    stateMachine
      .testRowOnClickCallsProps<RotateAuthKey>("Mobile Devices", props, propsOnClickCalls)
  }

  test("Disabled rows in LimitedFunctionality.F8eUnreachable") {
    stateMachine.test(props) {
      // Wait for initial state to settle with all async services loaded
      awaitUntilBodyModel<SettingsBodyModel>(
        matching = { model ->
          model.sectionModels
            .flatMap { it.rowModels }
            .any { it.title == "Private Wallet Update" }
        }
      )
      appFunctionalityService.status.emit(
        AppFunctionalityStatus.LimitedFunctionality(
          cause = F8eUnreachable(Instant.DISTANT_PAST)
        )
      )
      expectDisabledRows(
        setOf(
          "Transfers",
          "Appearance",
          "Notifications",
          "Mobile Devices",
          "Inheritance",
          "Private Wallet Update",
          "Contact Us",
          "Help Center"
        )
      )
    }
  }

  test("Disabled rows in LimitedFunctionality.InternetUnreachable") {
    stateMachine.test(props) {
      // Wait for initial state to settle with all async services loaded
      awaitUntilBodyModel<SettingsBodyModel>(
        matching = { model ->
          model.sectionModels
            .flatMap { it.rowModels }
            .any { it.title == "Private Wallet Update" }
        }
      )
      appFunctionalityService.status.emit(
        AppFunctionalityStatus.LimitedFunctionality(
          cause =
            InternetUnreachable(
              Instant.DISTANT_PAST,
              Instant.DISTANT_PAST
            )
        )
      )
      expectDisabledRows(
        setOf(
          "Transfers",
          "Appearance",
          "Notifications",
          "Mobile Devices",
          "Inheritance",
          "Custom Electrum Server",
          "UTXO Consolidation",
          "Private Wallet Update",
          "Contact Us",
          "Help Center"
        )
      )
    }
  }

  test("private wallet migration row shown when feature flag enabled") {
    stateMachine.test(props) {
      awaitUntilBodyModel<SettingsBodyModel>(matching = {
        it.privacyMigrationRow != null
      }) {
        privacyMigrationRow.shouldNotBeNull().apply {
          coachmarkLabelModel?.text shouldBe "New"
        }
      }
    }
  }

  test("private wallet migration row hidden when feature flag disabled") {
    privateWalletMigrationService.migrationState.value = PrivateWalletMigrationState.NotAvailable

    stateMachine.test(props) {
      awaitItem().shouldBeTypeOf<SettingsBodyModel>().apply {
        privacyMigrationRow shouldBe null
      }
    }
  }
})

private val SettingsBodyModel.privacyMigrationRow get() = sectionModels
  .first { it.sectionHeaderTitle == "Advanced" }
  .rowModels
  .firstOrNull { it.title == "Private Wallet Update" }

suspend inline fun <reified T : SettingsListUiProps.SettingsListRow> SettingsListUiStateMachine.testRowOnClickCallsProps(
  rowTitle: String,
  props: SettingsListUiProps,
  propsOnClickCalls: Map<KClass<out SettingsListUiProps.SettingsListRow>, Turbine<Unit>>,
) {
  test(props) {
    awaitItem().shouldBeTypeOf<SettingsBodyModel>().apply {
      sectionModels.flatMap { it.rowModels }.first { it.title == rowTitle }
        .onClick()
      propsOnClickCalls[T::class]?.awaitItem()
    }
    cancelAndIgnoreRemainingEvents()
  }
}

suspend fun StateMachineTester<SettingsListUiProps, BodyModel>.expectDisabledRows(
  disabledRowTitles: Set<String>,
) {
  awaitItem().shouldBeTypeOf<SettingsBodyModel>().apply {
    sectionModels.flatMap { it.rowModels }.filter { it.isDisabled }.map { it.title }
      .toSet()
      .shouldBe(disabledRowTitles)
  }
}
