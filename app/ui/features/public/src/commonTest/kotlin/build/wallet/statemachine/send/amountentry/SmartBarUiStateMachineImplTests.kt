package build.wallet.statemachine.send.amountentry

import build.wallet.statemachine.core.test
import build.wallet.statemachine.send.TransferAmountUiState
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class SmartBarUiStateMachineImplTests : FunSpec({
  val props = SmartBarUiProps(
    transferAmountState = TransferAmountUiState.ValidAmountEnteredUiState.AmountBelowBalanceUiState,
    onSendMaxClick = {}
  )

  val stateMachine = SmartBarUiStateMachineImpl()

  test("transfer state keeps send max when amount reaches balance") {
    stateMachine.test(
      props.copy(
        transferAmountState = TransferAmountUiState.ValidAmountEnteredUiState.AmountEqualOrAboveBalanceUiState
      )
    ) {
      awaitItem().shouldNotBeNull()
        .title
        .string
        .shouldBe("Send Max (balance minus fees)")
    }
  }

  test("transfer state hides legacy approval and unavailable banners") {
    stateMachine.test(
      props.copy(
        transferAmountState = TransferAmountUiState.ValidAmountEnteredUiState.AmountBelowBalanceUiState
      )
    ) {
      awaitItem().shouldBeNull()
    }

    stateMachine.test(
      props.copy(
        transferAmountState = TransferAmountUiState.InvalidAmountEnteredUiState.InvalidAmountEqualOrAboveBalanceUiState
      )
    ) {
      awaitItem().shouldBeNull()
    }
  }
})
