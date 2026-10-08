package build.wallet.platform.sharing

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Broadcast receiver for sharing manager. Triggered when a user selects a share action.
 * We use this to know when to advance the trusted contact invitation flow.
 *
 * Declared in the manifest, so the system instantiates it with its no-arg constructor when the
 * chooser reports a selection — which can be after the activity that started the share is gone.
 * The pending completion therefore has to be reachable statically rather than injected.
 */
class SharingManagerBroadcastReceiver : BroadcastReceiver() {
  override fun onReceive(
    context: Context,
    intent: Intent,
  ) {
    takePendingCompletion()?.invoke(true)
  }

  companion object {
    /**
     * Completion for the share currently awaiting a selection, or null when none is outstanding.
     *
     * Deliberately only the callback, not the [SharingManager] that created it: the manager is
     * activity-scoped and holds its `Activity`, so parking it here outlived the activity and leaked
     * it whenever the chooser was dismissed rather than acted on.
     *
     * Callers must not capture an `Activity`, a `Context`, or anything activity-scoped in the
     * callback they pass, for the same reason.
     */
    private var pendingCompletion: ((Boolean) -> Unit)? = null

    fun setPendingCompletion(completion: ((Boolean) -> Unit)?) {
      pendingCompletion = completion
    }

    /**
     * Returns the outstanding completion and clears it, so a share is only ever reported once and
     * nothing is retained afterwards.
     */
    fun takePendingCompletion(): ((Boolean) -> Unit)? =
      pendingCompletion.also { pendingCompletion = null }
  }
}
