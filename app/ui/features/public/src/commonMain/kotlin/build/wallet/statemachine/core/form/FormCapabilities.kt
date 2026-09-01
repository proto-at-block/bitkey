package build.wallet.statemachine.core.form

/*
 * Opt-in capabilities that individual [FormBodyModel] subclasses can implement to tell the
 * renderer to perform niche behavior. The base [FormBodyModel] surface stays minimal — the
 * renderer detects each capability via `as?` and applies the corresponding behavior only when a
 * model opts in.
 */

/**
 * Marker interface implemented by [FormBodyModel] subclasses whose footer should fade and slide
 * in once the hosting state machine signals the reveal. The buttons must still be supplied from
 * the first composition so the footer slot can reserve its measured height — only
 * [footerRevealed] gates the animation.
 */
interface FooterRevealAware {
  val footerRevealed: Boolean
}

/**
 * Marker interface implemented by [FormBodyModel] subclasses that should prevent the screen
 * from dimming while the model is on-screen (e.g. during fingerprint enrollment or while
 * waiting on a partner transaction quote). Presence alone enables the behavior — there is no
 * associated value.
 */
interface KeepsScreenOn
