package build.wallet.frost

/**
 * Used to create new instances of [ShareGenerator], which is a stateful class.
 */
interface ShareGeneratorFactory {
  fun createShareGenerator(): ShareGenerator
}
