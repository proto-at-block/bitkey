package build.wallet.statemachine.home.full.card

import build.wallet.availability.AppFunctionalityStatus
import build.wallet.bitkey.auth.AppGlobalAuthPublicKeyMock
import build.wallet.bitkey.keybox.KeyboxMock
import build.wallet.compose.collections.emptyImmutableList
import build.wallet.compose.collections.immutableListOf
import build.wallet.f8e.socrec.SocRecRelationships
import build.wallet.statemachine.StateMachineMock
import build.wallet.statemachine.core.LabelModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.data.firmware.FirmwareDataUpToDateMock
import build.wallet.statemachine.data.keybox.ActiveKeyboxLoadedDataMock
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryData.InitiatingLostHardwareRecoveryData.AwaitingNewHardwareData
import build.wallet.statemachine.moneyhome.card.CardModel
import build.wallet.statemachine.moneyhome.card.MoneyHomeCardsProps
import build.wallet.statemachine.moneyhome.card.MoneyHomeCardsUiStateMachineImpl
import build.wallet.statemachine.moneyhome.card.backup.CloudBackupHealthCardUiProps
import build.wallet.statemachine.moneyhome.card.backup.CloudBackupHealthCardUiStateMachine
import build.wallet.statemachine.moneyhome.card.fwup.DeviceUpdateCardUiProps
import build.wallet.statemachine.moneyhome.card.fwup.DeviceUpdateCardUiStateMachine
import build.wallet.statemachine.moneyhome.card.gettingstarted.GettingStartedCardUiProps
import build.wallet.statemachine.moneyhome.card.gettingstarted.GettingStartedCardUiStateMachine
import build.wallet.statemachine.moneyhome.card.replacehardware.SetupHardwareCardUiProps
import build.wallet.statemachine.moneyhome.card.replacehardware.SetupHardwareCardUiStateMachine
import build.wallet.statemachine.moneyhome.card.sweep.StartSweepCardUiProps
import build.wallet.statemachine.moneyhome.card.sweep.StartSweepCardUiStateMachine
import build.wallet.statemachine.recovery.hardware.HardwareRecoveryStatusCardUiProps
import build.wallet.statemachine.recovery.hardware.HardwareRecoveryStatusCardUiStateMachine
import build.wallet.statemachine.recovery.socrec.RecoveryContactCardsUiProps
import build.wallet.statemachine.recovery.socrec.RecoveryContactCardsUiStateMachine
import build.wallet.statemachine.ui.matchers.shouldHaveSubtitle
import build.wallet.statemachine.ui.matchers.shouldHaveTitle
import build.wallet.statemachine.ui.matchers.shouldNotHaveSubtitle
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldBeSingleton
import io.kotest.matchers.shouldBe
import kotlinx.collections.immutable.ImmutableList

