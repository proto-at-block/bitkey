package build.wallet.statemachine.send.amountentry

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import build.wallet.availability.AppFunctionalityService
import build.wallet.availability.FunctionalityFeatureStates
import build.wallet.compose.coroutines.rememberStableCoroutineScope
import build.wallet.coroutines.scopes.mapAsStateFlow
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.feature.flags.DesignSystemUpdatesFeatureFlag
import build.wallet.feature.isEnabled
import build.wallet.limit.DailySpendingLimitStatus.RequiresHardware
import build.wallet.limit.MobilePayService
import build.wallet.statemachine.core.Icon.SmallIconBitkey
import build.wallet.statemachine.core.LabelModel
import build.wallet.statemachine.core.LabelModel.Color.ON60
import build.wallet.statemachine.moneyhome.card.CardModel
import build.wallet.statemachine.moneyhome.card.CardModel.CardStyle.Outline
import build.wallet.statemachine.send.TransferAmountUiState.InvalidAmountEnteredUiState.InvalidAmountEqualOrAboveBalanceUiState
import build.wallet.statemachine.send.TransferAmountUiState.ValidAmountEnteredUiState.AmountBelowBalanceUiState
import build.wallet.statemachine.send.TransferAmountUiState.ValidAmountEnteredUiState.AmountEqualOrAboveBalanceUiState

@BitkeyInject(ActivityScope::class)
class TransferCardUiStateMachineImpl(
  private val appFunctionalityService: AppFunctionalityService,
  private val mobilePayService: MobilePayService,
  private val designSystemUpdatesFeatureFlag: DesignSystemUpdatesFeatureFlag,
) : TransferCardUiStateMachine {
  @Composable
  override fun model(props: TransferCardUiProps): CardModel? {
    val scope = rememberStableCoroutineScope()
    val isDesignSystemV2Enabled by remember {
      designSystemUpdatesFeatureFlag.flagValue().mapAsStateFlow(scope) { it.isEnabled() }
    }.collectAsState(initial = designSystemUpdatesFeatureFlag.isEnabled())

    if (isDesignSystemV2Enabled) {
      return when (props.transferAmountState) {
        AmountEqualOrAboveBalanceUiState -> CardModel(
          title =
            LabelModel.StringWithStyledSubstringModel.from(
              string = "Send Max (balance minus fees)",
              substringToColor =
                mapOf(
                  "(balance minus fees)" to ON60
                )
            ),
          subtitle = null,
          leadingImage = null,
          content = null,
          style = Outline,
          onClick = props.onSendMaxClick
        )
        else -> null
      }
    }

    val mobilePayAvailability by remember {
      appFunctionalityService.status
        .mapAsStateFlow(scope) { it.featureStates.mobilePay }
    }.collectAsState()

    val spendingLimitStatus = remember(props.enteredBitcoinMoney, props.bitcoinBalance) {
      val transactionAmount = when (props.transferAmountState) {
        AmountEqualOrAboveBalanceUiState -> props.bitcoinBalance.spendable
        else -> props.enteredBitcoinMoney
      }

      mobilePayService.getDailySpendingLimitStatus(transactionAmount = transactionAmount)
    }

    val requiresHardware = remember(props.enteredBitcoinMoney, spendingLimitStatus) {
      !props.enteredBitcoinMoney.isZero && spendingLimitStatus is RequiresHardware
    }

    val state by remember(
      props.transferAmountState,
      requiresHardware,
      mobilePayAvailability
    ) {
      mutableStateOf(
        when {
          props.transferAmountState is AmountEqualOrAboveBalanceUiState -> State.AmountEqualOrAboveBalanceBanner
          props.transferAmountState is InvalidAmountEqualOrAboveBalanceUiState ->
            State.InsufficientFundsBanner
          props.transferAmountState is AmountBelowBalanceUiState && requiresHardware ->
            State.HardwareRequiredBanner
          props.transferAmountState is AmountBelowBalanceUiState &&
            mobilePayAvailability == FunctionalityFeatureStates.FeatureState.Unavailable ->
            State.F8eUnavailableBanner
          else -> State.NoBanner
        }
      )
    }

    return when (state) {
      is State.AmountEqualOrAboveBalanceBanner -> CardModel(
        title =
          LabelModel.StringWithStyledSubstringModel.from(
            string = "Send Max (balance minus fees)",
            substringToColor =
              mapOf(
                "(balance minus fees)" to ON60
              )
          ),
        subtitle = null,
        leadingImage = null,
        content = null,
        style = Outline,
        onClick = props.onSendMaxClick
      )
      is State.InsufficientFundsBanner -> CardModel(
        title =
          LabelModel.StringWithStyledSubstringModel.from(
            string = "You don't have enough available",
            substringToColor = emptyMap()
          ),
        subtitle = null,
        leadingImage = null,
        content = null,
        style = Outline,
        titleTreatment = CardModel.TitleTreatment.Destructive
      )
      is State.HardwareRequiredBanner -> CardModel(
        title =
          LabelModel.StringWithStyledSubstringModel.from(
            string = "Bitkey approval required",
            substringToColor = emptyMap()
          ),
        subtitle = null,
        leadingImage = CardModel.CardImage.StaticImage(SmallIconBitkey),
        content = null,
        style = Outline
      )
      State.F8eUnavailableBanner -> CardModel(
        title =
          LabelModel.StringWithStyledSubstringModel.from(
            string = "Transfer without hardware unavailable",
            substringToColor = emptyMap()
          ),
        subtitle = null,
        leadingImage = CardModel.CardImage.StaticImage(SmallIconBitkey),
        content = null,
        style = Outline,
        onClick = props.onHardwareRequiredClick
      )
      State.NoBanner -> null
    }
  }
}

private sealed class State {
  data object HardwareRequiredBanner : State()

  data object AmountEqualOrAboveBalanceBanner : State()

  data object InsufficientFundsBanner : State()

  data object F8eUnavailableBanner : State()

  data object NoBanner : State()
}
