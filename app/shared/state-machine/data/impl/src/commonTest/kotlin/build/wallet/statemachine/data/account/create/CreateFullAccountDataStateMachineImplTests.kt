package build.wallet.statemachine.data.account.create

import build.wallet.bitkey.keybox.KeyboxMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.onboarding.CreateFullAccountContext.NewFullAccount
import build.wallet.statemachine.core.test
import build.wallet.statemachine.data.account.CreateFullAccountData.CreatingAccountData
import build.wallet.statemachine.data.account.CreateFullAccountData.OnboardingAccountData
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeTypeOf

class CreateFullAccountDataStateMachineImplTests : FunSpec({

  val dataStateMachine = CreateFullAccountDataStateMachineImpl()

  val rollbackCalls = turbines.create<Unit>("rollback calls")

  val props = CreateFullAccountDataProps(
    onboardingKeybox = null,
    rollback = { rollbackCalls.add(Unit) },
    context = NewFullAccount
  )

  test("data with no existing onboarding") {
    dataStateMachine.test(props) {
      awaitItem().shouldBeTypeOf<CreatingAccountData>()
        .context.shouldBe(NewFullAccount)
    }
  }

  test("data with existing onboarding") {
    dataStateMachine.test(props.copy(onboardingKeybox = KeyboxMock)) {
      awaitItem().shouldBeInstanceOf<OnboardingAccountData>()
    }
  }

  test("rollback from create keybox") {
    dataStateMachine.test(props) {
      awaitItem().shouldBeInstanceOf<CreatingAccountData>().rollback()
      rollbackCalls.awaitItem()
    }
  }
})
