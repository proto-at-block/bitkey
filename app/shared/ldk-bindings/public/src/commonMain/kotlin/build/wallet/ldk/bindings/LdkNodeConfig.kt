package build.wallet.ldk.bindings

data class LdkNodeConfig(
  val storagePath: String,
  val esploraServerUrl: String,
  val network: Network,
  val listeningAddress: String?,
  val defaultCltvExpiryDelta: Long,
)
