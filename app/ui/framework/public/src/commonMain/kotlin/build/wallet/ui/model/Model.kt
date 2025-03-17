package build.wallet.ui.model

/**
 * Marker interface for UI models.
 */
interface Model {
  /**
   A stable value that identifies the uniqueness of this model. This should not change simply if the
   content of a view changes. Each unique screen that you expect consumers of the state
   machine output to render individually (e.g. push/pop to on a stack, replace an existing view,
   render as a card, etc) should maintain a stable key as their content changes.
   */
  val key: String get() = this::class.qualifiedName!!
}
