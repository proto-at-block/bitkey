package build.wallet.statemachine.settings.full.feedback

import build.wallet.analytics.events.screen.id.EventTrackerScreenId

enum class FeedbackEventTrackerScreenId : EventTrackerScreenId {
  /** Loading form structure from the backend */
  FEEDBACK_LOADING_FORM,

  /** Could not load form structure from the backend */
  FEEDBACK_LOAD_FAILED,

  /** User filling the form */
  FEEDBACK_FILLING_FORM,

  /** Submitting a filled-in user feedback */
  FEEDBACK_SUBMITTING,

  /** Successfully submitted user feedback */
  FEEDBACK_SUBMIT_SUCCESS,

  /** Could not submit user feedback */
  FEEDBACK_SUBMIT_FAILED,
}
