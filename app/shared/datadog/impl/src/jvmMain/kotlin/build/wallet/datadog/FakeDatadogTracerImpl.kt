package build.wallet.datadog

class FakeDatadogTracerImpl : DatadogTracer {
  override fun buildSpan(spanName: String) = stubSpan()

  override fun buildSpan(
    spanName: String,
    parentSpan: DatadogSpan,
  ) = stubSpan()

  override fun inject(span: DatadogSpan): TracerHeaders {
    return TracerHeaders()
  }

  private fun stubSpan() =
    object : DatadogSpan {
      override var resourceName: String? = ""

      override fun setTag(
        key: String,
        value: String,
      ) = Unit

      override fun finish() = Unit

      override fun finish(cause: Throwable) = Unit
    }
}
