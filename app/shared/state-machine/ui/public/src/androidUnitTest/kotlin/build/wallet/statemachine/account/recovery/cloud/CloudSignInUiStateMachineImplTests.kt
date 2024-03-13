package build.wallet.statemachine.account.recovery.cloud

import build.wallet.analytics.events.screen.context.CloudEventTrackerScreenIdContext
import build.wallet.cloud.store.CloudAccountMock
import build.wallet.cloud.store.CloudStoreAccount
import build.wallet.coroutines.turbine.turbines
import build.wallet.statemachine.StateMachineMock
import build.wallet.statemachine.account.recovery.cloud.google.GoogleSignInModel
import build.wallet.statemachine.account.recovery.cloud.google.GoogleSignInModel.SigningIn
import build.wallet.statemachine.account.recovery.cloud.google.GoogleSignInProps
import build.wallet.statemachine.account.recovery.cloud.google.GoogleSignInStateMachine
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.awaitBody
import build.wallet.statemachine.core.test
import build.wallet.statemachine.recovery.cloud.CloudSignInUiProps
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class CloudSignInUiStateMachineImplTests : FunSpec({
  val cloudAccount = CloudAccountMock(instanceId = "123")
  val googleSignInStateMachine =
    object : GoogleSignInStateMachine, StateMachineMock<GoogleSignInProps, GoogleSignInModel>(
      initialModel = SigningIn
    ) {}

  val onSignInFailureCalls = turbines.create<Unit>("cannot access cloud calls")
  val onSignedInCalls = turbines.create<CloudStoreAccount>("on signed in calls")

  val stateMachine = CloudSignInUiStateMachineImpl(googleSignInStateMachine)
  val props =
    CloudSignInUiProps(
      forceSignOut = false,
      onSignInFailure = {
        onSignInFailureCalls.add(Unit)
      },
      onSignedIn = { account ->
        onSignedInCalls.add(account)
      },
      eventTrackerContext = CloudEventTrackerScreenIdContext.ACCOUNT_CREATION
    )

  test("google sign in - success") {
    googleSignInStateMachine.emitModel(GoogleSignInModel.SuccessfullySignedIn(cloudAccount))

    stateMachine.test(props) {
      googleSignInStateMachine.props.forceSignOut.shouldBeFalse()
      awaitBody<LoadingSuccessBodyModel>()

      onSignedInCalls.awaitItem().shouldBe(cloudAccount)
    }
  }

  test("google sign in - force sign out first") {
    googleSignInStateMachine.emitModel(GoogleSignInModel.SuccessfullySignedIn(cloudAccount))

    stateMachine.test(props.copy(forceSignOut = true)) {
      googleSignInStateMachine.props.forceSignOut.shouldBeTrue()
      awaitBody<LoadingSuccessBodyModel>()

      onSignedInCalls.awaitItem().shouldBe(cloudAccount)
    }
  }

  test("google sign in - in progress") {
    googleSignInStateMachine.emitModel(SigningIn)

    stateMachine.test(props) {
      awaitBody<LoadingSuccessBodyModel>()
    }
  }

  test("google sign in - failure") {
    googleSignInStateMachine.emitModel(GoogleSignInModel.SignInFailure(message = "oops"))

    stateMachine.test(props) {
      awaitBody<LoadingSuccessBodyModel>()
    }

    onSignInFailureCalls.awaitItem()
  }

  test("google sign in - cannot exit from loading") {
    googleSignInStateMachine.emitModel(SigningIn)

    stateMachine.test(props) {
      awaitBody<LoadingSuccessBodyModel> {
        onBack.shouldBeNull()
      }
    }
  }
})
