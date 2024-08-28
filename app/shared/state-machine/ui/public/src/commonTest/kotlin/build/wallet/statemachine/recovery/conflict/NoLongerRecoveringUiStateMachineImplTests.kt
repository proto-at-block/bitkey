package build.wallet.statemachine.recovery.conflict

import build.wallet.bitkey.factor.PhysicalFactor.App
import build.wallet.coroutines.turbine.turbines
import build.wallet.db.DbQueryError
import build.wallet.recovery.RecoveryDaoMock
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.ui.awaitUntilScreenModelWithBody
import build.wallet.statemachine.ui.matchers.shouldBeLoading
import build.wallet.statemachine.ui.matchers.shouldHaveText
import build.wallet.statemachine.ui.matchers.shouldNotBeLoading
import build.wallet.statemachine.ui.robots.click
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf

class NoLongerRecoveringUiStateMachineImplTests : FunSpec({

  val recoveryDao = RecoveryDaoMock(turbines::create)
  val stateMachine = NoLongerRecoveringUiStateMachineImpl(recoveryDao)

  val props = NoLongerRecoveringUiProps(App)

  beforeTest {
    recoveryDao.reset()
  }

  test("cancel local recovery") {
    stateMachine.test(props) {
      awaitUntilScreenModelWithBody<FormBodyModel> {
        bottomSheetModel.shouldBeNull()

        val formBody = body as FormBodyModel
        formBody.run {
          header.shouldNotBeNull().headline.shouldBe("Your recovery attempt has been canceled.")
          primaryButton.shouldHaveText("Got it")
          recoveryDao.clearCalls.expectNoEvents()
          primaryButton.click()
        }
      }

      awaitUntilScreenModelWithBody<FormBodyModel> {
        bottomSheetModel.shouldBeNull()

        val formBody = body as FormBodyModel
        formBody.run {
          header.shouldNotBeNull().headline.shouldBe("Your recovery attempt has been canceled.")
          primaryButton.shouldBeLoading()
        }
      }

      recoveryDao.clearCalls.awaitItem()
    }
  }

  test("fail to cancel local recovery and successfully retry") {
    recoveryDao.clearCallResult = Err(DbQueryError(cause = null))

    stateMachine.test(props) {
      awaitUntilScreenModelWithBody<FormBodyModel> {
        bottomSheetModel.shouldBeNull()
        val formBody = body as FormBodyModel
        formBody.run {
          header.shouldNotBeNull().headline.shouldBe("Your recovery attempt has been canceled.")
          primaryButton.shouldHaveText("Got it")
          recoveryDao.clearCalls.expectNoEvents()
          primaryButton.click()
        }
      }

      awaitUntilScreenModelWithBody<FormBodyModel> {
        bottomSheetModel.shouldBeNull()
        val formBody = body as FormBodyModel
        formBody.run {
          header.shouldNotBeNull().headline.shouldBe("Your recovery attempt has been canceled.")
          primaryButton.shouldBeLoading()
        }
      }

      recoveryDao.clearCalls.awaitItem()

      awaitUntilScreenModelWithBody<FormBodyModel> {
        val formBody = body as FormBodyModel
        formBody.run {
          header.shouldNotBeNull().headline.shouldBe("Your recovery attempt has been canceled.")
          primaryButton.shouldNotBeLoading()
        }

        recoveryDao.clearCallResult = Ok(Unit)

        val sheet = bottomSheetModel.shouldNotBeNull()
        val body = sheet.body.shouldBeTypeOf<FormBodyModel>()
        body.header.shouldNotBeNull().headline.shouldBe("We couldn’t clear the recovery")
        body.primaryButton.shouldHaveText("Retry")
        body.primaryButton.click()
      }

      awaitUntilScreenModelWithBody<FormBodyModel> {
        bottomSheetModel.shouldBeNull()
        val formBody = body as FormBodyModel
        formBody.run {
          header.shouldNotBeNull().headline.shouldBe("Your recovery attempt has been canceled.")
          primaryButton.shouldBeLoading()
        }
      }

      recoveryDao.clearCalls.awaitItem()
    }
  }
})