class MoneyHomeCardsStateMachineImplTests : FunSpec({
  val deviceUpdateCardUiStateMachine =
    object : DeviceUpdateCardUiStateMachine, StateMachineMock<DeviceUpdateCardUiProps, CardModel?>(
      initialModel = null
    ) {}
  val gettingStartedCardStateMachine =
    object : GettingStartedCardUiStateMachine, StateMachineMock<GettingStartedCardUiProps, CardModel?>(
      initialModel = null
    ) {}
  val hardwareRecoveryStatusCardUiStateMachine =
    object : HardwareRecoveryStatusCardUiStateMachine, StateMachineMock<HardwareRecoveryStatusCardUiProps, CardModel?>(
      initialModel = null
    ) {}
  val recoveryContactCardsUiStateMachine =
    object : RecoveryContactCardsUiStateMachine, StateMachineMock<RecoveryContactCardsUiProps, ImmutableList<CardModel>>(
      initialModel = emptyImmutableList()
    ) {}
  val setupHardwareCardUiStateMachine =
    object : SetupHardwareCardUiStateMachine, StateMachineMock<SetupHardwareCardUiProps, CardModel?>(
      initialModel = null
    ) {}
  val cloudBackupHealthCardUiStateMachine =
    object : CloudBackupHealthCardUiStateMachine, StateMachineMock<CloudBackupHealthCardUiProps, CardModel?>(
      initialModel = null
    ) {}

  val startSweepCardUiStateMachine =
    object : StartSweepCardUiStateMachine, StateMachineMock<StartSweepCardUiProps, CardModel?>(
      initialModel = null
    ) {}

  val stateMachine =
    MoneyHomeCardsUiStateMachineImpl(
      deviceUpdateCardUiStateMachine = deviceUpdateCardUiStateMachine,
      gettingStartedCardUiStateMachine = gettingStartedCardStateMachine,
      hardwareRecoveryStatusCardUiStateMachine = hardwareRecoveryStatusCardUiStateMachine,
      recoveryContactCardsUiStateMachine = recoveryContactCardsUiStateMachine,
      setupHardwareCardUiStateMachine = setupHardwareCardUiStateMachine,
      cloudBackupHealthCardUiStateMachine = cloudBackupHealthCardUiStateMachine,
      startSweepCardUiStateMachine = startSweepCardUiStateMachine
    )

  val props =
    MoneyHomeCardsProps(
      deviceUpdateCardUiProps =
        DeviceUpdateCardUiProps(
          firmwareData = FirmwareDataUpToDateMock,
          onUpdateDevice = {}
        ),
      gettingStartedCardUiProps =
        GettingStartedCardUiProps(
          accountData = ActiveKeyboxLoadedDataMock,
          appFunctionalityStatus = AppFunctionalityStatus.FullFunctionality,
          trustedContacts = emptyList(),
          onAddBitcoin = {},
          onEnableSpendingLimit = {},
          onInviteTrustedContact = {},
          onAddAdditionalFingerprint = {},
          onShowAlert = {},
          onDismissAlert = {}
        ),
      hardwareRecoveryStatusCardUiProps =
        HardwareRecoveryStatusCardUiProps(
          lostHardwareRecoveryData =
            AwaitingNewHardwareData(
              newAppGlobalAuthKey = AppGlobalAuthPublicKeyMock,
              addHardwareKeys = { _, _, _ -> }
            ),
          onClick = {}
        ),
      recoveryContactCardsUiProps =
        RecoveryContactCardsUiProps(
          relationships = SocRecRelationships.EMPTY,
          onClick = {}
        ),
      setupHardwareCardUiProps =
        SetupHardwareCardUiProps(
          deviceInfo = null,
          onReplaceDevice = {}
        ),
      cloudBackupHealthCardUiProps = CloudBackupHealthCardUiProps(
        appFunctionalityStatus = AppFunctionalityStatus.FullFunctionality,
        onActionClick = {}
      ),
      startSweepCardUiProps = StartSweepCardUiProps(
        onStartSweepClicked = {},
        keybox = KeyboxMock,
      )
    )

  afterTest {
    deviceUpdateCardUiStateMachine.reset()
    gettingStartedCardStateMachine.reset()
    recoveryContactCardsUiStateMachine.reset()
  }

  test("card list should be empty") {
    stateMachine.test(props) {
      awaitItem().cards.shouldBeEmpty()
    }
  }

  test("card list should have length 1 when there is a getting started card") {
    gettingStartedCardStateMachine.emitModel(TEST_CARD_MODEL)
    stateMachine.test(props) {
      awaitItem().cards.shouldBeSingleton()
    }
  }

  test("card list should have length 1 when there is a device update card") {
    deviceUpdateCardUiStateMachine.emitModel(TEST_CARD_MODEL)
    stateMachine.test(props) {
      awaitItem().cards.shouldBeSingleton()
    }
  }

  test("card list should have length 1 when there is a hw status card") {
    hardwareRecoveryStatusCardUiStateMachine.emitModel(TEST_CARD_MODEL)
    stateMachine.test(props) {
      awaitItem().cards.shouldBeSingleton()
    }
  }

  test("card list should include invitation cards in the middle") {
    hardwareRecoveryStatusCardUiStateMachine.emitModel(TEST_CARD_MODEL)
    recoveryContactCardsUiStateMachine.emitModel(
      immutableListOf(
        TEST_CARD_MODEL.copy(subtitle = "first invitation"),
        TEST_CARD_MODEL.copy(subtitle = "second invitation"),
        TEST_CARD_MODEL.copy(subtitle = "third invitation")
      )
    )
    gettingStartedCardStateMachine.emitModel(TEST_CARD_MODEL)
    stateMachine.test(props) {
      awaitItem().cards.let {
        it.size.shouldBe(5)
        it[0].shouldNotHaveSubtitle()
        it[1].shouldHaveSubtitle("first invitation")
        it[2].shouldHaveSubtitle("second invitation")
        it[3].shouldHaveSubtitle("third invitation")
        it[4].shouldNotHaveSubtitle()
      }
    }
  }

  test(
    "card list should have length 1 when there is both a hw status card and a device update card and a replace hardware card"
  ) {
    deviceUpdateCardUiStateMachine.emitModel(TEST_CARD_MODEL)
    hardwareRecoveryStatusCardUiStateMachine.emitModel(
      TEST_CARD_MODEL.copy(
        title =
          LabelModel.StringWithStyledSubstringModel.from(
            "HW CARD",
            emptyMap()
          )
      )
    )
    setupHardwareCardUiStateMachine.emitModel(
      TEST_CARD_MODEL.copy(
        title =
          LabelModel.StringWithStyledSubstringModel.from(
            "REPLACE HW CARD",
            emptyMap()
          )
      )
    )
    stateMachine.test(props) {
      awaitItem().cards
        .single()
        .shouldHaveTitle("HW CARD")
    }
  }
})

val TEST_CARD_MODEL =
  CardModel(
    title =
      LabelModel.StringWithStyledSubstringModel.from(
        "Test Card",
        emptyMap()
      ),
    subtitle = null,
    leadingImage = null,
    content = null,
    style = CardModel.CardStyle.Outline
  )
