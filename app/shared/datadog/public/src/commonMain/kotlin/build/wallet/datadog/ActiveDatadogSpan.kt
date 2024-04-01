package build.wallet.datadog

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlin.contracts.InvocationKind.EXACTLY_ONCE
import kotlin.contracts.contract
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

class ActiveDatadogSpan(val span: DatadogSpan) : AbstractCoroutineContextElement(Key) {
  companion object Key : CoroutineContext.Key<ActiveDatadogSpan>
}

@Suppress("TooGenericExceptionCaught")
suspend fun <T> DatadogTracer.span(
  spanName: String,
  resourceName: String? = null,
  block: suspend CoroutineScope.(DatadogSpan) -> T,
): T {
  contract { callsInPlace(block, EXACTLY_ONCE) }

  val activeSpan =
    when (val parentSpan = currentCoroutineContext()[ActiveDatadogSpan]) {
      null -> buildSpan(spanName)
      else -> buildSpan(spanName, parentSpan.span)
    }
  activeSpan.resourceName = resourceName

  return try {
    withContext(ActiveDatadogSpan(activeSpan)) { block(activeSpan) }
  } catch (e: Throwable) {
    activeSpan.finish(e)
    throw e
  } finally {
    activeSpan.finish()
  }
}
