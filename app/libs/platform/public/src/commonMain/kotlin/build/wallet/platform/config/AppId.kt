package build.wallet.platform.config

/**
 * Represents a unique identifier for an Android or iOS app.
 *
 * On Android, this is defined by Application ID.
 * On iOS, this is defined by Bundle ID.
 *
 * Each app variant has a unique identifier.
 * Examples: `world.bitkey`, `world.bitkey.app`, `world.bitkey.team`, `world.bitkey.debug`.
 */
data class AppId(val value: String)
