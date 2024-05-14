package build.wallet.statemachine.partnerships.expected

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.analytics.events.screen.id.ExpectedTransactionTrackerScreenId.EXPECTED_TRANSACTION_NOTICE_LOADING
import build.wallet.f8e.partnerships.GetTransferPartnerListService
import build.wallet.logging.logFailure
import build.wallet.partnerships.PartnerInfo
import build.wallet.statemachine.core.LoadingBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.time.DateTimeFormatter
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess

class ExpectedTransactionNoticeUiStateMachineImpl(
  private val getTransferPartnerListService: GetTransferPartnerListService,
  private val dateTimeFormatter: DateTimeFormatter,
) : ExpectedTransactionNoticeUiStateMachine {
  @Composable
  override fun model(props: ExpectedTransactionNoticeProps): ScreenModel {
    var state by remember { mutableStateOf<State>(State.LoadingPartnershipDetails) }

    when (state) {
      // W-8015: Update endpoint used for fetching partner info
      is State.LoadingPartnershipDetails -> LaunchedEffect("load partnership details") {
        getTransferPartnerListService.getTransferPartners(props.fullAccountId, props.f8eEnvironment)
          .onSuccess {
            state = State.TransactionDetails(
              partnerInfo = it.partnerList.find { it.partner == props.partner?.value }
            )
          }
          .logFailure { "Unable to fetch partner list for expected transaction screen" }
          .onFailure {
            state = State.TransactionDetails(null)
          }
      }
      else -> {}
    }

    return when (val currentState = state) {
      is State.LoadingPartnershipDetails -> LoadingBodyModel(
        id = EXPECTED_TRANSACTION_NOTICE_LOADING
      )
      is State.TransactionDetails -> ExpectedTransactionNoticeModel(
        partnerInfo = currentState.partnerInfo,
        transactionDate = dateTimeFormatter.shortDateWithTime(props.receiveTime),
        onViewInPartnerApp = props.onViewInPartnerApp,
        onBack = props.onBack
      )
    }.asModalFullScreen()
  }

  private sealed interface State {
    data object LoadingPartnershipDetails : State

    data class TransactionDetails(val partnerInfo: PartnerInfo?) : State
  }
}
