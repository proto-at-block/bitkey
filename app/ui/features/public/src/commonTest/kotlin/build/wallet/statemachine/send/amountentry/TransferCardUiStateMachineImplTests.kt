package build.wallet.statemachine.send.amountentry

import build.wallet.availability.AppFunctionalityServiceFake
import build.wallet.availability.AppFunctionalityStatus
import build.wallet.availability.F8eUnreachable
import build.wallet.bitcoin.balance.BitcoinBalanceFake
import build.wallet.coroutines.turbine.turbines
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.feature.flags.DesignSystemUpdatesFeatureFlag
import build.wallet.feature.setFlagValue
import build.wallet.limit.DailySpendingLimitStatus
import build.wallet.limit.MobilePayServiceMock
import build.wallet.money.BitcoinMoney
import build.wallet.statemachine.core.test
import build.wallet.statemachine.send.TransferAmountUiState
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Instant

class TransferCardUiStateMachineImplTests : FunSpec({
  val appFunctionalityService = AppFunctionalityServiceFake()
  val mobilePayService = MobilePayServiceMock(turbines::create)
  val designSystemUpdatesFeatureFlag = DesignSystemUpdatesFeatureFlag(FeatureFlagDaoFake())

  val props = TransferCardUiProps(
    bitcoinBalance = BitcoinBalanceFake,
    enteredBitcoinMoney = BitcoinMoney.sats(800),
    transferAmountState = TransferAmountUiState.ValidAmountEnteredUiState.AmountBelowBalanceUiState,
    onHardwareRequiredClick = {},
    onSendMaxClick = {}
  )

  val stateMachine = TransferCardUiStateMachineImpl(
    appFunctionalityService = appFunctionalityService,
    mobilePayService = mobilePayService,
    designSystemUpdatesFeatureFlag = designSystemUpdatesFeatureFlag
  )

  beforeTest {
    appFunctionalityService.reset()
    mobilePayService.reset()
    designSystemUpdatesFeatureFlag.setFlagValue(false)
  }

  test("legacy transfer state shows send max when amount reaches balance") {
    stateMachine.test(
      props.copy(
        transferAmountState = TransferAmountUiState.ValidAmountEnteredUiState.AmountEqualOrAboveBalanceUiState
      )
    ) {
      mobilePayService.getDailySpendingLimitStatusCalls.awaitItem().shouldBe(BitcoinBalanceFake.spendable)
      awaitItem().shouldNotBeNull()
        .title
        .shouldNotBeNull()
        .string
        .shouldBe("Send Max (balance minus fees)")
    }
  }

  test("legacy transfer state shows insufficient funds banner when send max is unavailable") {
    stateMachine.test(
      props.copy(
        transferAmountState = TransferAmountUiState.InvalidAmountEnteredUiState.InvalidAmountEqualOrAboveBalanceUiState
      )
    ) {
      mobilePayService.getDailySpendingLimitStatusCalls.awaitItem().shouldBe(props.enteredBitcoinMoney)
      awaitItem().shouldNotBeNull()
        .title
        .shouldNotBeNull()
        .string
        .shouldBe("You don't have enough available")
    }
  }

  test("legacy transfer state shows approval required when hardware is needed") {
    mobilePayService.status = DailySpendingLimitStatus.RequiresHardware
    stateMachine.test(
      props.copy(
        transferAmountState = TransferAmountUiState.ValidAmountEnteredUiState.AmountBelowBalanceUiState
      )
    ) {
      mobilePayService.getDailySpendingLimitStatusCalls.awaitItem().shouldBe(props.enteredBitcoinMoney)
      awaitItem().shouldNotBeNull()
        .title
        .shouldNotBeNull()
        .string
        .shouldBe("Bitkey approval required")
    }
  }

  test("legacy transfer state shows transfer without hardware unavailable when f8e is unreachable") {
    appFunctionalityService.status.value = AppFunctionalityStatus.LimitedFunctionality(
      cause = F8eUnreachable(lastReachableTime = Instant.DISTANT_PAST)
    )
    mobilePayService.status = DailySpendingLimitStatus.MobilePayAvailable
    stateMachine.test(
      props.copy(
        transferAmountState = TransferAmountUiState.ValidAmountEnteredUiState.AmountBelowBalanceUiState
      )
    ) {
      mobilePayService.getDailySpendingLimitStatusCalls.awaitItem().shouldBe(props.enteredBitcoinMoney)
      awaitItem().shouldNotBeNull()
        .title
        .shouldNotBeNull()
        .string
        .shouldBe("Transfer without hardware unavailable")
    }
  }

  test("dsv2 transfer state keeps send max when amount reaches balance") {
    designSystemUpdatesFeatureFlag.setFlagValue(true)
    stateMachine.test(
      props.copy(
        transferAmountState = TransferAmountUiState.ValidAmountEnteredUiState.AmountEqualOrAboveBalanceUiState
      )
    ) {
      awaitItem().shouldNotBeNull()
        .title
        .shouldNotBeNull()
        .string
        .shouldBe("Send Max (balance minus fees)")
    }
  }

  test("dsv2 transfer state hides legacy approval and unavailable banners") {
    designSystemUpdatesFeatureFlag.setFlagValue(true)
    mobilePayService.status = DailySpendingLimitStatus.RequiresHardware
    stateMachine.test(
      props.copy(
        transferAmountState = TransferAmountUiState.ValidAmountEnteredUiState.AmountBelowBalanceUiState
      )
    ) {
      awaitItem().shouldBeNull()
    }

    appFunctionalityService.status.value = AppFunctionalityStatus.LimitedFunctionality(
      cause = F8eUnreachable(lastReachableTime = Instant.DISTANT_PAST)
    )
    mobilePayService.status = DailySpendingLimitStatus.MobilePayAvailable
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
