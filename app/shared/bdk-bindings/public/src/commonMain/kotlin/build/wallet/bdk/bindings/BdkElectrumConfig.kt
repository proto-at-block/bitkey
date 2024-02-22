package build.wallet.bdk.bindings

/**
 * https://github.com/bitcoindevkit/bdk-ffi/blob/v0.28.0/bdk-ffi/src/bdk.udl#L123
 * https://bitcoindevkit.org/jvm/bdk-jvm/org.bitcoindevkit/-electrum-config/index.html
 */
data class BdkElectrumConfig(
  val url: String,
  val socks5: String?,
  val retry: Int,
  val timeout: UInt?,
  val stopGap: Int,
  val validateDomain: Boolean,
)
