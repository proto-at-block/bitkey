package build.wallet.ui.components.forms

/**
 * Reconciles a [build.wallet.ui.model.input.TextFieldModel]'s hoisted `value` with a
 * locally-owned `TextFieldState`.
 *
 * The text field is model-driven: every user edit is reported upward via `onValueChange`,
 * and the producer (a state machine or body model) usually echoes the same text back as a
 * new `model.value`. Because that round trip is asynchronous, echoes can arrive *after* the
 * user has typed more characters. Historically the field re-initialized itself from every
 * incoming `model.value`, which dropped rapid keystrokes and, on some Android IMEs
 * (e.g. Motorola/Android 16), permanently desynced the input connection so only the first
 * character could ever be entered (BKW-580).
 *
 * With `TextFieldState`, local state is the single source of truth while typing. This class
 * decides when an incoming model value is:
 * - an **echo** of an edit we already reported → ignore, never touch local state;
 * - a **genuine external correction** (formatting, paste button, programmatic clear/reset)
 *   → adopt into local state.
 *
 * ### Ordering assumptions
 *
 * Incoming values are observed via recomposition of `model.value`, which is Compose snapshot
 * state: recomposition **conflates** intermediate values (it may skip them) but cannot deliver
 * them out of order. [shouldAdoptExternal] therefore treats a match against the *latest*
 * reported text as "producer caught up" and clears older pending echoes — a skipped echo is
 * expected; a reordered one cannot occur through this pipeline.
 *
 * A consequence, by design: if a producer sets `model.value` back to text the user previously
 * typed (and which is no longer pending), it is classified as a genuine correction and adopted.
 * That is the correct behavior for producers that intentionally revert/reject an edit.
 */
internal class TextFieldModelSync(initialText: String) {
  /** The most recent text either reported upward or adopted from the model. */
  private var lastSynced = initialText

  /** Texts reported upward that have not yet been observed echoing back. */
  private val pendingEchoes = ArrayDeque<String>()

  /**
   * Called when the locally-owned text changes. Returns true if this is a user edit that
   * should be reported upward (false when the change is our own adoption of a model value).
   */
  fun onLocalTextChanged(text: String): Boolean {
    if (text == lastSynced) return false
    lastSynced = text
    pendingEchoes.addLast(text)
    // Producers that never echo would otherwise grow this without bound.
    while (pendingEchoes.size > MAX_PENDING_ECHOES) {
      pendingEchoes.removeFirst()
    }
    return true
  }

  /**
   * Called when a new model value arrives. Returns true if the value is a genuine external
   * correction that should replace the local text (false for echoes and no-ops).
   */
  fun shouldAdoptExternal(incoming: String): Boolean {
    if (incoming == lastSynced) {
      // Producer has caught up with our latest edit; all older reports are settled.
      pendingEchoes.clear()
      return false
    }
    val echoIndex = pendingEchoes.indexOf(incoming)
    if (echoIndex >= 0) {
      // Stale echo of an intermediate edit — the user has typed more since. Never adopt;
      // adopting would discard the characters typed during the round-trip window.
      repeat(echoIndex + 1) { pendingEchoes.removeFirst() }
      return false
    }
    // Not something we reported: the producer changed the text (formatting, paste,
    // validation, reset). Adopt it as the new source of truth.
    pendingEchoes.clear()
    lastSynced = incoming
    return true
  }

  private companion object {
    const val MAX_PENDING_ECHOES = 64
  }
}
