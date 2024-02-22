package build.wallet.platform

/**
 * A gateway to platform's global environment dependencies.
 *
 * Inject this to provide a common constructor for implementations that might depend on platform
 * specific dependencies.
 */
expect class PlatformContext
