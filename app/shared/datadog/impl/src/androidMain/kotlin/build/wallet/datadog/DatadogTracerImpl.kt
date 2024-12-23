package build.wallet.datadog

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import io.opentracing.propagation.Format
import io.opentracing.propagation.TextMapInject
import io.opentracing.util.GlobalTracer

@BitkeyInject(AppScope::class)
class DatadogTracerImpl : DatadogTracer {
  override fun buildSpan(spanName: String): DatadogSpan =
    GlobalTracer.get().buildSpan(spanName)
      .start()
      .let(::DatadogSpanImpl)

  override fun buildSpan(
    spanName: String,
    parentSpan: DatadogSpan,
  ): DatadogSpan {
    require(parentSpan is DatadogSpanImpl)

    return GlobalTracer.get().buildSpan(spanName)
      .asChildOf(parentSpan.span)
      .start()
      .let(::DatadogSpanImpl)
  }

  override fun inject(span: DatadogSpan): TracerHeaders {
    require(span is DatadogSpanImpl)

    val headers = mutableMapOf<String, String>()
    GlobalTracer.get().inject(
      span.span.context(),
      Format.Builtin.TEXT_MAP_INJECT,
      TextMapInject { key, value ->
        headers[key] = value
      }
    )
    return TracerHeaders(headers = headers)
  }
}
