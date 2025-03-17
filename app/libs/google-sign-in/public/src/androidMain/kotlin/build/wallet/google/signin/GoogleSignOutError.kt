package build.wallet.google.signin

/**
 * Describes a failure during a Google Sign Out - either something is wrong with our Google
 * integration, customer canceled signed, or there's some other error.
 */
data class GoogleSignOutError(override val message: String) : Error()
