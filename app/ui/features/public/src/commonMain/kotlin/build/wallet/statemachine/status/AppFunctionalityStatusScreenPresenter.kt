package build.wallet.statemachine.status

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import bitkey.ui.framework.Navigator
import bitkey.ui.framework.Screen
import bitkey.ui.framework.ScreenPresenter
import build.wallet.availability.*
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.time.DateTimeFormatter
import build.wallet.time.TimeZoneProvider
import build.wallet.time.isThisYear
import build.wallet.time.isToday
import kotlinx.datetime.Clock
import kotlinx.datetime.toLocalDateTime

data class AppFunctionalityStatusScreen(
  val originScreen: Screen?,
) : Screen

@BitkeyInject(ActivityScope::class)
class AppFunctionalityStatusScreenPresenter(
  private val appFunctionalityService: AppFunctionalityService,
  private val dateTimeFormatter: DateTimeFormatter,
  private val timeZoneProvider: TimeZoneProvider,
  private val clock: Clock,
) : ScreenPresenter<AppFunctionalityStatusScreen> {
  @Composable
  override fun model(
    navigator: Navigator,
    screen: AppFunctionalityStatusScreen,
  ): ScreenModel {
    val appFunctionalityStatus by remember {
      appFunctionalityService.status
    }.collectAsState()

    val bodyModel = when (val status = appFunctionalityStatus) {
      is AppFunctionalityStatus.LimitedFunctionality -> when (status.cause) {
        is InactiveApp -> InactiveAppInfoBodyModel(
          onClose = {
            screen.originScreen?.let {
              navigator.goTo(it)
            } ?: navigator.exit()
          }
        )
        is F8eUnreachable, is InternetUnreachable, is EmergencyAccessMode -> AppFunctionalityStatusBodyModel(
          status = status,
          cause = status.cause as ConnectivityCause,
          dateFormatter = { date ->
            val dateTime = date.toLocalDateTime(timeZoneProvider.current())
            when {
              date.isToday(clock, timeZoneProvider) -> dateTimeFormatter.localTime(dateTime)
              date.isThisYear(clock, timeZoneProvider) -> dateTimeFormatter.shortDate(dateTime)
              else -> dateTimeFormatter.shortDateWithYear(dateTime)
            }
          },
          onClose = {
            screen.originScreen?.let {
              navigator.goTo(it)
            } ?: navigator.exit()
          }
        )
      }
      is AppFunctionalityStatus.FullFunctionality ->
        error("Unexpected status: $status, screen should not be shown")
    }

    return ScreenModel(
      body = bodyModel,
      presentationStyle = ScreenPresentationStyle.Modal
    )
  }
}
