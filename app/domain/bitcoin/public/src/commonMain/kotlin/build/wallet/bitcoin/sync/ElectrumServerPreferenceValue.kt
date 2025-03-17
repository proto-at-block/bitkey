package build.wallet.bitcoin.sync

/**
 * An interface that uses `On` or `Off` variants to indicate whether or not a user has
 * set a custom server.
 */
sealed class ElectrumServerPreferenceValue {
  /**
   * Indicates that the user is using a Block-provided Electrum server.
   *
   * @property previousUserDefinedElectrumServer If a user has a previously defined server, it will this property will be populated. Else, it will be null.
   */
  data class Off(
    val previousUserDefinedElectrumServer: ElectrumServer?,
  ) : ElectrumServerPreferenceValue()

  /**
   * Indicates that the user has configured **and** turned on the setting for using a custom
   * Electrum server, instead of the default one.
   *
   * @param server the ElectrumServer that the user has configured.
   */
  data class On(val server: ElectrumServer) : ElectrumServerPreferenceValue()
}
