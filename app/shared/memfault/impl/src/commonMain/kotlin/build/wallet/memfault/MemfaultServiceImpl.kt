package build.wallet.memfault

import build.wallet.ktor.result.HttpError
import build.wallet.ktor.result.catching
import build.wallet.ktor.result.readByteString
import build.wallet.ktor.result.setUnredactedBody
import build.wallet.logging.log
import build.wallet.logging.logNetworkFailure
import build.wallet.memfault.MemfaultProjectKey.Companion.MEMFAULT_PROJECT_KEY
import build.wallet.memfault.MemfaultService.DownloadFwupBundleSuccess
import build.wallet.memfault.MemfaultService.QueryFwupBundleSuccess
import build.wallet.memfault.MemfaultService.UploadTelemetryEventSuccess
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.map
import com.github.michaelbull.result.orElse
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.http.contentType
import io.ktor.http.encodedPath
import io.ktor.http.fullPath
import io.ktor.http.path
import kotlinx.serialization.Serializable
import okio.ByteString

class MemfaultServiceImpl(
  private val memfaultHttpClient: MemfaultHttpClient,
) : MemfaultService {
  override suspend fun queryForFwupBundle(
    deviceSerial: String,
    hardwareVersion: String,
    softwareType: String,
    currentVersion: String,
  ): Result<QueryFwupBundleSuccess, HttpError> {
    return memfaultHttpClient.client()
      .catching {
        get {
          url {
            protocol = URLProtocol.HTTPS
            host = "device.memfault.com"
            path("api/v0/releases/latest/url")
            parameters.append("device_serial", deviceSerial)
            parameters.append("hardware_version", hardwareVersion)
            parameters.append("software_type", softwareType)
            parameters.append("current_version", currentVersion)
          }
          header("Memfault-Project-Key", MEMFAULT_PROJECT_KEY)
        }
      }
      .map { response ->
        val url =
          when (response.status) {
            HttpStatusCode.NoContent -> null
            else -> (response.body<QueryFwupBundleResponseBody>()).data.url
          }
        QueryFwupBundleSuccess(url)
      }
  }

  override suspend fun downloadFwupBundle(
    url: String,
  ): Result<DownloadFwupBundleSuccess, HttpError> {
    val downloadUrl = Url(url)
    return memfaultHttpClient.client()
      .catching {
        get {
          url {
            protocol = downloadUrl.protocol
            host = downloadUrl.host
            encodedPath = downloadUrl.fullPath
            contentType(ContentType.Application.OctetStream)
            accept(ContentType.Application.OctetStream)
          }
        }
      }
      .map { response ->
        DownloadFwupBundleSuccess(response.readByteString())
      }
  }

  override suspend fun uploadTelemetryEvent(
    chunk: ByteArray,
    deviceSerial: String,
  ): Result<UploadTelemetryEventSuccess, HttpError> {
    return memfaultHttpClient.client()
      .catching {
        post("https://chunks.memfault.com/api/v0/chunks/$deviceSerial") {
          header("Memfault-Project-Key", MEMFAULT_PROJECT_KEY)
          header(HttpHeaders.ContentType, ContentType.Application.OctetStream)
          setUnredactedBody(chunk)
        }
      }.map {
        UploadTelemetryEventSuccess
      }.logNetworkFailure { "Error while uploading telemetry" }
  }

  override suspend fun uploadCoredump(
    coredump: ByteString,
    deviceSerial: String,
    hardwareVersion: String,
    softwareType: String,
    softwareVersion: String,
  ): Result<Unit, HttpError> {
    val client = memfaultHttpClient.client()

    // 1) Prepare upload
    val response =
      client
        .catching {
          post("https://files.memfault.com/api/v0/upload") {
            header("Memfault-Project-Key", MEMFAULT_PROJECT_KEY)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setUnredactedBody(
              UploadCoredumpRequestBody(
                kind = "COREDUMP",
                device =
                  MemfaultDevice(
                    device_serial = deviceSerial,
                    hardware_version = hardwareVersion,
                    software_version = softwareVersion,
                    software_type = "$hardwareVersion-$softwareType" // e.g. evt-app-a-dev
                  ),
                size = coredump.size
              )
            )
          }
        }.map {
          it.body<PrepareUploadResponse>()
        }.getOrElse { error ->
          log { "Error while preparing coredump: $error" }
          return Err(error)
        }

    // 2) Actually upload
    client.catching {
      put(response.data.upload_url) {
        header(HttpHeaders.ContentType, ContentType.Application.OctetStream)
        setUnredactedBody(coredump.toByteArray())
      }
    }.map {
      log { "Coredump upload response: $it" }
    }.orElse {
      log { "Error while uploading coredump: $it" }
      return Err(it)
    }

    // 3) Commit upload
    return memfaultHttpClient.client()
      .catching {
        post("https://files.memfault.com/api/v0/upload/coredump") {
          header("Memfault-Project-Key", MEMFAULT_PROJECT_KEY)
          header(HttpHeaders.ContentType, ContentType.Application.Json)
          setUnredactedBody(
            CommitUploadRequestBody(
              file =
                UploadFile(
                  token = response.data.token
                )
            )
          )
        }
      }.map {
        log { "Committed coredump: $it" }
      }.logNetworkFailure { "Error while committing coredump upload" }
  }
}

@Serializable
private data class QueryFwupBundleResponseBody(
  val data: QueryFwupBundleDataBody,
)

@Serializable
private data class QueryFwupBundleDataBody(
  val url: String,
)

@Serializable
@Suppress("ConstructorParameterNaming")
private data class MemfaultDevice(
  val device_serial: String,
  val hardware_version: String,
  val software_version: String,
  val software_type: String,
)

@Serializable
private data class UploadCoredumpRequestBody(
  val kind: String,
  val device: MemfaultDevice,
  val size: Int,
)

@Serializable
private data class UploadFile(
  val token: String,
)

@Serializable
private data class CommitUploadRequestBody(
  val file: UploadFile,
)

@Serializable
@Suppress("ConstructorParameterNaming")
private data class PrepareUploadResponseBody(
  val token: String,
  val upload_url: String,
)

@Serializable
private data class PrepareUploadResponse(
  val data: PrepareUploadResponseBody,
)
