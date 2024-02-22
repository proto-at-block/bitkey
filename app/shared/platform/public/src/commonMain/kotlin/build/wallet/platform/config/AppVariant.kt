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
   * Team internal testing variant
   */
  Team,

  /**
   * Beta variant with only release features enabled.
   */
  Beta,

  /**
   * Public variant with only release features enabled.
   */
  Customer,

  /**
   * Emergency Access variant, identical to the [Customer] variant, but
   * in emergency access mode, with most onboarding features and server
   * connectivity disabled.
   * This variant is currently only used in Android builds
   */
  Emergency,
}
