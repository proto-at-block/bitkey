package build.wallet.datadog

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject

@BitkeyInject(AppScope::class)
class DatadogRumMonitorImpl : DatadogRumMonitor {
  override fun startView(
    key: String,
    name: String,
    attributes: Map<String, String>,
  ) = Unit

  override fun stopView(
    key: String,
    attributes: Map<String, String>,
  ) = Unit

  override fun startResourceLoading(
    resourceKey: String,
    method: String,
    url: String,
    attributes: Map<String, String>,
  ) = Unit

  override fun stopResourceLoading(
    resourceKey: String,
    kind: ResourceType,
    attributes: Map<String, String>,
  ) = Unit

  override fun stopResourceLoadingError(
    resourceKey: String,
    source: ErrorSource,
    cause: Throwable,
    attributes: Map<String, String>,
  ) = Unit

  override fun addUserAction(
    type: ActionType,
    name: String,
    attributes: Map<String, String>,
  ) = Unit

  override fun addError(
    message: String,
    source: ErrorSource,
    attributes: Map<String, String>,
  ) = Unit
}
