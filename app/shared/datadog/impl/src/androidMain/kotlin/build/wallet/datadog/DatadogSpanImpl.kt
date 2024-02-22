package build.wallet.datadog

import com.datadog.trace.api.interceptor.MutableSpan
import io.opentracing.Span

data class DatadogSpanImpl(
  val span: Span,
) : DatadogSpan {
  override var resourceName: String? = null
    set(value) {
      (span as? MutableSpan)?.resourceName = value
      field = value
    }

  override fun setTag(
    key: String,
    value: String,
  ) {
    span.setTag(key, value)
  }

  override fun finish() {
    span.finish()
  }

  override fun finish(cause: Throwable) {
    (span as? MutableSpan)?.setError(true)
    setTag("error.type", cause::class.simpleName.orEmpty())
    setTag("error.msg", cause.message.orEmpty())
    setTag("error.stack", cause.stackTraceToString())
    span.finish()
  }
}
