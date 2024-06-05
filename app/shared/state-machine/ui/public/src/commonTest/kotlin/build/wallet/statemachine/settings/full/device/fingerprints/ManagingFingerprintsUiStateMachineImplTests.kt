package build.wallet.statemachine.settings.full.device.fingerprints

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.plusAssign
import build.wallet.analytics.events.EventTrackerMock
import build.wallet.analytics.events.TrackedAction
import build.wallet.analytics.v1.Action
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.compose.collections.immutableListOf
import build.wallet.coroutines.turbine.turbines
import build.wallet.firmware.EnrolledFingerprints
import build.wallet.firmware.FingerprintHandle
import build.wallet.home.GettingStartedTask
import build.wallet.home.GettingStartedTask.TaskId.AddAdditionalFingerprint
import build.wallet.home.GettingStartedTask.TaskState.Complete
import build.wallet.home.GettingStartedTask.TaskState.Incomplete
import build.wallet.home.GettingStartedTaskDaoMock
import build.wallet.nfc.NfcCommandsMock
import build.wallet.nfc.NfcSessionFake
import build.wallet.statemachine.BodyModelMock
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.SheetStateMachineMock
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.awaitScreenWithBody
import build.wallet.statemachine.core.awaitScreenWithBodyModelMock
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel.ListGroup
import build.wallet.statemachine.core.test
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import build.wallet.statemachine.settings.full.device.EnrolledFingerprintResult
import build.wallet.ui.model.icon.IconImage
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconTint
import build.wallet.ui.model.list.ListItemAccessory
import build.wallet.ui.model.list.ListItemAccessory.ButtonAccessory
import build.wallet.ui.model.list.ListItemModel
import build.wallet.ui.model.list.ListItemTreatment
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class ManagingFingerprintsUiStateMachineImplTests : FunSpec({
  val gettingStartedTaskDao = GettingStartedTaskDaoMock(turbines::create)
  val eventTracker = EventTrackerMock(turbines::create)

  val stateMachine = ManagingFingerprintsUiStateMachineImpl(
    editingFingerprintUiStateMachine =
      object : EditingFingerprintUiStateMachine, SheetStateMachineMock<EditingFingerprintProps>(
        id = "editing fingerprints"
      ) {},
    nfcSessionUIStateMachine =
      object : NfcSessionUIStateMachine, ScreenStateMachineMock<NfcSessionUIStateMachineProps<*>>(
        "nfc fingerprints"
      ) {},
    enrollingFingerprintUiStateMachine =
      object : EnrollingFingerprintUiStateMachine, ScreenStateMachineMock<EnrollingFingerprintProps>(
        "enrolling fingerprints"
      ) {},
    gettingStartedTaskDao = gettingStartedTaskDao,
    eventTracker = eventTracker
  )
  val onBackCalls = turbines.create<Unit>("on back calls")
  val onFwUpRequiredCalls = turbines.create<Unit>("onFwUpRequired calls")

  val enrolledFingerprints = EnrolledFingerprints(
    maxCount = 3,
    fingerprintHandles = immutableListOf(
      FingerprintHandle(index = 0, label = "Left Thumb"),
      FingerprintHandle(index = 2, label = "Right Thumb")
    )
  )

  val props = ManagingFingerprintsProps(
    account = FullAccountMock,
    onBack = { onBackCalls += Unit },
    onFwUpRequired = { onFwUpRequiredCalls += Unit },
    entryPoint = EntryPoint.DEVICE_SETTINGS
  )

  val nfcCommandsMock = NfcCommandsMock(turbines::create)

  beforeTest {
    nfcCommandsMock.reset()
  }

  test("MONEY_HOME entry point opens enrollment modal after retrieving fingerprints") {
    stateMachine.test(props.copy(entryPoint = EntryPoint.MONEY_HOME)) {
      // Retrieving enrolled fingerprints
      awaitRetrievingFingerprints(
        nfcCommandsMock = nfcCommandsMock,
        fingerprints = enrolledFingerprints
      )

      // Bottom modal is showing
      with(awaitItem()) {
        bottomSheetModel.shouldNotBeNull()
          .body.shouldBeInstanceOf<BodyModelMock<EditingFingerprintProps>>()
          .latestProps.apply {
            isExistingFingerprint.shouldBeFalse()
          }
      }
    }
  }

  test("fwup required") {
    // Disable fw feature flags
    nfcCommandsMock.setFirmwareFeatureFlags(emptyList())

    stateMachine.test(props) {
      awaitScreenWithBodyModelMock<NfcSessionUIStateMachineProps<EnrolledFingerprintResult>> {
        session(NfcSessionFake(), nfcCommandsMock)
        onSuccess(EnrolledFingerprintResult.FwUpRequired)
      }

      onFwUpRequiredCalls.awaitItem()

      awaitScreenWithBodyModelMock<NfcSessionUIStateMachineProps<EnrolledFingerprintResult>>()
    }
  }

  test("fingerprints are retrieved and listed") {
    stateMachine.test(props) {
      awaitRetrievingFingerprints(
        nfcCommandsMock = nfcCommandsMock,
        fingerprints = enrolledFingerprints
      )

      awaitScreenWithBody<FormBodyModel> {
        mainContentList[0].apply {
          shouldBeInstanceOf<ListGroup>()
          listGroupModel.items[0].shouldBeEnrolledFingerprint(title = "Left Thumb")
          listGroupModel.items[1].shouldBeFingerprintPlaceholder(title = "Finger 2")
          listGroupModel.items[2].shouldBeEnrolledFingerprint(title = "Right Thumb")
        }
      }
    }
  }

  test("onBack calls") {
    stateMachine.test(props) {
      awaitRetrievingFingerprints(
        nfcCommandsMock = nfcCommandsMock,
        fingerprints = enrolledFingerprints
      )

      awaitScreenWithBody<FormBodyModel> {
        val icon = toolbar.shouldNotBeNull()
          .leadingAccessory
          .shouldBeInstanceOf<ToolbarAccessoryModel.IconAccessory>()

        icon.model.onClick.shouldNotBeNull()
          .invoke()
      }

      onBackCalls.awaitItem().shouldBe(Unit)
    }
  }

  test("tap on add fingerprint and cancel") {
    stateMachine.test(props) {
      awaitRetrievingFingerprints(
        nfcCommandsMock = nfcCommandsMock,
        fingerprints = enrolledFingerprints
      )

      // Tap on the Add button
      awaitScreenWithBody<FormBodyModel> {
        mainContentList[0]
          .shouldBeInstanceOf<ListGroup>()
          .clickAddFingerprint(1)
      }

      with(awaitItem()) {
        bottomSheetModel.shouldNotBeNull()
          .body.shouldBeInstanceOf<BodyModelMock<EditingFingerprintProps>>()
          .latestProps.apply {
            isExistingFingerprint.shouldBeFalse()
            onBack.invoke()
          }
      }

      awaitScreenWithBody<FormBodyModel>()
    }
  }

  test("going back from enrollment takes you to fingerprint editing") {
    stateMachine.test(props) {
      awaitRetrievingFingerprints(
        nfcCommandsMock = nfcCommandsMock,
        fingerprints = enrolledFingerprints
      )

      // Tap on the Add button
      awaitScreenWithBody<FormBodyModel> {
        mainContentList[0]
          .shouldBeInstanceOf<ListGroup>()
          .clickAddFingerprint(1)
      }

      val fingerprintHandle = FingerprintHandle(index = 1, label = "Right Pinky")
      with(awaitItem()) {
        bottomSheetModel.shouldNotBeNull()
          .body.shouldBeInstanceOf<BodyModelMock<EditingFingerprintProps>>()
          .latestProps.onSave(fingerprintHandle)
      }

      awaitScreenWithBodyModelMock<EnrollingFingerprintProps> {
        onCancel()
      }

      // See the fingerprint editing bottom sheet
      with(awaitItem()) {
        bottomSheetModel.shouldNotBeNull()
          .body.shouldBeInstanceOf<BodyModelMock<EditingFingerprintProps>>()
          .latestProps.fingerprintToEdit.shouldBe(fingerprintHandle)
      }
    }
  }

  test("tap on add fingerprint and save") {
    stateMachine.test(props) {
      gettingStartedTaskDao.addTasks(
        listOf(GettingStartedTask(id = AddAdditionalFingerprint, state = Incomplete))
      )

      awaitRetrievingFingerprints(
        nfcCommandsMock = nfcCommandsMock,
        fingerprints = enrolledFingerprints
      )

      // Tap on the Add button
      awaitScreenWithBody<FormBodyModel> {
        mainContentList[0]
          .shouldBeInstanceOf<ListGroup>()
          .listGroupModel.items[1].trailingAccessory
          .shouldBeInstanceOf<ButtonAccessory>().apply {
            model.text.shouldBe("Add")
            model.onClick.invoke()
          }
      }

      // "Enroll" a new fingerprint at index 1
      val fingerprints = EnrolledFingerprints(
        maxCount = 3,
        fingerprintHandles = listOf(
          FingerprintHandle(index = 0, label = "Left Thumb"),
          FingerprintHandle(index = 1, label = "Right Pinky"),
          FingerprintHandle(index = 2, label = "Right Thumb")
        )
      )

      with(awaitItem()) {
        bottomSheetModel.shouldNotBeNull()
          .body.shouldBeInstanceOf<BodyModelMock<EditingFingerprintProps>>()
          .latestProps.onSave(fingerprints.fingerprintHandles[1])
      }

      awaitScreenWithBodyModelMock<EnrollingFingerprintProps> {
        fingerprintHandle.shouldBe(fingerprints.fingerprintHandles[1])
        onSuccess(fingerprints)
      }

      eventTracker.eventCalls.awaitItem().shouldBe(
        TrackedAction(
          Action.ACTION_APP_COUNT,
          counterId = FingerprintEventTrackerCounterId.FINGERPRINT_ADDED_COUNT,
          count = 3
        )
      )

      // See the newly enrolled fingerprint in the list
      with(awaitItem()) {
        body.shouldBeInstanceOf<FormBodyModel>().mainContentList[0]
          .shouldBeInstanceOf<ListGroup>()
          .listGroupModel.apply {
            items[0].shouldBeEnrolledFingerprint(title = "Left Thumb")
            items[1].shouldBeEnrolledFingerprint(title = "Right Pinky")
            items[2].shouldBeEnrolledFingerprint(title = "Right Thumb")
          }

        // Delete fingerprint toast is shown
        toastModel.shouldNotBeNull().title.shouldBe("Fingerprint added")
      }

      gettingStartedTaskDao.getTasks().shouldContainExactly(
        GettingStartedTask(
          id = AddAdditionalFingerprint, state = Complete
        )
      )
    }
  }

  test("tap on existing fingerprint and go back") {
    stateMachine.test(props) {
      awaitRetrievingFingerprints(
        nfcCommandsMock = nfcCommandsMock,
        fingerprints = enrolledFingerprints
      )

      // Tap on the edit button
      awaitScreenWithBody<FormBodyModel> {
        mainContentList[0]
          .shouldBeInstanceOf<ListGroup>()
          .clickEditFingerprint(0)
      }

      with(awaitItem()) {
        bottomSheetModel.shouldNotBeNull()
          .body.shouldBeInstanceOf<BodyModelMock<EditingFingerprintProps>>()
          .latestProps.onBack.invoke()
      }

      awaitScreenWithBody<FormBodyModel>()
    }
  }

  test("tap on existing fingerprint and edit label") {
    stateMachine.test(props) {
      awaitRetrievingFingerprints(
        nfcCommandsMock = nfcCommandsMock,
        fingerprints = enrolledFingerprints
      )

      // Tap on the edit button
      awaitScreenWithBody<FormBodyModel> {
        mainContentList[0]
          .shouldBeInstanceOf<ListGroup>()
          .clickEditFingerprint(0)
      }

      // "Edit" the label
      with(awaitItem()) {
        bottomSheetModel.shouldNotBeNull()
          .body.shouldBeInstanceOf<BodyModelMock<EditingFingerprintProps>>()
          .latestProps.onSave(FingerprintHandle(index = 0, label = "New Finger"))
      }

      // "Save" the new label to hardware, which will return the latest fingerprints
      val fingerprints = EnrolledFingerprints(
        maxCount = 3,
        fingerprintHandles = listOf(
          FingerprintHandle(index = 0, label = "New Finger"),
          FingerprintHandle(index = 2, label = "Right Thumb")
        )
      )
      awaitScreenWithBodyModelMock<NfcSessionUIStateMachineProps<EnrolledFingerprints>> {
        session(NfcSessionFake(), nfcCommandsMock)
        onSuccess(fingerprints)
      }

      // Confirm that any in-progress enrollment is canceled when saving a new label
      nfcCommandsMock.cancelFingerprintEnrollmentCalls.awaitItem().shouldBe(Unit)
      // Save the label
      nfcCommandsMock.setFingerprintLabelCalls.awaitItem()
      // Retrieve the most recent fingerprints
      nfcCommandsMock.getEnrolledFingerprintsCalls.awaitItem()

      // See the updated label in the list
      with(awaitItem()) {
        body.shouldBeInstanceOf<FormBodyModel>().mainContentList[0]
          .shouldBeInstanceOf<ListGroup>()
          .listGroupModel.apply {
            items[0].shouldBeEnrolledFingerprint(title = "New Finger")
            items[1].shouldBeFingerprintPlaceholder(title = "Finger 2")
            items[2].shouldBeEnrolledFingerprint(title = "Right Thumb")
          }

        // No toast is shown for editing
        toastModel.shouldBeNull()
      }
    }
  }

  test("tap on existing fingerprint and cancel during nfc keeps original label title") {
    stateMachine.test(props) {
      awaitRetrievingFingerprints(
        nfcCommandsMock = nfcCommandsMock,
        fingerprints = enrolledFingerprints
      )

      // Tap on the edit button
      awaitScreenWithBody<FormBodyModel> {
        mainContentList[0]
          .shouldBeInstanceOf<ListGroup>()
          .clickEditFingerprint(0)
      }

      // "Edit" the label
      with(awaitItem()) {
        bottomSheetModel.shouldNotBeNull()
          .body.shouldBeInstanceOf<BodyModelMock<EditingFingerprintProps>>()
          .latestProps.onSave(FingerprintHandle(index = 0, label = "New Finger"))
      }

      // Cancel the NFC command
      awaitScreenWithBodyModelMock<NfcSessionUIStateMachineProps<EnrolledFingerprints>> {
        onCancel()
      }

      // Ensure the original fingerprint label continues to be "Left Thumb"
      with(awaitItem()) {
        bottomSheetModel.shouldNotBeNull()
          .body.shouldBeInstanceOf<BodyModelMock<EditingFingerprintProps>>()
          .latestProps.apply {
            originalFingerprintLabel.shouldBe("Left Thumb")
            fingerprintToEdit.shouldBe(FingerprintHandle(index = 0, label = "New Finger"))
          }
      }
    }
  }

  test("tap on existing fingerprint and delete it") {
    stateMachine.test(props) {
      awaitRetrievingFingerprints(
        nfcCommandsMock = nfcCommandsMock,
        fingerprints = enrolledFingerprints
      )

      // Tap on the edit button
      awaitScreenWithBody<FormBodyModel> {
        mainContentList[0]
          .shouldBeInstanceOf<ListGroup>()
          .clickEditFingerprint(2)
      }

      // "Delete" the fingerprint
      with(awaitItem()) {
        bottomSheetModel.shouldNotBeNull()
          .body.shouldBeInstanceOf<BodyModelMock<EditingFingerprintProps>>()
          .latestProps.onDeleteFingerprint(FingerprintHandle(index = 2, label = "Right Thumb"))
      }

      // Delete the handle on hardware, which will return the latest fingerprints
      val fingerprints = EnrolledFingerprints(
        maxCount = 3,
        fingerprintHandles = listOf(
          FingerprintHandle(index = 0, label = "New Finger")
        )
      )
      awaitScreenWithBodyModelMock<NfcSessionUIStateMachineProps<EnrolledFingerprints>> {
        session(NfcSessionFake(), nfcCommandsMock)
        onSuccess(fingerprints)
      }

      eventTracker.eventCalls.awaitItem().shouldBe(
        TrackedAction(
          Action.ACTION_APP_COUNT,
          counterId = FingerprintEventTrackerCounterId.FINGERPRINT_DELETED_COUNT,
          count = 1
        )
      )

      // Confirm that any in-progress enrollment is canceled when deleting a fingerprint
      nfcCommandsMock.cancelFingerprintEnrollmentCalls.awaitItem().shouldBe(Unit)
      // Delete the fingerprint
      nfcCommandsMock.deleteFingerprintCalls.awaitItem()
      // Retrieve the most recent fingerprints
      nfcCommandsMock.getEnrolledFingerprintsCalls.awaitItem()

      // See that the index is now a placeholder in the list
      with(awaitItem()) {
        body.shouldBeInstanceOf<FormBodyModel>().mainContentList[0]
          .shouldBeInstanceOf<ListGroup>()
          .listGroupModel.apply {
            items[0].shouldBeEnrolledFingerprint(title = "New Finger")
            items[1].shouldBeFingerprintPlaceholder(title = "Finger 2")
            items[2].shouldBeFingerprintPlaceholder(title = "Finger 3")
          }

        // Delete fingerprint toast is shown
        toastModel.shouldNotBeNull().title.shouldBe("Fingerprint deleted")
      }
    }
  }
})

