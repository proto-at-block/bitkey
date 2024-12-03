package build.wallet.f8e.logging

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpClientPlugin
import io.ktor.client.plugins.observer.ResponseHandler
import io.ktor.client.plugins.observer.ResponseObserver
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.HttpRequestPipeline
import io.ktor.client.request.HttpSendPipeline
import io.ktor.client.statement.HttpReceivePipeline
import io.ktor.client.statement.HttpResponsePipeline
import io.ktor.http.contentType
import io.ktor.util.AttributeKey
import io.ktor.util.InternalAPI

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
 * By default, (when [debug] is false) only headers with a [HeaderSanitizer]
 * are logged and request/response bodies are logged in their data class form
 * allowing the use of `@Redacted` on sensitive fields.
 *
 * When [debug] mode is enabled, all original requests headers will be logged
 * and the request/response bodies are logged in their serialized format.
 *
 * To provide additional loggable data from any HTTP call, the `logExtras`
 * helper is available when building the request:
 * ```
 * val myData = ...
 * val response = http.post("/") {
 *   setBody(...)
 *   logExtras(myData)
 * }
 * ```
 */
@Suppress("TooGenericExceptionCaught")
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
      plugin.configureResponseLogging(scope)
      if (plugin.debug) {
        plugin.configureRequestLoggingDebug(scope)
      } else {
        plugin.configureRequestLogging(scope)
      }
    }
  }

  private fun configureRequestLoggingDebug(client: HttpClient) {
    client.sendPipeline.intercept(HttpSendPipeline.Monitoring) {
      val logger = HttpClientCallLogger(tag = tag)
      context.attributes.put(F8eCallLogger, logger)

      val response = try {
        logRequest(logger, context, debug, headerSanitizers)
      } catch (_: Throwable) {
        null
      }

      try {
        proceedWith(response ?: subject)
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
        logRequest(logger, context, debug, headerSanitizers)
      } catch (cause: Throwable) {
        logRequestException(tag, context, cause)
        throw cause
      }
    }
  }

  private fun configureResponseLogging(client: HttpClient) {
    client.receivePipeline.intercept(HttpReceivePipeline.State) { response ->
      val logger = response.call.attributes[F8eCallLogger]
      val header = StringBuilder()

      var failed = false
      try {
        header.appendResponseHeader(response.call.response, debug, headerSanitizers)
        proceed()
      } catch (cause: Throwable) {
        header.appendResponseException(response.call.request, cause)
        failed = true
        throw cause
      } finally {
        logger.logResponseHeader(header.toString())
        if (failed) logger.closeResponseLog()
      }
    }
    client.responsePipeline.intercept(HttpResponsePipeline.Receive) {
      try {
        proceed()
      } catch (cause: Throwable) {
        val logger = context.attributes[F8eCallLogger]
        val log = StringBuilder()
        log.appendResponseException(context.request, cause)
        logger.logResponseException(log.toString(), cause)
        logger.closeResponseLog()
        throw cause
      }
    }

    if (debug) {
      val observer: ResponseHandler = observer@{
        val logger = it.call.attributes[F8eCallLogger]
        val log = StringBuilder()
        @OptIn(InternalAPI::class)
        try {
          log.appendResponseBodyDebug(it.contentType(), it.content)
        } catch (_: Throwable) {
        } finally {
          logger.logResponseBody(log.toString().trim())
          logger.closeResponseLog()
        }
      }

      ResponseObserver.install(ResponseObserver(observer), client)
    } else {
      client.responsePipeline.intercept(HttpResponsePipeline.After) {
        val logger = context.attributes[F8eCallLogger]
        val log = StringBuilder()
        try {
          log.appendResponseBody(subject, context)
        } catch (_: Throwable) {
        } finally {
          logger.logResponseBody(log.toString().trim())
          logger.closeResponseLog()
        }
      }
    }
  }
}
