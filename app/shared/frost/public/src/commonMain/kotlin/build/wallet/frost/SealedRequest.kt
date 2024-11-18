package build.wallet.frost

/**
 * Wrapper class for a sealed request, created by [ShareGenerator.generate].
 */
data class SealedRequest(val value: String)
