package bitkey.datadog

import app.cash.turbine.Turbine
import app.cash.turbine.plusAssign

class DatadogRumMonitorFake(
  turbine: (name: String) -> Turbine<Any>,
) : DatadogRumMonitor {
  override fun startView(
    key: String,
    name: String,
    attributes: Map<String, String>,
  ) = Unit

  override fun stopView(
    key: String,
    attributes: Map<String, String>,
  ) = Unit

  val startResourceLoadingCalls = turbine("RUM start resource loading calls")

  override fun startResourceLoading(
    resourceKey: String,
    method: String,
    url: String,
    attributes: Map<String, String>,
  ) {
    startResourceLoadingCalls += resourceKey
  }

  val stopResourceLoadingCalls = turbine("RUM stop resource loading calls")

  override fun stopResourceLoading(
    resourceKey: String,
    kind: ResourceType,
    attributes: Map<String, String>,
  ) {
    stopResourceLoadingCalls += resourceKey
  }

  val stopResourceLoadingErrorCalls = turbine("RUM stop resource with error loading calls")

  override fun stopResourceLoadingError(
    resourceKey: String,
    source: ErrorSource,
    cause: Throwable,
    attributes: Map<String, String>,
  ) {
    stopResourceLoadingErrorCalls += resourceKey
  }

  val addUserActionCalls = turbine("RUM add user action calls")

  override fun addUserAction(
    type: ActionType,
    name: String,
    attributes: Map<String, String>,
  ) {
    addUserActionCalls += Triple(type, name, attributes)
  }

  val addErrorCalls = turbine("RUM add error calls")

  override fun addError(
    message: String,
    source: ErrorSource,
    attributes: Map<String, String>,
  ) {
    addErrorCalls += message
  }
}
