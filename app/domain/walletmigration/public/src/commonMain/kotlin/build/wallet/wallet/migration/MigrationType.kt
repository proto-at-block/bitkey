package build.wallet.wallet.migration

enum class MigrationType(
  val requiresNewAuthKeys: Boolean,
  val requiresNewDdk: Boolean,
  val requiresServerSweep: Boolean,
) {
  PrivateWalletMigration(
    requiresNewAuthKeys = false,
    requiresNewDdk = false,
    requiresServerSweep = false
  ),

  /**
   * Upgrade from a W1 hardware device to a W3 hardware device.
   *
   * This migration pairs a new W3 device, creates new keysets with it,
   * and sweeps funds from the old wallet using the old hardware to sign.
   * No server-side delay+notify is needed since we have the old hardware.
   * New auth keys and DDK are required for the new hardware.
   */
  W3Upgrade(
    requiresNewAuthKeys = true,
    requiresNewDdk = true,
    requiresServerSweep = false
  ),
}
