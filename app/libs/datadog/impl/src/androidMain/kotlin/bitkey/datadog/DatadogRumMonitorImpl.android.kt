package bitkey.datadog

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import com.datadog.android.rum.GlobalRumMonitor

@BitkeyInject(AppScope::class)
class DatadogRumMonitorImpl : DatadogRumMonitor {
  override fun startView(
    key: String,
    name: String,
    attributes: Map<String, String>,
  ) {
    GlobalRumMonitor.get()
      .startView(key, name, attributes)
  }

  override fun stopView(
    key: String,
    attributes: Map<String, String>,
  ) {
    GlobalRumMonitor.get()
      .stopView(key, attributes)
  }

  override fun startResourceLoading(
    resourceKey: String,
    method: String,
    url: String,
    attributes: Map<String, String>,
  ) {
    GlobalRumMonitor.get()
      .startResource(
        key = resourceKey,
        method,
        url,
        attributes
      )
  }

  override fun stopResourceLoading(
    resourceKey: String,
    kind: ResourceType,
    attributes: Map<String, String>,
  ) {
    GlobalRumMonitor.get()
      .stopResource(
        key = resourceKey,
        statusCode = null,
        size = null,
        kind = kind.rumResourceKind,
        attributes
      )
  }

  override fun stopResourceLoadingError(
    resourceKey: String,
    source: ErrorSource,
    cause: Throwable,
    attributes: Map<String, String>,
  ) {
    GlobalRumMonitor.get()
      .stopResourceWithError(
        key = resourceKey,
        statusCode = null,
        message = cause.toString(),
        source = source.rumErrorSource,
        throwable = cause,
        attributes
      )
  }

  override fun addUserAction(
    type: ActionType,
    name: String,
    attributes: Map<String, String>,
  ) {
    GlobalRumMonitor.get()
      .addAction(
        type = type.rumActionType,
        name,
        attributes
      )
  }

  override fun addError(
    message: String,
    source: ErrorSource,
    attributes: Map<String, String>,
  ) {
    GlobalRumMonitor.get()
      .addError(
        message = message,
        source = source.rumErrorSource,
        throwable = null,
        attributes
      )
  }
}
