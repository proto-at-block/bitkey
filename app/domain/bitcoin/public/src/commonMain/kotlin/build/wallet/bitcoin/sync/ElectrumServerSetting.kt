package build.wallet.bitcoin.sync

/**
 * An interface that uses `Default` or `UserDefined` variants to indicate the user's Electrum server
 * setting.
 */
sealed interface ElectrumServerSetting {
  /**
   * Electrum server to connect to.
   */
  val server: ElectrumServer

  /**
   * An Electrum Server setting provided by Block.
   */
  data class Default(
    override val server: ElectrumServer,
  ) : ElectrumServerSetting

  /**
   * An Electrum Server setting provided by the customer.
   */
  data class UserDefined(override val server: ElectrumServer) : ElectrumServerSetting
}
