package build.wallet.platform.config

/**
 * Used to determine what functionality and features
 * should be enabled/disabled: debug menu, logging levels, default keybox config, network type.
 *
 * [AppVariant] is defined by each platform.
 */
enum class AppVariant {
  /**
   * Development variant with debugging tools and most features enabled.
   */
  Development,

  /**
   * Nightly builds for iOS (Android nightlies use Team currently)
   */
  Alpha,

  /**
   * Team internal testing variant
   */
  Team,

  /**
   * Public variant with only release features enabled.
   */
  Customer,

  /**
   * Emergency Exit Kit variant, identical to the [Customer] variant, but
   * in Emergency Exit Kit mode, with most onboarding features and server
   * connectivity disabled.
   * This variant is currently only used in Android builds
   */
  Emergency,
}
