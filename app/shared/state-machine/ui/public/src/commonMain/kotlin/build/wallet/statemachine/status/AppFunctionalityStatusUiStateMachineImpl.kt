package build.wallet.statemachine.status

import androidx.compose.runtime.Composable
import build.wallet.availability.ConnectivityCause
import build.wallet.availability.EmergencyAccessMode
import build.wallet.availability.F8eUnreachable
import build.wallet.availability.InactiveApp
import build.wallet.availability.InternetUnreachable
import build.wallet.feature.flags.MobilePayRevampFeatureFlag
import build.wallet.feature.isEnabled
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.time.DateTimeFormatter
import build.wallet.time.TimeZoneProvider
import build.wallet.time.isThisYear
import build.wallet.time.isToday
import kotlinx.datetime.Clock
import kotlinx.datetime.toLocalDateTime

class AppFunctionalityStatusUiStateMachineImpl(
  private val dateTimeFormatter: DateTimeFormatter,
  private val timeZoneProvider: TimeZoneProvider,
  private val clock: Clock,
  private val mobilePayRevampFeatureFlag: MobilePayRevampFeatureFlag,
) : AppFunctionalityStatusUiStateMachine {
  @Composable
  override fun model(props: AppFunctionalityStatusUiProps): ScreenModel {
    val bodyModel =
      when (props.status.cause) {
        is InactiveApp ->
          InactiveAppInfoBodyModel(
            onClose = props.onClose
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
            isRevampOn = mobilePayRevampFeatureFlag.isEnabled(),
            onClose = props.onClose
          )
      }

    return ScreenModel(
      body = bodyModel,
      presentationStyle = ScreenPresentationStyle.Modal
    )
  }
}
