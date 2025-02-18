package build.wallet.statemachine.settings

import app.cash.turbine.Turbine
import build.wallet.availability.AppFunctionalityServiceFake
import build.wallet.availability.AppFunctionalityStatus
import build.wallet.availability.F8eUnreachable
import build.wallet.availability.InternetUnreachable
import build.wallet.cloud.backup.health.CloudBackupHealthRepositoryMock
import build.wallet.cloud.backup.health.MobileKeyBackupStatus
import build.wallet.coachmark.CoachmarkServiceMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.F8eEnvironment
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.StateMachineTester
import build.wallet.statemachine.core.testWithVirtualTime
import build.wallet.statemachine.settings.SettingsListUiProps.SettingsListRow.*
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconTint
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import kotlinx.datetime.Instant
import kotlin.reflect.KClass

class SettingsListUiStateMachineImplTests : FunSpec({

  val appFunctionalityService = AppFunctionalityServiceFake()
  val cloudBackupHealthRepository = CloudBackupHealthRepositoryMock(turbines::create)

  val stateMachine =
    SettingsListUiStateMachineImpl(
      appFunctionalityService = appFunctionalityService,
      cloudBackupHealthRepository = cloudBackupHealthRepository,
      coachmarkService = CoachmarkServiceMock(turbineFactory = turbines::create)
    )

  val propsOnBackCalls = turbines.create<Unit>("props onBack calls")
  val propsOnClickCalls: Map<KClass<out SettingsListUiProps.SettingsListRow>, Turbine<Unit>> =
    mapOf(
      BitkeyDevice::class to turbines.create("BitkeyDevice onClick calls"),
      CustomElectrumServer::class to turbines.create("CustomElectrumServer onClick calls"),
      AppearancePreference::class to turbines.create("AppearancePreference onClick calls"),
      HelpCenter::class to turbines.create("HelpCenter onClick calls"),
      MobilePay::class to turbines.create("MobilePay onClick calls"),
      NotificationPreferences::class to turbines.create("Notifications onClick calls"),
      ContactUs::class to turbines.create("SendFeedback onClick calls"),
      TrustedContacts::class to turbines.create("TrustedContacts onClick calls"),
      CloudBackupHealth::class to turbines.create("CloudBackupHealth onClick calls"),
      RotateAuthKey::class to turbines.create("RotateAuthKey onClick calls"),
      Biometric::class to turbines.create("Biometric onClick calls")
    )

  val props =
    SettingsListUiProps(
      onBack = { propsOnBackCalls.add(Unit) },
      f8eEnvironment = F8eEnvironment.Production,
      supportedRows =
        setOf(
          BitkeyDevice { propsOnClickCalls[BitkeyDevice::class]?.add(Unit) },
          CustomElectrumServer { propsOnClickCalls[CustomElectrumServer::class]?.add(Unit) },
          AppearancePreference { propsOnClickCalls[AppearancePreference::class]?.add(Unit) },
          HelpCenter { propsOnClickCalls[HelpCenter::class]?.add(Unit) },
          MobilePay { propsOnClickCalls[MobilePay::class]?.add(Unit) },
          NotificationPreferences { propsOnClickCalls[NotificationPreferences::class]?.add(Unit) },
          ContactUs { propsOnClickCalls[ContactUs::class]?.add(Unit) },
          TrustedContacts { propsOnClickCalls[TrustedContacts::class]?.add(Unit) },
          CloudBackupHealth { propsOnClickCalls[CloudBackupHealth::class]?.add(Unit) },
          RotateAuthKey { propsOnClickCalls[RotateAuthKey::class]?.add(Unit) },
          Biometric { propsOnClickCalls[Biometric::class]?.add(Unit) },
          UtxoConsolidation { propsOnClickCalls[UtxoConsolidation::class]?.add(Unit) }
        ),
      onShowAlert = {},
      onDismissAlert = {}
    )

  afterEach {
    appFunctionalityService.reset()
    cloudBackupHealthRepository.reset()
  }

  test("onBack calls props onBack") {
    stateMachine.testWithVirtualTime(props) {
      awaitItem().shouldBeTypeOf<SettingsBodyModel>()
        .onBack.shouldNotBeNull().invoke()
      propsOnBackCalls.awaitItem()
    }
  }

  test("list default") {
    stateMachine.testWithVirtualTime(props) {
      awaitItem().shouldBeTypeOf<SettingsBodyModel>().apply {
        sectionModels
          .map { it.sectionHeaderTitle to it.rowModels.map { row -> row.title } }
          .shouldBe(
            listOf(
              "General" to listOf("Transfers", "Bitkey Device", "Appearance", "Notifications"),
              "Security & Recovery" to listOf("App Security", "Mobile Devices", "Cloud Backup", "Trusted Contacts"),
              "Advanced" to listOf("Custom Electrum Server", "UTXO Consolidation"),
              "Support" to listOf("Contact Us", "Help Center")
            )
          )
      }
    }
  }

  test("cloud backup health setting when mobile backup has problem") {
    cloudBackupHealthRepository.mobileKeyBackupStatus.value =
      MobileKeyBackupStatus.ProblemWithBackup.NoCloudAccess
    stateMachine.testWithVirtualTime(props) {
      awaitItem().shouldBeTypeOf<SettingsBodyModel>().apply {
        sectionModels
          .first { it.sectionHeaderTitle == "Security & Recovery" }
          .rowModels
          .first { it.title == "Cloud Backup" }
          .should {
            it.specialTrailingIconModel.shouldNotBeNull()
              .shouldBe(
                IconModel(
                  icon = Icon.SmallIconInformationFilled,
                  iconSize = IconSize.Small,
                  iconTint = IconTint.Warning
                )
              )
          }
      }
    }
  }

  test("Transfer settings updates state") {
    stateMachine
      .testRowOnClickCallsProps<MobilePay>("Transfers", props, propsOnClickCalls)
  }

  test("Bitkey Device updates state") {
    stateMachine
      .testRowOnClickCallsProps<BitkeyDevice>("Bitkey Device", props, propsOnClickCalls)
  }

  test("Currency updates state") {
    stateMachine
      .testRowOnClickCallsProps<AppearancePreference>("Appearance", props, propsOnClickCalls)
  }

  test("Notifications updates state") {
    stateMachine
      .testRowOnClickCallsProps<NotificationPreferences>("Notifications", props, propsOnClickCalls)
  }

  test("Trusted Contacts updates state") {
    stateMachine
      .testRowOnClickCallsProps<TrustedContacts>("Trusted Contacts", props, propsOnClickCalls)
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

  test("App Security updates state") {
    stateMachine
      .testRowOnClickCallsProps<Biometric>("App Security", props, propsOnClickCalls)
  }

  test("Disabled rows in LimitedFunctionality.F8eUnreachable") {
    stateMachine.testWithVirtualTime(props) {
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
          "Trusted Contacts",
          "Help Center",
          "Mobile Devices",
          "Cloud Backup",
          "Contact Us"
        )
      )
    }
  }

  test("Disabled rows in LimitedFunctionality.InternetUnreachable") {
    stateMachine.testWithVirtualTime(props) {
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
          "Trusted Contacts",
          "Custom Electrum Server",
          "Help Center",
          "Mobile Devices",
          "Cloud Backup",
          "Contact Us",
          "UTXO Consolidation"
        )
      )
    }
  }
})

suspend inline fun <reified T : SettingsListUiProps.SettingsListRow> SettingsListUiStateMachine.testRowOnClickCallsProps(
  rowTitle: String,
  props: SettingsListUiProps,
  propsOnClickCalls: Map<KClass<out SettingsListUiProps.SettingsListRow>, Turbine<Unit>>,
) {
  testWithVirtualTime(props) {
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
