package build.wallet.statemachine.data.recovery.conflict

import build.wallet.bitkey.factor.PhysicalFactor.App
import build.wallet.bitkey.factor.PhysicalFactor.Hardware
import build.wallet.coroutines.turbine.turbines
import build.wallet.db.DbQueryError
import build.wallet.recovery.RecoveryDaoMock
import build.wallet.statemachine.core.test
import build.wallet.statemachine.data.recovery.conflict.NoLongerRecoveringData.ClearingLocalRecoveryData
import build.wallet.statemachine.data.recovery.conflict.NoLongerRecoveringData.ClearingLocalRecoveryFailedData
import build.wallet.statemachine.data.recovery.conflict.NoLongerRecoveringData.ShowingNoLongerRecoveringData
import com.github.michaelbull.result.Err
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.types.shouldBeInstanceOf

class NoLongerRecoveringDataStateMachineImplTests : FunSpec({

  val recoveryDao = RecoveryDaoMock(turbines::create)
  val stateMachine =
    NoLongerRecoveringDataStateMachineImpl(
      recoveryDao = recoveryDao
    )

  test("canceled lost app happy path") {
    stateMachine.test(NoLongerRecoveringDataStateMachineDataProps(App)) {
      awaitItem().let {
        it.shouldBeInstanceOf<ShowingNoLongerRecoveringData>()
        it.onAcknowledge()
      }

      awaitItem().shouldBeInstanceOf<ClearingLocalRecoveryData>()
      recoveryDao.clearCalls.awaitItem()
    }
  }

  test("canceled lost hardware happy path") {
    stateMachine.test(NoLongerRecoveringDataStateMachineDataProps(Hardware)) {
      awaitItem().let {
        it.shouldBeInstanceOf<ShowingNoLongerRecoveringData>()
        it.onAcknowledge()
      }

      awaitItem().shouldBeInstanceOf<ClearingLocalRecoveryData>()
      recoveryDao.clearCalls.awaitItem()
    }
  }

  test("lost app clear recovery failure - retry and rollback") {
    recoveryDao.clearCallResult = Err(DbQueryError(Throwable()))
    stateMachine.test(NoLongerRecoveringDataStateMachineDataProps(App)) {
      awaitItem().let {
        it.shouldBeInstanceOf<ShowingNoLongerRecoveringData>()
        it.onAcknowledge()
      }

      awaitItem().shouldBeInstanceOf<ClearingLocalRecoveryData>()
      recoveryDao.clearCalls.awaitItem()

      awaitItem().let {
        it.shouldBeInstanceOf<ClearingLocalRecoveryFailedData>()
        it.retry()
      }

      awaitItem().shouldBeInstanceOf<ClearingLocalRecoveryData>()
      recoveryDao.clearCalls.awaitItem()

      awaitItem().let {
        it.shouldBeInstanceOf<ClearingLocalRecoveryFailedData>()
        it.rollback()
      }

      awaitItem().shouldBeInstanceOf<ShowingNoLongerRecoveringData>()
    }
  }

  test("lost hardware clear recovery failure - retry and rollback") {
    recoveryDao.clearCallResult = Err(DbQueryError(Throwable()))
    stateMachine.test(NoLongerRecoveringDataStateMachineDataProps(Hardware)) {
      awaitItem().let {
        it.shouldBeInstanceOf<ShowingNoLongerRecoveringData>()
        it.onAcknowledge()
      }

      awaitItem().shouldBeInstanceOf<ClearingLocalRecoveryData>()
      recoveryDao.clearCalls.awaitItem()

      awaitItem().let {
        it.shouldBeInstanceOf<ClearingLocalRecoveryFailedData>()
        it.retry()
      }

      awaitItem().shouldBeInstanceOf<ClearingLocalRecoveryData>()
      recoveryDao.clearCalls.awaitItem()

      awaitItem().let {
        it.shouldBeInstanceOf<ClearingLocalRecoveryFailedData>()
        it.rollback()
      }

      awaitItem().shouldBeInstanceOf<ShowingNoLongerRecoveringData>()
    }
  }
})
