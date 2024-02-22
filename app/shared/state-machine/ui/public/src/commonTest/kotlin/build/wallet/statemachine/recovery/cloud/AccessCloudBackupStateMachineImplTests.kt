package build.wallet.statemachine.recovery.cloud

import app.cash.turbine.plusAssign
import build.wallet.bitkey.f8e.FullAccountIdMock
import build.wallet.cloud.backup.CloudBackup
import build.wallet.cloud.backup.CloudBackupError.UnrectifiableCloudBackupError
import build.wallet.cloud.backup.CloudBackupRepositoryFake
import build.wallet.cloud.backup.CloudBackupV2WithFullAccountMock
import build.wallet.cloud.store.CloudAccountMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.emergencyaccesskit.EakDataFake
import build.wallet.emergencyaccesskit.EmergencyAccessKitAssociation
import build.wallet.feature.FeatureFlagDaoMock
import build.wallet.feature.setFlagValue
import build.wallet.recovery.emergencyaccess.EmergencyAccessFeatureFlag
import build.wallet.statemachine.core.LoadingBodyModel
import build.wallet.statemachine.core.awaitScreenWithBody
import build.wallet.statemachine.core.awaitScreenWithBodyModelMock
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.test
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf

class AccessCloudBackupStateMachineImplTests : FunSpec({

  val accountId = FullAccountIdMock
  val fakeCloudAccount = CloudAccountMock(instanceId = "1")
  val fakeBackup = CloudBackupV2WithFullAccountMock
  val cloudBackupRepository = CloudBackupRepositoryFake()
  val emergencyAccessFeatureFlag = EmergencyAccessFeatureFlag(FeatureFlagDaoMock())
  val stateMachine =
    AccessCloudBackupUiStateMachineImpl(
      cloudSignInUiStateMachine = CloudSignInUiStateMachineMock(),
      cloudBackupRepository = cloudBackupRepository,
      rectifiableErrorHandlingUiStateMachine = RectifiableErrorHandlingUiStateMachineMock(),
      emergencyAccessFeatureFlag = emergencyAccessFeatureFlag
    )

  val exitCalls = turbines.create<Unit>("exit calls")
  val backupFoundCalls = turbines.create<CloudBackup>("backup found calls")
  val cannotAccessCloudCalls = turbines.create<Unit>("cannot access cloud calls")
  val importEmergencyAccessKitCalls = turbines.create<Unit>("import emergency access kit calls")

  val props =
    AccessCloudBackupUiProps(
      eakAssociation = EakDataFake,
      forceSignOutFromCloud = false,
      onExit = {
        exitCalls += Unit
      },
      onBackupFound = { backup ->
        backupFoundCalls += backup
      },
      onCannotAccessCloudBackup = {
        cannotAccessCloudCalls += Unit
      },
      onImportEmergencyAccessKit = {
        importEmergencyAccessKitCalls += Unit
      }
    )

  afterTest {
    cloudBackupRepository.reset()
    emergencyAccessFeatureFlag.setFlagValue(false)
  }

  test("successfully find backup and restore it") {
    cloudBackupRepository.writeBackup(accountId, fakeCloudAccount, fakeBackup)

    stateMachine.test(props) {
      awaitScreenWithBodyModelMock<CloudSignInUiProps> {
        onSignedIn(fakeCloudAccount)
      }

      awaitScreenWithBody<LoadingBodyModel>()

      backupFoundCalls.awaitItem().shouldBe(fakeBackup)
    }
  }

  test("cloud account signed in but cloud backup not found") {
    stateMachine.test(props) {
      awaitScreenWithBodyModelMock<CloudSignInUiProps> {
        onSignedIn(fakeCloudAccount)
      }

      awaitScreenWithBody<LoadingBodyModel>()
      awaitScreenWithBody<FormBodyModel>()
    }
  }

  test("cloud account signed in but failure when trying to access cloud backup") {
    cloudBackupRepository.returnReadError = UnrectifiableCloudBackupError(Exception("oops"))

    stateMachine.test(props) {
      awaitScreenWithBodyModelMock<CloudSignInUiProps> {
        onSignedIn(fakeCloudAccount)
      }

      awaitScreenWithBody<LoadingBodyModel>()
      awaitScreenWithBody<FormBodyModel>()
    }
  }

  test("cloud account signed in but cloud backup not found - exit") {
    stateMachine.test(props) {
      awaitScreenWithBodyModelMock<CloudSignInUiProps> {
        onSignedIn(fakeCloudAccount)
      }

      awaitScreenWithBody<LoadingBodyModel>()
      awaitScreenWithBody<FormBodyModel> {
        onBack?.invoke()
      }

      exitCalls.awaitItem().shouldBe(Unit)
    }
  }

  // See AccessCloudBackupStateMachineImplTests{Android,IOS} for the "check again" tests.
  // The behavior on the two platforms is slightly different.

  test("cloud account signed in but cloud backup not found - cannot access cloud option") {
    stateMachine.test(props) {
      awaitScreenWithBodyModelMock<CloudSignInUiProps> {
        onSignedIn(fakeCloudAccount)
      }

      awaitScreenWithBody<LoadingBodyModel>()
      awaitScreenWithBody<FormBodyModel> {
        cloudBackupRepository.writeBackup(accountId, fakeCloudAccount, fakeBackup)
        mainContentList
          .first()
          .shouldNotBeNull()
          .shouldBeTypeOf<FormMainContentModel.ListGroup>()
          .listGroupModel
          .items[1]
          .onClick.shouldNotBeNull().invoke()
      }

      cannotAccessCloudCalls.awaitItem().shouldBe(Unit)
    }
  }

  test("cloud account sign in failed - emergency access row hidden without feature flag") {
    emergencyAccessFeatureFlag.setFlagValue(false)

    stateMachine.test(props) {
      awaitScreenWithBodyModelMock<CloudSignInUiProps> {
        onSignInFailure()
      }

      awaitScreenWithBody<FormBodyModel> {
        mainContentList
          .first()
          .shouldNotBeNull()
          .shouldBeTypeOf<FormMainContentModel.ListGroup>()
          .listGroupModel
          .items
          .count()
          .shouldBe(2)
      }
    }
  }

  test("cloud account sign in failed - emergency access row always available in EAK build") {
    emergencyAccessFeatureFlag.setFlagValue(false)
    stateMachine.test(props.copy(eakAssociation = EmergencyAccessKitAssociation.EakBuild)) {
      awaitScreenWithBodyModelMock<CloudSignInUiProps> {
        onSignInFailure()
      }

      awaitScreenWithBody<FormBodyModel> {
        mainContentList
          .first()
          .shouldNotBeNull()
          .shouldBeTypeOf<FormMainContentModel.ListGroup>()
          .listGroupModel
          .items[2]
          .onClick.shouldNotBeNull().invoke()
      }

      importEmergencyAccessKitCalls.awaitItem().shouldBe(Unit)
    }
  }

  test("cloud account sign in failed - start emergency access recovery") {
    emergencyAccessFeatureFlag.setFlagValue(true)
    stateMachine.test(props) {
      awaitScreenWithBodyModelMock<CloudSignInUiProps> {
        onSignInFailure()
      }

      awaitScreenWithBody<FormBodyModel> {
        mainContentList
          .first()
          .shouldNotBeNull()
          .shouldBeTypeOf<FormMainContentModel.ListGroup>()
          .listGroupModel
          .items[2]
          .onClick.shouldNotBeNull().invoke()
      }

      importEmergencyAccessKitCalls.awaitItem().shouldBe(Unit)
    }
  }
})
