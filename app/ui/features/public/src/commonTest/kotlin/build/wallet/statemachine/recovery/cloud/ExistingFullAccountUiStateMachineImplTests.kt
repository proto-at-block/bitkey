package build.wallet.statemachine.recovery.cloud

import app.cash.turbine.plusAssign
import build.wallet.cloud.backup.CloudBackupRepositoryFake
import build.wallet.cloud.backup.CloudBackupV2WithFullAccountMock
import build.wallet.cloud.store.CloudAccountMock
import build.wallet.cloud.store.CloudStoreAccountError
import build.wallet.cloud.store.CloudStoreAccountRepositoryMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.platform.device.DevicePlatform
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.ui.awaitBody
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ExistingFullAccountUiStateMachineImplTests : FunSpec({

  val cloudBackupRepository = CloudBackupRepositoryFake()
  val cloudStoreAccountRepository = CloudStoreAccountRepositoryMock()
  val stateMachine = ExistingFullAccountUiStateMachineImpl(
    cloudStoreAccountRepository = cloudStoreAccountRepository,
    cloudBackupRepository = cloudBackupRepository
  )

  val onBackCalls = turbines.create<Unit>("onBack calls")
  val onRestoreCalls = turbines.create<Unit>("onRestore calls")
  val onBackupArchiveCalls = turbines.create<Unit>("onBackupArchive calls")

  val props = ExistingFullAccountUiProps(
    cloudBackup = CloudBackupV2WithFullAccountMock,
    devicePlatform = DevicePlatform.Android,
    onBack = { onBackCalls += Unit },
    onRestore = { onRestoreCalls += Unit },
    onBackupArchive = { onBackupArchiveCalls += Unit }
  )

  beforeTest {
    cloudBackupRepository.reset()
    cloudStoreAccountRepository.reset()
  }

  test("initial screen allows restore and back") {
    stateMachine.test(props) {
      awaitBody<ExistingFullAccountFoundBodyModel> {
        onRestore()
      }
      onRestoreCalls.awaitItem().shouldBe(Unit)
    }
  }

  test("warning and back") {
    stateMachine.test(props) {
      awaitBody<ExistingFullAccountFoundBodyModel> {
        onDeleteBackupAndCreateNew()
      }

      awaitBody<WarningAboutDeletingBackupBodyModel> {
        onBack()
      }

      awaitBody<ExistingFullAccountFoundBodyModel>()
    }
  }

  test("confirm toggles and archive success") {
    val cloudAccount = CloudAccountMock(instanceId = "acct-1")
    cloudStoreAccountRepository.currentAccountResult = Ok(cloudAccount)

    stateMachine.test(props) {
      awaitBody<ExistingFullAccountFoundBodyModel> {
        onDeleteBackupAndCreateNew()
      }

      awaitBody<WarningAboutDeletingBackupBodyModel> {
        onContinue()
      }

      awaitBody<ConfirmingDeleteBackupBodyModel> {
        firstOptionIsConfirmed.shouldBe(false)
        secondOptionIsConfirmed.shouldBe(false)
        onClickFirstOption()
      }

      awaitBody<ConfirmingDeleteBackupBodyModel> {
        firstOptionIsConfirmed.shouldBe(true)
        secondOptionIsConfirmed.shouldBe(false)
        onClickSecondOption()
      }

      awaitBody<ConfirmingDeleteBackupBodyModel> {
        firstOptionIsConfirmed.shouldBe(true)
        secondOptionIsConfirmed.shouldBe(true)
        onConfirmDelete()
      }

      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }

      onBackupArchiveCalls.awaitItem().shouldBe(Unit)
    }
  }

  test("archiving error returns to first screen when no cloud account") {
    cloudStoreAccountRepository.currentAccountResult = Err(CloudStoreAccountError())

    stateMachine.test(props) {
      awaitBody<ExistingFullAccountFoundBodyModel> {
        onDeleteBackupAndCreateNew()
      }

      awaitBody<WarningAboutDeletingBackupBodyModel> {
        onContinue()
      }

      awaitBody<ConfirmingDeleteBackupBodyModel> {
        onClickFirstOption()
        onClickSecondOption()
        onConfirmDelete()
      }

      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      awaitBody<ExistingFullAccountFoundBodyModel>()

      onBackupArchiveCalls.expectNoEvents()
    }
  }
})
