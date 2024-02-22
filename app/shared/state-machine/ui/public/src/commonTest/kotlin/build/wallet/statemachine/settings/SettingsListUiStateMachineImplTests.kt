package build.wallet.statemachine.settings

import app.cash.turbine.Turbine
import build.wallet.auth.InactiveDeviceIsEnabledFeatureFlag
import build.wallet.availability.AppFunctionalityStatus
import build.wallet.availability.AppFunctionalityStatusProviderMock
import build.wallet.availability.F8eUnreachable
import build.wallet.availability.InternetUnreachable
import build.wallet.cloud.backup.CloudBackupHealthFeatureFlag
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.F8eEnvironment
import build.wallet.feature.FeatureFlagDaoMock
import build.wallet.feature.setFlagValue
import build.wallet.money.MultipleFiatCurrencyEnabledFeatureFlag
import build.wallet.platform.config.AppVariant.Customer
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.StateMachineTester
import build.wallet.statemachine.core.test
import build.wallet.statemachine.settings.SettingsListUiProps.SettingsListRow.BitkeyDevice
import build.wallet.statemachine.settings.SettingsListUiProps.SettingsListRow.CloudBackupHealth
import build.wallet.statemachine.settings.SettingsListUiProps.SettingsListRow.CurrencyPreference
import build.wallet.statemachine.settings.SettingsListUiProps.SettingsListRow.CustomElectrumServer
import build.wallet.statemachine.settings.SettingsListUiProps.SettingsListRow.HelpCenter
import build.wallet.statemachine.settings.SettingsListUiProps.SettingsListRow.MobilePay
import build.wallet.statemachine.settings.SettingsListUiProps.SettingsListRow.Notifications
import build.wallet.statemachine.settings.SettingsListUiProps.SettingsListRow.RotateAuthKey
import build.wallet.statemachine.settings.SettingsListUiProps.SettingsListRow.SendFeedback
import build.wallet.statemachine.settings.SettingsListUiProps.SettingsListRow.TrustedContacts
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import kotlinx.datetime.Instant
import kotlin.reflect.KClass

