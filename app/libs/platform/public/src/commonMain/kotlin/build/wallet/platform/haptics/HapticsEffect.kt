package build.wallet.platform.haptics

enum class HapticsEffect {
  /** A short, intense vibration. Used for success states. */
  DoubleClick,

  /** A longer, duller vibration. Used for error states. */
  DullOneShot,

  Selection,

  /** A distinct error/rejection pulse. */
  Reject,

  /** A short, soft vibration. Used for user click feedback. */
  LightClick,

  /** A short, intense vibration. Used for user click feedback. */
  MediumClick,

  /** A short, very-intense vibration. Used for user click feedback. */
  HeavyClick,
}
