package build.wallet.memfault

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.ktor.result.*
import build.wallet.logging.logDebug
import build.wallet.logging.logFailure
import build.wallet.logging.logNetworkFailure
import build.wallet.memfault.MemfaultClient.*
import build.wallet.memfault.MemfaultProjectKey.MEMFAULT_PROJECT_KEY
import com.github.michaelbull.result.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import okio.ByteString

@BitkeyInject(AppScope::class)
class MemfaultClientImpl(
  private val memfaultHttpClient: MemfaultHttpClient,
) : MemfaultClient {
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
        val url = when (response.status) {
          HttpStatusCode.OK -> response.bodyResult<QueryFwupBundleResponseBody>().get()?.data?.url
          else -> null
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
          post("https://ingress.memfault.com/api/v0/upload") {
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
        }
        .andThen { response ->
          response.bodyResult<PrepareUploadResponse>()
            .mapError { HttpError.NetworkError(it) }
        }
        .logFailure { "Error while uploading coredump" }
        .getOrElse { return Err(it) }

    // 2) Actually upload
    client.catching {
      put(response.data.upload_url) {
        header(HttpHeaders.ContentType, ContentType.Application.OctetStream)
        setUnredactedBody(coredump.toByteArray())
      }
    }
      .logFailure { "Error while uploading coredump" }
      .mapBoth(
        success = {
          logDebug { "Coredump upload response: $it" }
          it
        },
        failure = { Err(it) }
      )

    // 3) Commit upload
    return memfaultHttpClient.client()
      .catching {
        post("https://ingress.memfault.com/api/v0/upload/coredump") {
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
        logDebug { "Committed coredump: $it" }
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
