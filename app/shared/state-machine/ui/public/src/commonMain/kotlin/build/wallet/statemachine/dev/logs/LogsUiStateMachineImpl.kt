package build.wallet.statemachine.dev.logs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.logging.LogLevel.Info
import build.wallet.logging.LogLevel.Warn
import build.wallet.logging.dev.LogStore
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.dev.logs.LogsUiStateMachine.Props
import build.wallet.time.DateTimeFormatter
import build.wallet.time.TimeZoneProvider
import kotlinx.collections.immutable.toImmutableList
import kotlinx.datetime.toLocalDateTime

@BitkeyInject(ActivityScope::class)
class LogsUiStateMachineImpl(
  private val dateTimeFormatter: DateTimeFormatter,
  private val logStore: LogStore,
  private val timeZoneProvider: TimeZoneProvider,
) : LogsUiStateMachine {
  @Composable
  override fun model(props: Props): BodyModel {
    var errorsOnly by remember { mutableStateOf(false) }
    var analyticsEventsOnly by remember { mutableStateOf(false) }

    val minimumLevel by remember(errorsOnly) {
      derivedStateOf { if (errorsOnly) Warn else Info }
    }
    val onlyTag by remember(analyticsEventsOnly) {
      derivedStateOf { if (analyticsEventsOnly) "Screen" else null }
    }

    val logs by remember(minimumLevel, analyticsEventsOnly) {
      logStore.logs(minimumLevel, onlyTag)
    }.collectAsState(emptyList())
    val timezone = remember { timeZoneProvider.current() }

    val logsModel by remember(logs, timezone) {
      derivedStateOf {
        LogsModel(
          logRows =
            logs.map {
              LogRowModel(
                dateTime = dateTimeFormatter.localTimestamp(it.time.toLocalDateTime(timezone)),
                level = it.level.name.uppercase(),
                tag = it.tag,
                isError = it.level >= Warn,
                message = it.message,
                throwableDescription = it.throwable?.stackTraceToString()
              )
            }.toImmutableList()
        )
      }
    }

    return LogsBodyModel(
      errorsOnly = errorsOnly,
      analyticsEventsOnly = analyticsEventsOnly,
      onErrorsOnlyValueChanged = {
        errorsOnly = it
      },
      onAnalyticsEventsOnlyValueChanged = {
        analyticsEventsOnly = it
      },
      onClear = {
        logStore.clear()
      },
      logsModel = logsModel,
      onBack = props.onBack
    )
  }
}
