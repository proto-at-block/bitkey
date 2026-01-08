package build.wallet.statemachine.status

import androidx.compose.runtime.*
import bitkey.recovery.fundslost.AtRiskCause
import bitkey.recovery.fundslost.FundsLostRiskLevel
import bitkey.recovery.fundslost.FundsLostRiskService
import build.wallet.analytics.events.EventTracker
import build.wallet.analytics.v1.Action
import build.wallet.availability.*
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.statemachine.status.BannerType.*
import build.wallet.statemachine.status.BannerType.OfflineStatus
import build.wallet.time.DateTimeFormatter
import build.wallet.time.TimeZoneProvider
import build.wallet.time.isThisYear
import build.wallet.time.isToday
import build.wallet.ui.model.status.BannerStyle
import build.wallet.ui.model.status.StatusBannerModel
import kotlinx.datetime.Clock
import kotlinx.datetime.toLocalDateTime

@BitkeyInject(ActivityScope::class)
class HomeStatusBannerUiStateMachineImpl(
  private val appFunctionalityService: AppFunctionalityService,
  private val dateTimeFormatter: DateTimeFormatter,
  private val timeZoneProvider: TimeZoneProvider,
  private val clock: Clock,
  private val eventTracker: EventTracker,
  private val fundsLostRiskService: FundsLostRiskService,
) : HomeStatusBannerUiStateMachine {
  @Composable
  override fun model(props: HomeStatusBannerUiProps): StatusBannerModel? {
    val appFunctionalityStatus = remember { appFunctionalityService.status }.collectAsState().value
    val fundsLostRisk by remember { fundsLostRiskService.riskLevel() }.collectAsState()

    return when (appFunctionalityStatus) {
      is AppFunctionalityStatus.FullFunctionality -> if (
        fundsLostRisk is FundsLostRiskLevel.AtRisk && props.bannerContext != BannerContext.SecurityHub
      ) {
        val subtitle = when ((fundsLostRisk as FundsLostRiskLevel.AtRisk).cause) {
          AtRiskCause.MissingHardware -> "Add a Bitkey device to avoid losing funds →"
          is AtRiskCause.MissingCloudBackup -> "Add a cloud backup to protect your funds →"
          AtRiskCause.MissingContactMethod -> "Add a contact method to protect your funds →"
          AtRiskCause.ActiveSpendingKeysetMismatch -> "Fix your local data to protect your funds →"
        }

        val bannerType = when (val cause = (fundsLostRisk as FundsLostRiskLevel.AtRisk).cause) {
          AtRiskCause.MissingHardware -> MissingHardware
          AtRiskCause.MissingContactMethod -> MissingCommunication
          is AtRiskCause.MissingCloudBackup -> MissingCloudBackup(
            problemWithBackup = cause.problem
          )
          AtRiskCause.ActiveSpendingKeysetMismatch -> SpendingKeysetMismatch
        }

        StatusBannerModel(
          title = "Your wallet is at risk",
          subtitle = subtitle,
          style = BannerStyle.Destructive,
          onClick = { props.onBannerClick?.invoke(bannerType) }
        )
      } else {
        null
      }

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
              InactiveApp, EmergencyExitMode -> "Limited Functionality"
            },
          subtitle =
            when (val cause = appFunctionalityStatus.cause) {
              is F8eUnreachable -> "Some features may not be available"
              is EmergencyExitMode -> "Emergency Exit Mode"
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
          style = BannerStyle.Warning,
          onClick =
            when (val handler = props.onBannerClick) {
              null -> null
              else -> {
                { handler(OfflineStatus) }
              }
            }
        )
      }
    }
  }
}
