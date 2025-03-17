package build.wallet.statemachine.trustedcontact.model

/**
 * Different scenario context that trusted contacts may be used in.
 *
 * This structure is useful for copy variants in screens where a screen
 * identical in functionality may be used for either inheritance or recovery.
 */
sealed interface TrustedContactFeatureVariant {
  val isInheritanceEnabled: Boolean

  /**
   * Indicates a generic use-case, not indicating whether the contact is
   * being used for inheritance or recovery.
   */
  data class Generic(
    override val isInheritanceEnabled: Boolean,
  ) : TrustedContactFeatureVariant

  /**
   * Indicates that the contact is being explicitly used for a single feature.
   *
   * Note: This is different than a [Generic] copy with a single variant
   * enabled, as it will refer to the feature regardless of whether a
   * feature flag is disabling it.
   */
  data class Direct(
    val target: Feature,
  ) : TrustedContactFeatureVariant {
    override val isInheritanceEnabled: Boolean = target == Feature.Inheritance
  }

  /**
   * Features that trusted are used for that affect user experience.
   */
  enum class Feature {
    Inheritance,
    Recovery,
  }
}
