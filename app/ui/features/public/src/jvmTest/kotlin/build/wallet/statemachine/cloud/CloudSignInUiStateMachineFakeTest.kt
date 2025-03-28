import build.wallet.analytics.events.screen.context.CloudEventTrackerScreenIdContext
import build.wallet.cloud.store.CloudStoreAccount
import build.wallet.cloud.store.CloudStoreAccountFake
import build.wallet.cloud.store.CloudStoreAccountRepositoryMock
import build.wallet.cloud.store.CloudStoreServiceProviderFake
import build.wallet.coroutines.turbine.turbines
import build.wallet.statemachine.cloud.CloudSignInModelFake
import build.wallet.statemachine.cloud.CloudSignInUiStateMachineFake
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.recovery.cloud.CloudSignInUiProps
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf

class CloudSignInUiStateMachineFakeTest : FunSpec({
  val onSignedInCalled = turbines.create<CloudStoreAccount>("sign in")
  val onSignInFailureCalled = turbines.create<Unit>("sign in failure")

  val fakeAccount = CloudStoreAccountFake("fakeAccount")
  val props =
    CloudSignInUiProps(
      forceSignOut = false,
      onSignedIn = onSignedInCalled::add,
      onSignInFailure = { onSignInFailureCalled.add(Unit) },
      eventTrackerContext = CloudEventTrackerScreenIdContext.ACCOUNT_CREATION
    )
  val cloudStoreAccountRepository = CloudStoreAccountRepositoryMock()
  val cloudStoreServiceProvider = CloudStoreServiceProviderFake
  val stateMachine =
    CloudSignInUiStateMachineFake(
      cloudStoreAccountRepository,
      cloudStoreServiceProvider
    )

  beforeTest {
    cloudStoreAccountRepository.clear()
  }

  test("already signed in") {
    cloudStoreAccountRepository.set(fakeAccount)
    stateMachine.test(props) {
      awaitItem().shouldBeTypeOf<LoadingSuccessBodyModel>()
        .state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      onSignedInCalled.awaitItem().shouldBe(fakeAccount)
    }
  }

  test("sign in") {
    stateMachine.test(props) {
      awaitItem().shouldBeTypeOf<LoadingSuccessBodyModel>()
        .state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      awaitItem().shouldBeTypeOf<CloudSignInModelFake>()
        .signInSuccess(fakeAccount)
      awaitItem().shouldBeTypeOf<LoadingSuccessBodyModel>()
        .state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      onSignedInCalled.awaitItem().shouldBe(fakeAccount)
    }
  }

  test("force sign out") {
    stateMachine.test(props.copy(forceSignOut = true)) {
      awaitItem().shouldBeTypeOf<LoadingSuccessBodyModel>()
        .state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      awaitItem().shouldBeTypeOf<CloudSignInModelFake>()
        .signInSuccess(fakeAccount)
      awaitItem().shouldBeTypeOf<LoadingSuccessBodyModel>()
        .state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      onSignedInCalled.awaitItem().shouldBe(fakeAccount)
    }
  }
})
