package bitkey.datadog

import com.datadog.opentracing.DDSpan
import io.opentracing.Span

internal data class DatadogSpanImpl(
  val span: Span,
) : DatadogSpan {
  override var resourceName: String? = null
    set(value) {
      (span as DDSpan).resourceName = value
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
    (span as DDSpan).setError(true)
    setTag("error.type", cause::class.simpleName.orEmpty())
    setTag("error.msg", cause.message.orEmpty())
    setTag("error.stack", cause.stackTraceToString())
    span.finish()
  }
}
