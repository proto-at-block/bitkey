package build.wallet.statemachine.core.form

/**
 * Content that can be rendered in the pre-footer slot of a form screen — the area directly
 * above the footer buttons (see [FormBodyModel.preFooterContentList]).
 *
 * This is a narrowed subset of [FormMainContentModel]: only content types that the form
 * renderer knows how to draw in the pre-footer slot implement this interface. Using a sealed
 * marker (rather than accepting any [FormMainContentModel]) makes unsupported pre-footer
 * content a compile-time error instead of a runtime crash.
 *
 * To support a new pre-footer content type, add this as a supertype on the
 * [FormMainContentModel] variant and handle it in the renderer's pre-footer `when` (the
 * exhaustive `when` will force the renderer change).
 */
sealed interface FormPreFooterContentModel
