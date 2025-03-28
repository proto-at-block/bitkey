package build.wallet.statemachine.send.amountentry

import build.wallet.availability.AppFunctionalityServiceFake
import build.wallet.availability.AppFunctionalityStatus
import build.wallet.availability.F8eUnreachable
import build.wallet.bitcoin.balance.BitcoinBalanceFake
import build.wallet.coroutines.turbine.turbines
import build.wallet.limit.DailySpendingLimitStatus
import build.wallet.limit.MobilePayServiceMock
import build.wallet.money.BitcoinMoney
import build.wallet.statemachine.core.test
import build.wallet.statemachine.send.TransferAmountUiState
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Instant

class TransferCardUiStateMachineImplTests : FunSpec({
  val appFunctionalityService = AppFunctionalityServiceFake()
  val mobilePayService = MobilePayServiceMock(turbines::create)

  val props = TransferCardUiProps(
    bitcoinBalance = BitcoinBalanceFake,
    enteredBitcoinMoney = BitcoinMoney.sats(800),
    transferAmountState = TransferAmountUiState.ValidAmountEnteredUiState.AmountBelowBalanceUiState,
    onHardwareRequiredClick = {},
    onSendMaxClick = {}
  )

  val stateMachine = TransferCardUiStateMachineImpl(
    appFunctionalityService = appFunctionalityService,
    mobilePayService = mobilePayService
  )

  beforeTest {
    appFunctionalityService.reset()
    mobilePayService.reset()
  }

  test("transfer state is AmountEqualOrAboveBalanceUiState") {
    stateMachine.test(
      props.copy(
        transferAmountState = TransferAmountUiState.ValidAmountEnteredUiState.AmountEqualOrAboveBalanceUiState
      )
    ) {
      mobilePayService.getDailySpendingLimitStatusCalls.awaitItem()

      awaitItem().shouldNotBeNull()
        .title
        .shouldNotBeNull()
        .string
        .shouldBe("Send Max (balance minus fees)")
    }
  }

  test("transfer state is AmountBelowBalanceUiState and requires hardware") {
    stateMachine.test(
      props.copy(
        transferAmountState = TransferAmountUiState.ValidAmountEnteredUiState.AmountBelowBalanceUiState
      )
    ) {
      mobilePayService.getDailySpendingLimitStatusCalls.awaitItem()

      awaitItem().shouldNotBeNull()
        .title
        .shouldNotBeNull()
        .string
        .shouldBe("Bitkey approval required")
    }
  }

  test("transfer state is AmountBelowBalanceUiState, f8e is unreachable") {
    appFunctionalityService.status.value = AppFunctionalityStatus.LimitedFunctionality(
      cause = F8eUnreachable(lastReachableTime = Instant.DISTANT_PAST)
    )
    mobilePayService.status = DailySpendingLimitStatus.MobilePayAvailable
    stateMachine.test(
      props.copy(
        transferAmountState = TransferAmountUiState.ValidAmountEnteredUiState.AmountBelowBalanceUiState
      )
    ) {
      mobilePayService.getDailySpendingLimitStatusCalls.awaitItem()

      awaitItem().shouldNotBeNull()
        .title
        .shouldNotBeNull()
        .string
        .shouldBe("Transfer without hardware unavailable")
    }
  }
})