class SettingsListUiStateMachineImplTests : FunSpec({

  val appFunctionalityStatusProvider = AppFunctionalityStatusProviderMock()
  val featureFlagDao = FeatureFlagDaoMock()
  val multipleFiatCurrencyEnabledFeatureFlag =
    MultipleFiatCurrencyEnabledFeatureFlag(featureFlagDao)
  val cloudBackupHealthFeatureFlag = CloudBackupHealthFeatureFlag(featureFlagDao, Customer)
  val inactiveDeviceIsEnabledFeatureFlag = InactiveDeviceIsEnabledFeatureFlag(featureFlagDao)

  val stateMachine =
    SettingsListUiStateMachineImpl(
      appFunctionalityStatusProvider = appFunctionalityStatusProvider,
      multipleFiatCurrencyEnabledFeatureFlag = multipleFiatCurrencyEnabledFeatureFlag,
      cloudBackupHealthFeatureFlag = cloudBackupHealthFeatureFlag,
      inactiveDeviceIsEnabledFeatureFlag = inactiveDeviceIsEnabledFeatureFlag
    )

  val propsOnBackCalls = turbines.create<Unit>("props onBack calls")
  val propsOnClickCalls: Map<KClass<out SettingsListUiProps.SettingsListRow>, Turbine<Unit>> =
    mapOf(
      BitkeyDevice::class to turbines.create("BitkeyDevice onClick calls"),
      CustomElectrumServer::class to turbines.create("CustomElectrumServer onClick calls"),
      CurrencyPreference::class to turbines.create("CurrencyPreference onClick calls"),
      HelpCenter::class to turbines.create("HelpCenter onClick calls"),
      MobilePay::class to turbines.create("MobilePay onClick calls"),
      Notifications::class to turbines.create("Notifications onClick calls"),
      SendFeedback::class to turbines.create("SendFeedback onClick calls"),
      TrustedContacts::class to turbines.create("TrustedContacts onClick calls"),
      CloudBackupHealth::class to turbines.create("CloudBackupHealth onClick calls"),
      RotateAuthKey::class to turbines.create("RotateAuthKey onClick calls")
    )

  val props =
    SettingsListUiProps(
      onBack = { propsOnBackCalls.add(Unit) },
      f8eEnvironment = F8eEnvironment.Production,
      supportedRows =
        setOf(
          BitkeyDevice { propsOnClickCalls[BitkeyDevice::class]?.add(Unit) },
          CustomElectrumServer { propsOnClickCalls[CustomElectrumServer::class]?.add(Unit) },
          CurrencyPreference { propsOnClickCalls[CurrencyPreference::class]?.add(Unit) },
          HelpCenter { propsOnClickCalls[HelpCenter::class]?.add(Unit) },
          MobilePay { propsOnClickCalls[MobilePay::class]?.add(Unit) },
          Notifications { propsOnClickCalls[Notifications::class]?.add(Unit) },
          SendFeedback { propsOnClickCalls[SendFeedback::class]?.add(Unit) },
          TrustedContacts { propsOnClickCalls[TrustedContacts::class]?.add(Unit) },
          CloudBackupHealth { propsOnClickCalls[CloudBackupHealth::class]?.add(Unit) },
          RotateAuthKey { propsOnClickCalls[RotateAuthKey::class]?.add(Unit) }
        ),
      onShowAlert = {},
      onDismissAlert = {}
    )

  afterEach {
    appFunctionalityStatusProvider.reset()
    multipleFiatCurrencyEnabledFeatureFlag.apply {
      setFlagValue(defaultFlagValue)
    }
    cloudBackupHealthFeatureFlag.apply {
      setFlagValue(defaultFlagValue)
    }
    inactiveDeviceIsEnabledFeatureFlag.apply {
      setFlagValue(defaultFlagValue)
    }
  }

  test("onBack calls props onBack") {
    stateMachine.test(props) {
      awaitItem().shouldBeTypeOf<SettingsBodyModel>()
        .onBack.shouldNotBeNull().invoke()
      propsOnBackCalls.awaitItem()
    }
  }

  test("list with multiple fiat currency enabled") {
    multipleFiatCurrencyEnabledFeatureFlag.setFlagValue(true)
    stateMachine.test(props) {
      awaitItem().shouldBeTypeOf<SettingsBodyModel>().apply {
        sectionModels
          .map { it.sectionHeaderTitle to it.rowModels.map { row -> row.title } }
          .shouldBe(
            listOf(
              "General" to listOf("Mobile Pay", "Bitkey Device", "Currency", "Notifications"),
              "Security & Recovery" to listOf("Trusted Contacts"),
              "Advanced" to listOf("Custom Electrum Server"),
              "Support" to listOf("Send Feedback", "Help Center")
            )
          )
      }
    }
  }

  test("list default") {
    stateMachine.test(props) {
      awaitItem().shouldBeTypeOf<SettingsBodyModel>().apply {
        sectionModels
          .map { it.sectionHeaderTitle to it.rowModels.map { row -> row.title } }
          .shouldBe(
            listOf(
              "General" to listOf("Mobile Pay", "Bitkey Device", "Notifications"),
              "Security & Recovery" to listOf("Trusted Contacts"),
              "Advanced" to listOf("Custom Electrum Server"),
              "Support" to listOf("Send Feedback", "Help Center")
            )
          )
      }
    }
  }

  test("cloud backup health setting when feature flag is enabled") {
    cloudBackupHealthFeatureFlag.setFlagValue(true)
    stateMachine.test(props) {
      awaitItem().shouldBeTypeOf<SettingsBodyModel>().apply {
        sectionModels
          .first { it.sectionHeaderTitle == "Security & Recovery" }
          .rowModels
          .first { it.title == "Cloud Backup" }
          .should {
            it.isDisabled.shouldBeFalse()
          }
      }
    }
  }

  test("key rotation setting when feature flag is enabled") {
    inactiveDeviceIsEnabledFeatureFlag.setFlagValue(true)
    stateMachine.test(props) {
      awaitItem().shouldBeTypeOf<SettingsBodyModel>().apply {
        sectionModels
          .first { it.sectionHeaderTitle == "Security & Recovery" }
          .rowModels
          .first { it.title == "Mobile Devices" }
          .should {
            it.isDisabled.shouldBeFalse()
          }
      }
    }
  }

  test("Mobile Pay updates state") {
    stateMachine
      .testRowOnClickCallsProps<MobilePay>("Mobile Pay", props, propsOnClickCalls)
  }

  test("Bitkey Device updates state") {
    stateMachine
      .testRowOnClickCallsProps<BitkeyDevice>("Bitkey Device", props, propsOnClickCalls)
  }

  test("Currency updates state") {
    multipleFiatCurrencyEnabledFeatureFlag.setFlagValue(true)
    stateMachine
      .testRowOnClickCallsProps<CurrencyPreference>("Currency", props, propsOnClickCalls)
  }

  test("Notifications updates state") {
    stateMachine
      .testRowOnClickCallsProps<Notifications>("Notifications", props, propsOnClickCalls)
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

  test("Send Feedback updates state") {
    stateMachine
      .testRowOnClickCallsProps<SendFeedback>("Send Feedback", props, propsOnClickCalls)
  }

  test("Help Center updates state") {
    stateMachine
      .testRowOnClickCallsProps<HelpCenter>("Help Center", props, propsOnClickCalls)
  }

  test("Mobile Devices updates state") {
    inactiveDeviceIsEnabledFeatureFlag.setFlagValue(true)
    stateMachine
      .testRowOnClickCallsProps<RotateAuthKey>("Mobile Devices", props, propsOnClickCalls)
  }

  test("Disabled rows in LimitedFunctionality.F8eUnreachable") {
    multipleFiatCurrencyEnabledFeatureFlag.setFlagValue(true)
    inactiveDeviceIsEnabledFeatureFlag.setFlagValue(true)
    stateMachine.test(props) {
      awaitItem()
      appFunctionalityStatusProvider.appFunctionalityStatusFlow.emit(
        AppFunctionalityStatus.LimitedFunctionality(
          cause = F8eUnreachable(Instant.DISTANT_PAST)
        )
      )
      expectDisabledRows(
        setOf(
          "Mobile Pay",
          "Currency",
          "Notifications",
          "Trusted Contacts",
          "Help Center",
          "Mobile Devices"
        )
      )
    }
  }

  test("Disabled rows in LimitedFunctionality.InternetUnreachable") {
    multipleFiatCurrencyEnabledFeatureFlag.setFlagValue(true)
    inactiveDeviceIsEnabledFeatureFlag.setFlagValue(true)
    stateMachine.test(props) {
      awaitItem()
      appFunctionalityStatusProvider.appFunctionalityStatusFlow.emit(
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
          "Mobile Pay",
          "Currency",
          "Notifications",
          "Trusted Contacts",
          "Custom Electrum Server",
          "Help Center",
          "Mobile Devices"
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
