package build.wallet.statemachine.status

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import build.wallet.analytics.events.EventTracker
import build.wallet.analytics.v1.Action
import build.wallet.availability.AppFunctionalityStatus
import build.wallet.availability.AppFunctionalityStatusProvider
import build.wallet.availability.EmergencyAccessMode
import build.wallet.availability.F8eUnreachable
import build.wallet.availability.InactiveApp
import build.wallet.availability.InternetUnreachable
import build.wallet.time.DateTimeFormatter
import build.wallet.time.TimeZoneProvider
import build.wallet.time.isThisYear
import build.wallet.time.isToday
import build.wallet.ui.model.status.StatusBannerModel
import kotlinx.datetime.Clock
import kotlinx.datetime.toLocalDateTime

class HomeStatusBannerUiStateMachineImpl(
  private val appFunctionalityStatusProvider: AppFunctionalityStatusProvider,
  private val dateTimeFormatter: DateTimeFormatter,
  private val timeZoneProvider: TimeZoneProvider,
  private val clock: Clock,
  private val eventTracker: EventTracker,
) : HomeStatusBannerUiStateMachine {
  @Composable
  override fun model(props: HomeStatusBannerUiProps): StatusBannerModel? {
    val appFunctionalityStatus =
      remember {
        appFunctionalityStatusProvider.appFunctionalityStatus(props.f8eEnvironment)
      }.collectAsState(AppFunctionalityStatus.FullFunctionality).value

    return when (appFunctionalityStatus) {
      is AppFunctionalityStatus.FullFunctionality ->
        null

      is AppFunctionalityStatus.LimitedFunctionality -> {
        LaunchedEffect("log-inactive-app-event") {
          if (appFunctionalityStatus.cause is InactiveApp) {
            eventTracker.track(Action.ACTION_APP_BECAME_INACTIVE)
          }
        }
        StatusBannerModel(
          title =
            when (appFunctionalityStatus.cause) {
              is F8eUnreachable -> "Unable to reach Bitkey services"
              is InternetUnreachable -> "Offline"
              InactiveApp, EmergencyAccessMode -> "Limited Functionality"
            },
          subtitle =
            when (val cause = appFunctionalityStatus.cause) {
              is F8eUnreachable -> "Some features may not be available"
              is EmergencyAccessMode -> "Emergency Access Kit"
              is InternetUnreachable -> {
                cause.lastElectrumSyncReachableTime
                  ?.let { date ->
                    val dateTime = date.toLocalDateTime(timeZoneProvider.current())
                    when {
                      date.isToday(clock, timeZoneProvider) -> dateTimeFormatter.localTime(dateTime)
                      date.isThisYear(
                        clock,
                        timeZoneProvider
                      ) -> dateTimeFormatter.shortDate(dateTime)
                      else -> dateTimeFormatter.shortDateWithYear(dateTime)
                    }
                  }
                  ?.let { "Balance last updated $it" }
              }
              InactiveApp -> "Your wallet is active on another phone"
            },
          onClick =
            when (val handler = props.onBannerClick) {
              null -> null
              else -> {
                { handler(appFunctionalityStatus) }
              }
            }
        )
      }
    }
  }
}
