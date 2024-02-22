package build.wallet.amount

/**
 * Represents a button on a keypad.
 */
sealed class KeypadButton {
  /** Represents delete keypad button as "<" icon. */
  data object Delete : KeypadButton()

  /** Represents decimal keypad button as "●" icon. */
  data object Decimal : KeypadButton()

  /** Type safe representation of a keypad digit button. */
  sealed class Digit : KeypadButton() {
    abstract val value: Int

    data object Zero : Digit() {
      override val value: Int = 0
    }

    data object One : Digit() {
      override val value: Int = 1
    }

    data object Two : Digit() {
      override val value: Int = 2
    }

    data object Three : Digit() {
      override val value: Int = 3
    }

    data object Four : Digit() {
      override val value: Int = 4
    }

    data object Five : Digit() {
      override val value: Int = 5
    }

    data object Six : Digit() {
      override val value: Int = 6
    }

    data object Seven : Digit() {
      override val value: Int = 7
    }

    data object Eight : Digit() {
      override val value: Int = 8
    }

    data object Nine : Digit() {
      override val value: Int = 9
    }
  }
}
