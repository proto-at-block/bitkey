package build.wallet.ui.model

/**
 * A sealed interface for defining type-safe functional interfaces for click handlers, rather than
 * passing lambdas
 *
 * *Note: this is needed to signal to the UI the type of interaction that is happening, so that click
 * is properly handled by the UI
 */
sealed interface Click {
  /**
   * Defines the subclasses must implement the invoke operator so the handlers can be directly invoked
   */
  operator fun invoke()

  /**
   * A standard click handler which is synchronously invoked on the UI
   */
  fun interface StandardClick : Click

  /**
   * A click handler which first closes the sheet on the UI then invokes the handler
   */
  fun interface SheetClosingClick : Click

  companion object {
    fun sheetClosingClick(onClick: () -> Unit): Click = SheetClosingClick { onClick() }

    fun standardClick(onClick: () -> Unit): Click = StandardClick { onClick() }
  }
}
