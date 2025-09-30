package build.wallet.statemachine.settings

import app.cash.turbine.Turbine
import build.wallet.availability.AppFunctionalityServiceFake
import build.wallet.availability.AppFunctionalityStatus
import build.wallet.availability.F8eUnreachable
import build.wallet.availability.InternetUnreachable
import build.wallet.coachmark.CoachmarkServiceMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.feature.FeatureFlagValue.BooleanFlag
import build.wallet.feature.flags.PrivateWalletMigrationFeatureFlag
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.StateMachineTester
import build.wallet.statemachine.core.test
import build.wallet.statemachine.settings.SettingsListUiProps.SettingsListRow.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import kotlinx.datetime.Instant
import kotlin.reflect.KClass

class SettingsListUiStateMachineImplTests : FunSpec({

  val appFunctionalityService = AppFunctionalityServiceFake()
  val featureFlagDao = FeatureFlagDaoFake()

  val privateWalletMigrationFeatureFlag = PrivateWalletMigrationFeatureFlag(
    featureFlagDao = featureFlagDao
  )

  val stateMachine = SettingsListUiStateMachineImpl(
    appFunctionalityService = appFunctionalityService,
    coachmarkService = CoachmarkServiceMock(turbineFactory = turbines::create),
    privateWalletMigrationFeatureFlag = privateWalletMigrationFeatureFlag
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
  }

  test("onBack calls props onBack") {
    stateMachine.test(props) {
      awaitItem().shouldBeTypeOf<SettingsBodyModel>()
        .onBack.shouldNotBeNull().invoke()
      propsOnBackCalls.awaitItem()
    }
  }

  test("list for full account") {
    stateMachine.test(props) {
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
              "Advanced" to listOf("Custom Electrum Server", "UTXO Consolidation"),
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
      awaitItem()
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
          "Help Center",
          "Contact Us"
        )
      )
    }
  }

  test("Disabled rows in LimitedFunctionality.InternetUnreachable") {
    stateMachine.test(props) {
      awaitItem()
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
          "Contact Us",
          "Help Center"
        )
      )
    }
  }

  test("private wallet migration row shown when feature flag enabled") {
    privateWalletMigrationFeatureFlag.setFlagValue(BooleanFlag(true))

    stateMachine.test(props) {
      awaitItem().shouldBeTypeOf<SettingsBodyModel>().apply {
        val advancedSection = sectionModels.first { it.sectionHeaderTitle == "Advanced" }
        val migrationRow = advancedSection.rowModels.firstOrNull { it.title == "Enhanced wallet privacy" }
        migrationRow.shouldNotBeNull()
      }
    }
  }

  test("private wallet migration row hidden when feature flag disabled") {
    privateWalletMigrationFeatureFlag.setFlagValue(BooleanFlag(false))

    stateMachine.test(props) {
      awaitItem().shouldBeTypeOf<SettingsBodyModel>().apply {
        val advancedSection = sectionModels.first { it.sectionHeaderTitle == "Advanced" }
        val migrationRow = advancedSection.rowModels.firstOrNull { it.title == "Enhanced wallet privacy" }
        migrationRow shouldBe null
      }
    }
  }
})

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
