package build.wallet.statemachine.start

import build.wallet.coroutines.turbine.turbines
import build.wallet.statemachine.BodyModelMock
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.core.test
import build.wallet.statemachine.data.keybox.AccountData
import build.wallet.statemachine.recovery.cloud.AccessCloudBackupUiProps
import build.wallet.statemachine.recovery.cloud.AccessCloudBackupUiStateMachine
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class GettingStartedRoutingStateMachineTests : FunSpec({
  val accessCloudBackupUiStateMachine =
    object : AccessCloudBackupUiStateMachine, ScreenStateMachineMock<AccessCloudBackupUiProps>(
      id = "access-cloud-backup"
    ) {}

  val stateMachine =
    GettingStartedRoutingStateMachineImpl(
      accessCloudBackupUiStateMachine = accessCloudBackupUiStateMachine
    )

  val startLiteAccountCreationCalls = turbines.create<Unit>("Create Lite Account")
  val startLiteAccountRecoveryCalls = turbines.create<Unit>("Recover Lite Account")
  val startCloudRecoveryCalls = turbines.create<Unit>("Cloud Recovery")
  val startLostAppRecoveryCalls = turbines.create<Unit>("App Recovery")
  val importEmergencyAccessKitCalls = turbines.create<Unit>("Import Emergency Exit Kit")
  val onExitCalls = turbines.create<Unit>("Exit")

  test("Load backup") {
    stateMachine.test(
      props = GettingStartedRoutingProps(
        startIntent = AccountData.StartIntent.RestoreBitkey,
        onStartLiteAccountCreation = { startLiteAccountCreationCalls.add(Unit) },
        onStartLiteAccountRecovery = { startLiteAccountRecoveryCalls.add(Unit) },
        onStartCloudRecovery = { startCloudRecoveryCalls.add(Unit) },
        onStartLostAppRecovery = { startLostAppRecoveryCalls.add(Unit) },
        onImportEmergencyAccessKit = { importEmergencyAccessKitCalls.add(Unit) },
        onExit = { onExitCalls.add(Unit) }
      )
    ) {
      awaitItem().body
        .shouldBeInstanceOf<BodyModelMock<AccessCloudBackupUiProps>>()
        .id.shouldBe("access-cloud-backup")
    }
  }
})
