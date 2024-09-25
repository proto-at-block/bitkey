package build.wallet.f8e.client.plugins

import build.wallet.bitkey.f8e.AccountId
import build.wallet.datadog.DatadogSpan
import build.wallet.datadog.DatadogTracer
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.http.encodedPath
import io.ktor.util.AttributeKey

const val SPAN_NAME = "client.request"

class DatadogTracerPluginConfig {
  lateinit var datadogTracer: DatadogTracer
}

/**
 * A Ktor Client Plugin to record request/response data via [DatadogTracer].
 */
val DatadogTracerPlugin = createClientPlugin(
  "DatadogTracerPlugin",
  ::DatadogTracerPluginConfig
) {
  val datadogTracer = pluginConfig.datadogTracer
  val spanKey = AttributeKey<DatadogSpan>("spanKey")

  onRequest { request, _ ->
    val accountId = request.attributes.getOrNull(AccountIdAttribute)
    val span =
      datadogTracer.buildSpan(SPAN_NAME).apply {
        setTag("http.method", request.method.value)
        setTag("http.url", filterAccountId(request.url.toString(), accountId))
        resourceName = filterAccountId(request.url.encodedPath, accountId)
      }
    datadogTracer.inject(span)
      .headers
      .entries
      .map { entry ->
        request.headers.append(entry.key, entry.value)
      }
    request.attributes.put(spanKey, span)
  }
  onResponse { response ->
    response.call.attributes[spanKey].apply {
      setTag("http.status_code", response.status.value.toString())
      setTag("http.version", response.version.toString())
      finish()
    }
  }
}

private fun filterAccountId(
  url: String,
  accountId: AccountId?,
) = accountId?.let { url.replace(accountId.serverId, ":account_id") } ?: url
