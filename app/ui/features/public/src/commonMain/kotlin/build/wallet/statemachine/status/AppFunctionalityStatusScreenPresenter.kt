package build.wallet.statemachine.status

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import bitkey.ui.framework.Navigator
import bitkey.ui.framework.Screen
import bitkey.ui.framework.ScreenPresenter
import build.wallet.availability.*
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.statemachine.core.BodyModel
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

    var lastProblematicBodyModel by remember { mutableStateOf<BodyModel?>(null) }

    LaunchedEffect(appFunctionalityStatus, screen.originScreen) {
      if (appFunctionalityStatus is AppFunctionalityStatus.FullFunctionality) {
        screen.originScreen?.let { navigator.goTo(it) } ?: navigator.exit()
      }
    }

    val currentBodyModel = when (val status = appFunctionalityStatus) {
      is AppFunctionalityStatus.LimitedFunctionality -> {
        val specificBodyModel = when (val cause = status.cause) {
          is InactiveApp -> InactiveAppInfoBodyModel(
            onClose = {
              screen.originScreen?.let { navigator.goTo(it) } ?: navigator.exit()
            }
          )
          is F8eUnreachable, is InternetUnreachable, is EmergencyAccessMode -> AppFunctionalityStatusBodyModel(
            status = status,
            cause = cause,
            dateFormatter = { date ->
              val dateTime = date.toLocalDateTime(timeZoneProvider.current())
              when {
                date.isToday(clock, timeZoneProvider) -> dateTimeFormatter.localTime(dateTime)
                date.isThisYear(clock, timeZoneProvider) -> dateTimeFormatter.shortDate(dateTime)
                else -> dateTimeFormatter.shortDateWithYear(dateTime)
              }
            },
            onClose = {
              screen.originScreen?.let { navigator.goTo(it) } ?: navigator.exit()
            }
          )
        }
        lastProblematicBodyModel = specificBodyModel
        specificBodyModel
      }
      is AppFunctionalityStatus.FullFunctionality ->
        lastProblematicBodyModel ?: InactiveAppInfoBodyModel(
          onClose = { screen.originScreen?.let { navigator.goTo(it) } ?: navigator.exit() }
        )
    }

    return ScreenModel(
      body = currentBodyModel,
      presentationStyle = ScreenPresentationStyle.Modal
    )
  }
}
