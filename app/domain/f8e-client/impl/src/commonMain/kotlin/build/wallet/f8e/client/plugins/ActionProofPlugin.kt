package build.wallet.f8e.client.plugins

import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.HttpRequestPipeline
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val ACTION_PROOF_HEADER = "Action-Proof"

private val json = Json {
  encodeDefaults = true
  explicitNulls = false
}

/**
 * A Ktor Client Plugin to add the Action-Proof header for hardware-signed
 * authorization of privileged operations.
 *
 * Header format: `Action-Proof: {"version":1,"signatures":["hex-65-byte-sig"],"nonce":"optional"}`
 */
val ActionProofPlugin = createClientPlugin("action-proof") {
  client.requestPipeline.intercept(HttpRequestPipeline.Before) {
    if (context.attributes.contains(ActionProofAttribute)) {
      val actionProof = context.attributes[ActionProofAttribute]
      val headerValue = json.encodeToString(actionProof)
      context.headers.append(ACTION_PROOF_HEADER, headerValue)
    }
  }
}
