package build.wallet.bitcoin.sync

data class ElectrumServerDetails(
  val host: String,
  val port: String,
  val protocol: String = "ssl",
) {
  /**
   * URL endpoint of Electrum server. It should look something like this: `ssl://somehost.com:50001`
   */
  fun url(): String = "$protocol://$host:$port"
}
