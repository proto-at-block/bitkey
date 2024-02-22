package build.wallet.statemachine.status

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.availability.ConnectivityCause
import build.wallet.availability.EmergencyAccessMode
import build.wallet.availability.F8eUnreachable
import build.wallet.availability.InactiveApp
import build.wallet.availability.InternetUnreachable
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.time.DateTimeFormatter
import build.wallet.time.TimeZoneProvider
import build.wallet.time.isThisYear
import build.wallet.time.isToday
import build.wallet.ui.model.alert.AlertModel
import kotlinx.datetime.Clock
import kotlinx.datetime.toLocalDateTime

class AppFunctionalityStatusUiStateMachineImpl(
  private val dateTimeFormatter: DateTimeFormatter,
  private val timeZoneProvider: TimeZoneProvider,
  private val clock: Clock,
) : AppFunctionalityStatusUiStateMachine {
  @Composable
  override fun model(props: AppFunctionalityStatusUiProps): ScreenModel {
    var alertModel: AlertModel? by remember { mutableStateOf(null) }

    val bodyModel =
      when (props.status.cause) {
        is InactiveApp ->
          InactiveAppInfoBodyModel(
            onClose = props.onClose,
            onSetUpNewWallet = {
              alertModel =
                SetUpNewWalletAlert(
                  onOverwrite = { alertModel = null },
                  onCancel = { alertModel = null }
                )
            }
          )
        is F8eUnreachable, is InternetUnreachable, is EmergencyAccessMode ->
          AppFunctionalityStatusBodyModel(
            status = props.status,
            cause = props.status.cause as ConnectivityCause,
            dateFormatter = { date ->
              val dateTime = date.toLocalDateTime(timeZoneProvider.current())
              when {
                date.isToday(clock, timeZoneProvider) -> dateTimeFormatter.localTime(dateTime)
                date.isThisYear(clock, timeZoneProvider) -> dateTimeFormatter.shortDate(dateTime)
                else -> dateTimeFormatter.shortDateWithYear(dateTime)
              }
            },
            onClose = props.onClose
          )
      }

    return ScreenModel(
      body = bodyModel,
      alertModel = alertModel,
      presentationStyle = ScreenPresentationStyle.Modal
    )
  }
}
