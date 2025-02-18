package build.wallet.f8e.logging

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.observer.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.*
import kotlinx.coroutines.CancellationException

internal val F8eCallLogger = AttributeKey<HttpClientCallLogger>("F8eCallLogger")
internal val F8eCallLogExtras = AttributeKey<Array<out Any>>("F8eCallLogExtras")
internal val F8eCallBody = AttributeKey<Any>("F8eCallLogBody")
internal val F8eCallDescription = AttributeKey<String>("F8eCallDescription")

fun HttpRequestBuilder.withExtras(vararg extras: Any) {
  attributes.put(F8eCallLogExtras, extras)
}

fun HttpRequestBuilder.withDescription(description: String) {
  attributes.put(F8eCallDescription, description)
}

internal typealias HeaderSanitizer = (String) -> String

/**
 * [F8eHttpClientLogger] is a Ktor Client plugin that allows logging of
 * sanitized HTTP message data, suitable for production.
 *
 * By default, (when [debug] is false) only a minimal summary of each request and response is logged,
 * and request/response headers or bodies are not included.
 *
 * When [debug] mode is enabled, all original request headers will be logged
 * and the request/response bodies are logged in their serialized format,
 * allowing the use of `@Redacted` on sensitive fields.
 *
 * To provide additional loggable data from any HTTP call, the `withExtras`
 * helper is available when building the request:
 * ```
 * val myData = ...
 * val response = http.post("/") {
 *   setBody(...)
 *   withExtras(myData)
 * }
 * ```
 */