private suspend fun ReceiveTurbine<ScreenModel>.awaitRetrievingFingerprints(
  nfcCommandsMock: NfcCommandsMock,
  fingerprints: EnrolledFingerprints,
) = apply {
  awaitScreenWithBodyModelMock<NfcSessionUIStateMachineProps<EnrolledFingerprintResult>> {
    session(NfcSessionFake(), nfcCommandsMock)
    onSuccess(EnrolledFingerprintResult.Success(fingerprints))
  }

  // Check that any existing enrollment is canceled.
  nfcCommandsMock.cancelFingerprintEnrollmentCalls.awaitItem().shouldBe(Unit)
  nfcCommandsMock.getEnrolledFingerprintsCalls.awaitItem()
}

private fun ListGroup.clickEditFingerprint(index: Int) =
  apply {
    listGroupModel.items[index].trailingAccessory
      .shouldBeInstanceOf<ButtonAccessory>().apply {
        model.text.shouldBe("Edit")
        model.onClick.invoke()
      }
  }

private fun ListGroup.clickAddFingerprint(index: Int) =
  apply {
    listGroupModel.items[index].trailingAccessory
      .shouldBeInstanceOf<ButtonAccessory>().apply {
        model.text.shouldBe("Add")
        model.onClick.invoke()
      }
  }

