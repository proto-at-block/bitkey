package build.wallet.statemachine.core.input

enum class DataInputStyle {
  /** Customer is entering for the first time. */
  Enter,

  /** Customer is editing after having entered for the first time. */
  Edit,
}