@Suppress("TooGenericExceptionCaught", "ThrowsCount")
class F8eHttpClientLogger private constructor(
  val tag: String,
  val debug: Boolean,
  val headerSanitizers: Map<String, HeaderSanitizer>,
) {
  class Config {
    /** The logging tag for HTTP message logs */
    var tag: String = "F8eServiceLogger"

    /** When enabled, log the full HTTP message without redaction. */
    var debug: Boolean = false

    internal val headerSanitizers = mutableMapOf<String, HeaderSanitizer>()

    /** Transform the [header] value with [sanitizer] to be included in HTTP message logs. */
    fun sanitize(
      header: String,
      sanitizer: (String) -> String,
    ) {
      headerSanitizers[header] = sanitizer
    }
  }

  companion object : HttpClientPlugin<Config, F8eHttpClientLogger> {
    override val key: AttributeKey<F8eHttpClientLogger> = AttributeKey("KtorServiceLogger")

    override fun prepare(block: Config.() -> Unit): F8eHttpClientLogger {
      val config = Config().apply(block)
      return F8eHttpClientLogger(
        tag = config.tag,
        debug = config.debug,
        headerSanitizers = config.headerSanitizers.toMap()
      )
    }

    override fun install(
      plugin: F8eHttpClientLogger,
      scope: HttpClient,
    ) {
      if (plugin.debug) {
        plugin.configureRequestLoggingDebug(scope)
        plugin.configureResponseLoggingDebug(scope)
      } else {
        plugin.configureRequestLogging(scope)
        plugin.configureResponseLogging(scope)
      }
    }
  }

  private fun configureRequestLoggingDebug(client: HttpClient) {
    client.sendPipeline.intercept(HttpSendPipeline.Monitoring) {
      val logger = HttpClientCallLogger(tag = tag)
      context.attributes.put(F8eCallLogger, logger)

      val response = try {
        logRequest(logger, context, debug, headerSanitizers)
      } catch (e: CancellationException) {
        // Cancellations are expected, rethrow to ensure structured cancellation.
        throw e
      } catch (_: Throwable) {
        null
      }

      try {
        proceedWith(response ?: subject)
      } catch (e: CancellationException) {
        // Cancellations are expected, rethrow to ensure structured cancellation.
        throw e
      } catch (cause: Throwable) {
        logRequestException(tag, context, cause)
        throw cause
      }
    }
  }

  private fun configureRequestLogging(client: HttpClient) {
    client.requestPipeline.intercept(HttpRequestPipeline.Before) {
      val logger = HttpClientCallLogger(tag = tag)
      context.attributes.put(F8eCallLogger, logger)

      try {
        proceed()
      } catch (cause: CancellationException) {
        // Cancellations are expected, rethrow to ensure structured cancellation.
        throw cause
      } catch (cause: Throwable) {
        logRequestException(tag, context, cause)
        throw cause
      }
    }
    client.requestPipeline.intercept(HttpRequestPipeline.Transform) {
      context.attributes.put(F8eCallBody, context.body)
    }
    client.sendPipeline.intercept(HttpSendPipeline.Monitoring) {
      val logger = context.attributes[F8eCallLogger]
      try {
        val description = context.attributes.getOrNull(F8eCallDescription)
        val message = buildString {
          append("REQUEST: ")
          if (description != null) {
            append("$description - ")
          }
          append("${context.method.value} ${context.url.encodedPath}")
        }
        logger.logRequest(message)
        logger.closeRequestLog()
        proceed()
      } catch (e: CancellationException) {
        // Cancellations are expected, rethrow to ensure structured cancellation.
        throw e
      } catch (cause: Throwable) {
        logRequestException(tag, context, cause)
        throw cause
      }
    }
  }

  private fun configureResponseLogging(client: HttpClient) {
    client.receivePipeline.intercept(HttpReceivePipeline.State) { response ->
      val logger = response.call.attributes[F8eCallLogger]
      val description = response.call.request.attributes.getOrNull(F8eCallDescription)
      val message = buildString {
        append("RESPONSE: ")
        if (description != null) {
          append("$description - ")
        }
        append("${response.call.request.method.value} ${response.call.request.url.encodedPath}")
        append(" - ${response.status.value}")
      }
      logger.logResponseHeader(message)
      try {
        proceed()
      } catch (e: CancellationException) {
        // Cancellations are expected, rethrow to ensure structured cancellation.
        throw e
      } catch (cause: Throwable) {
        logger.logResponseException(
          "REQUEST: ${response.call.request.method} ${response.call.request.url.encodedPath} failed with error: $cause",
          cause
        )
        throw cause
      } finally {
        logger.closeResponseLog()
      }
    }

    installResponseReceiveExceptionInterceptor(client)
  }

  private fun configureResponseLoggingDebug(client: HttpClient) {
    client.receivePipeline.intercept(HttpReceivePipeline.State) { response ->
      val logger = response.call.attributes[F8eCallLogger]
      val header = StringBuilder()

      var failed = false
      try {
        header.appendResponseHeader(response.call.response, debug, headerSanitizers)
        proceed()
      } catch (e: CancellationException) {
        // Cancellations are expected, rethrow to ensure structured cancellation.
        throw e
      } catch (cause: Throwable) {
        header.appendResponseException(response.call.request, cause)
        failed = true
        throw cause
      } finally {
        logger.logResponseHeader(header.toString())
        if (failed) logger.closeResponseLog()
      }
    }

    installResponseReceiveExceptionInterceptor(client)

    val observer: ResponseHandler = observer@{
      val logger = it.call.attributes[F8eCallLogger]
      val log = StringBuilder()
      @OptIn(InternalAPI::class)
      try {
        log.appendResponseBodyDebug(it.contentType(), it.content)
      } catch (e: CancellationException) {
        // Cancellations are expected, rethrow to ensure structured cancellation.
        throw e
      } catch (_: Throwable) {
        // Even if we fail to parse the content, we still want to log and close
      } finally {
        logger.logResponseBody(log.toString().trim())
        logger.closeResponseLog()
      }
    }
    ResponseObserver.install(ResponseObserver(observer), client)
  }

  private fun installResponseReceiveExceptionInterceptor(client: HttpClient) {
    client.responsePipeline.intercept(HttpResponsePipeline.Receive) {
      try {
        proceed()
      } catch (e: CancellationException) {
        // Cancellations are expected, rethrow to ensure structured cancellation.
        throw e
      } catch (cause: Throwable) {
        val logger = context.attributes[F8eCallLogger]
        val log = StringBuilder()
        log.appendResponseException(context.request, cause)
        logger.logResponseException(log.toString(), cause)
        logger.closeResponseLog()
        throw cause
      }
    }
  }
}
