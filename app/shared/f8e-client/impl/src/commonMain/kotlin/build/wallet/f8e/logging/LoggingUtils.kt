package build.wallet.f8e.logging

import build.wallet.logging.logError
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*

/**
 * Using the [request] data, [logRequest] builds a complete
 * log message with header information and the outgoing body.
 *
 * When [isDebug] is false, only headers with a sanitizer will
 * be included and the [OutgoingContent] is ignored (and null returned),
 * instead logging the original body data model.
 */
internal suspend fun logRequest(
  logger: HttpClientCallLogger,
  request: HttpRequestBuilder,
  isDebug: Boolean,
  headerSanitizers: Map<String, HeaderSanitizer>,
): OutgoingContent? {
  val message = buildString {
    request.attributes.getOrNull(F8eCallDescription)?.let { description ->
      append("DESCRIPTION: ")
      appendLine(description)
    }
    appendLine("REQUEST: ${request.url.build()}")
    appendLine("METHOD: ${request.method.value}")
    appendLine("HEADERS")
    appendHeaders(request.headers.build(), isDebug, headerSanitizers)

    appendLine("EXTRAS")
    request.attributes.getOrNull(F8eCallLogExtras)?.forEach { extra ->
      appendLine(extra)
    }
  }

  logger.logRequest(message)

  return logRequestBody(request, logger, isDebug)
}

internal fun logRequestException(
  tag: String,
  context: HttpRequestBuilder,
  cause: Throwable,
) {
  logError(tag = tag) {
    "REQUEST ${context.url.build()} failed with exception: $cause"
  }
}

/**
 * When [isDebug] is true, copy and log the [OutgoingContent] of
 * [request] to [logger] and return a wrapped [OutgoingContent] to
 * complete the request.
 *
 * When [isDebug] is false, log the original body model stored in
 * the [F8eCallBody] attribute of the request.
 */
private suspend fun logRequestBody(
  request: HttpRequestBuilder,
  logger: HttpClientCallLogger,
  isDebug: Boolean,
): OutgoingContent? {
  val requestLog = StringBuilder()
  requestLog.appendLine("BODY START")
  val outgoingContent = if (isDebug) {
    // This code mirrors ktor-logging to handle processing the body for
    // logging in an efficient way within Ktor coroutine infrastructure.
    val content = request.body as OutgoingContent
    val charset = content.contentType?.charset() ?: Charsets.UTF_8

    @Suppress("DEPRECATION") // pending ktor migration to kotlinx-io
    val channel = ByteChannel()
    GlobalScope.launch(Dispatchers.Unconfined) {
      val text = channel.tryReadText(charset) ?: "[request body omitted]"
      requestLog.appendLine(text)
    }
    content.observe(channel)
  } else {
    val text = when (val body = request.attributes[F8eCallBody]) {
      EmptyContent -> ""
      is TextContent,
      is Array<*>,
      is Iterable<*>,
      -> "[request body omitted]"
      else -> body.toString()
    }
    requestLog.appendLine(text)
    null
  }
  requestLog.append("BODY END")

  logger.logRequest(requestLog.toString())
  logger.closeRequestLog()

  return outgoingContent
}

private fun Appendable.logHeader(
  key: String,
  value: String,
) {
  appendLine("-> $key: $value")
}

internal fun StringBuilder.appendHeaders(
  headers: Headers,
  isDebug: Boolean,
  headerSanitizers: Map<String, HeaderSanitizer>,
) {
  if (isDebug) {
    val sortedHeaders = headers.entries().sortedBy { it.key }

    sortedHeaders.forEach { (key, values) ->
      val formattedLine = values.joinToString("; ")
      logHeader(key, formattedLine)
    }
  } else {
    val sortedHeaders = headerSanitizers.entries.sortedBy { it.key }
    sortedHeaders.forEach { (key, sanitizer) ->
      val values = headers.getAll(key) ?: return@forEach
      val formattedLine = values.joinToString("; ") {
        sanitizer(it)
      }
      logHeader(key, formattedLine)
    }
  }
}

internal fun StringBuilder.appendResponseException(
  request: HttpRequest,
  cause: Throwable,
) {
  append("RESPONSE ${request.url} failed with exception: $cause")
}

internal fun StringBuilder.appendResponseHeader(
  response: HttpResponse,
  debug: Boolean,
  headerSanitizers: Map<String, HeaderSanitizer>,
) {
  response.request.attributes.getOrNull(F8eCallDescription)?.let { description ->
    append("DESCRIPTION: ")
    appendLine(description)
  }
  appendLine("RESPONSE: ${response.status}")
  appendLine("METHOD: ${response.call.request.method.value}")
  appendLine("FROM: ${response.call.request.url}")

  appendLine("HEADERS")
  appendHeaders(response.headers, debug, headerSanitizers)
}

internal fun StringBuilder.appendResponseBody(
  response: HttpResponseContainer,
  context: HttpClientCall,
) {
  appendLine("BODY Content-Type: ${context.response.contentType()}")
  appendLine("BODY START")
  appendLine(response.response)
  append("BODY END")
}

internal suspend fun StringBuilder.appendResponseBodyDebug(
  contentType: ContentType?,
  content: ByteReadChannel,
) {
  appendLine("BODY Content-Type: $contentType")
  appendLine("BODY START")

  val charset = contentType?.charset() ?: Charsets.UTF_8
  val message = content.tryReadText(charset) ?: "[response body omitted]"
  appendLine(message)
  append("BODY END")
}

@Suppress("DEPRECATION") // pending ktor migration to kotlinx-io
private suspend fun OutgoingContent.observe(log: ByteWriteChannel): OutgoingContent =
  when (this) {
    is OutgoingContent.ByteArrayContent -> {
      log.writeFully(bytes())
      log.close()
      this
    }
    is OutgoingContent.ReadChannelContent -> {
      val responseChannel = ByteChannel()
      val content = readFrom()

      content.copyToBoth(log, responseChannel)
      LoggedContent(this, responseChannel)
    }
    is OutgoingContent.WriteChannelContent -> {
      val responseChannel = ByteChannel()
      val content = toReadChannel()
      content.copyToBoth(log, responseChannel)
      LoggedContent(this, responseChannel)
    }
    else -> {
      log.close()
      this
    }
  }

// Originally from the ktor-logging module, this helper converts
// OutgoingContent into a ByteReadChannel to allow logging and
// completing the request.
@OptIn(DelicateCoroutinesApi::class)
private fun OutgoingContent.WriteChannelContent.toReadChannel(): ByteReadChannel =
  GlobalScope.writer(Dispatchers.IO) {
    writeTo(channel)
  }.channel

@Suppress("TooGenericExceptionCaught", "SwallowedException")
internal suspend inline fun ByteReadChannel.tryReadText(charset: Charset): String? =
  try {
    readRemaining().readText(charset = charset)
  } catch (cause: Throwable) {
    null
  }