private fun ListItemModel.shouldBeEnrolledFingerprint(title: String) =
  apply {
    shouldBeFingerprintListItem(
      title = title,
      iconTint = IconTint.Foreground,
      treatment = ListItemTreatment.PRIMARY,
      trailingAccessoryText = "Edit"
    )
  }

private fun ListItemModel.shouldBeFingerprintPlaceholder(title: String) =
  apply {
    shouldBeFingerprintListItem(
      title = title,
      iconTint = IconTint.On30,
      treatment = ListItemTreatment.SECONDARY,
      trailingAccessoryText = "Add"
    )
  }

private fun ListItemModel.shouldBeFingerprintListItem(
  title: String,
  iconTint: IconTint,
  treatment: ListItemTreatment,
  trailingAccessoryText: String,
) {
  leadingAccessory.apply {
    shouldBeInstanceOf<ListItemAccessory.IconAccessory>()
    model.apply {
      iconImage.shouldBe(IconImage.LocalImage(icon = Icon.SmallIconFingerprint))
      iconTint.shouldBe(iconTint)
      iconSize.shouldBe(IconSize.Small)
    }
  }
  title.shouldBe(title)
  treatment.shouldBe(treatment)
  trailingAccessory.apply {
    shouldBeInstanceOf<ButtonAccessory>()
    model.text.shouldBe(trailingAccessoryText)
  }
}
