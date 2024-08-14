package build.wallet.statemachine.data.keybox

import build.wallet.bitkey.keybox.KeyboxMock
import build.wallet.bitkey.keybox.LiteAccountMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.keybox.KeyboxDaoMock
import build.wallet.statemachine.StateMachineMock
import build.wallet.statemachine.core.test
import build.wallet.statemachine.data.account.CreateFullAccountData
import build.wallet.statemachine.data.account.create.CreateFullAccountDataProps
import build.wallet.statemachine.data.account.create.CreateFullAccountDataStateMachine
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.types.shouldBeTypeOf

class HasActiveLiteAccountDataStateMachineTests : FunSpec({

  val createFullAccountDataStateMachine =
    object : CreateFullAccountDataStateMachine,
      StateMachineMock<CreateFullAccountDataProps, CreateFullAccountData>(
        initialModel = CreateFullAccountData.CreateKeyboxData.CreatingAppKeysData(
          rollback = {}
        )
      ) {}
  val keyboxDao = KeyboxDaoMock(turbines::create)

  val stateMachine =
    HasActiveLiteAccountDataStateMachineImpl(
      createFullAccountDataStateMachine = createFullAccountDataStateMachine,
      keyboxDao = keyboxDao
    )

  val props = HasActiveLiteAccountDataProps(
    account = LiteAccountMock
  )

  beforeTest {
    keyboxDao.reset()
    createFullAccountDataStateMachine.reset()
  }

  test("initial state - no onboarding keybox") {
    keyboxDao.onboardingKeybox.value = Ok(null)
    stateMachine.test(props) {
      awaitItem().shouldBeTypeOf<AccountData.HasActiveLiteAccountData>()
    }
  }

  test("initial state - with onboarding keybox") {
    keyboxDao.onboardingKeybox.value = Ok(KeyboxMock)
    stateMachine.test(props) {
      // Before onboarding keybox is pulled from DB
      awaitItem().shouldBeTypeOf<AccountData.HasActiveLiteAccountData>()
      awaitItem().shouldBeTypeOf<AccountData.NoActiveAccountData.CreatingFullAccountData>()
    }
  }

  test("progress to upgrading account state") {
    keyboxDao.onboardingKeybox.value = Ok(null)
    stateMachine.test(props) {
      awaitItem().shouldBeTypeOf<AccountData.HasActiveLiteAccountData>().let {
        it.onUpgradeAccount()
      }
      awaitItem().shouldBeTypeOf<AccountData.NoActiveAccountData.CreatingFullAccountData>()
    }
  }
})
