package build.wallet.f8e.client.plugins

import build.wallet.f8e.url
import build.wallet.platform.device.DevicePlatform.Android
import io.ktor.client.plugins.api.*
import io.ktor.http.*
import io.ktor.http.ContentType.Application.Json
import io.ktor.util.*

/**
 * A Ktor Client plugin to apply default request behavior shared across
 * all requests, including configuring the environment URL.
 */
val CommonRequestConfigPlugin = createClientPlugin("common-request-config") {
  onRequest { request, _ ->
    with(request) {
      val deviceInfo = request.attributes[DeviceInfoAttribute]
      val platformInfo = request.attributes[PlatformInfoAttribute]
      val f8eEnvironment = requireNotNull(attributes.getOrNull(F8eEnvironmentAttribute)) {
        "HTTP requests must set an environment with `withEnvironment(f8eEnvironment)`"
      }
      val isAndroidEmulator = deviceInfo.devicePlatform == Android && deviceInfo.isEmulator
      val environmentUrl = Url(f8eEnvironment.url(isAndroidEmulator))
      url {
        host = environmentUrl.host
        port = environmentUrl.port
        protocol = environmentUrl.protocol
      }

      request.headers.append(
        HttpHeaders.UserAgent,
        "${platformInfo.app_id}/${platformInfo.application_version} ${platformInfo.device_make} (${platformInfo.device_model}; ${platformInfo.os_type.name}/${platformInfo.os_version})"
      )

      // We don't want to replace these two headers if already provided.
      headers.appendIfNameAbsent(HttpHeaders.Accept, Json.toString())
      headers.appendIfNameAbsent(HttpHeaders.ContentType, Json.toString())
    }
  }
}
