#include "attributes.h"
#include "display_controller.h"
#include "display_controller_internal.h"
#include "ui_events.h"  // For enrollment_progress_data_t

#include <stdio.h>

// Page definitions
#define PAGE_INTRO    0
#define PAGE_SCANNING 1
#define PAGE_SUCCESS  2

#define AUTO_ADVANCE_DELAY_TICKS 50  // 1 second at 20ms/tick

static UI_TASK_DATA uint32_t auto_advance_timer = 0;

void display_controller_fingerprint_on_enter(display_controller_t* controller,
                                             const void* entry_data) {
  // Initialize fingerprint enrollment state
  controller->nav.fingerprint.current_page = PAGE_INTRO;
  controller->nav.fingerprint.samples_done = 0;
  auto_advance_timer = 0;

  // Extract total sample count (keep old data extraction for now - not in typed union yet)
  if (entry_data) {
    const enrollment_progress_data_t* progress = (const enrollment_progress_data_t*)entry_data;
    controller->nav.fingerprint.total_samples = progress->total_samples;
  }

  // Set up screen params for intro page
  controller->show_screen.params.fingerprint.progress_percent = 0;
  controller->show_screen.params.fingerprint.samples_remaining =
    controller->nav.fingerprint.total_samples;
  controller->show_screen.params.fingerprint.status =
    fwpb_display_params_fingerprint_display_params_fingerprint_status_NONE;

  // Enrollment is required (no back button) if nav_stack is empty (entered from ONBOARDING)
  controller->show_screen.params.fingerprint.is_required = (controller->nav_stack_depth == 0);

  controller->show_screen.which_params = fwpb_display_show_screen_fingerprint_tag;
}

void display_controller_fingerprint_on_exit(display_controller_t* controller) {
  (void)controller;
  auto_advance_timer = 0;
}

flow_action_result_t display_controller_fingerprint_on_tick(display_controller_t* controller) {
  // Handle auto-advance for success page
  if (controller->nav.fingerprint.current_page == PAGE_SUCCESS) {
    auto_advance_timer++;

    if (auto_advance_timer >= AUTO_ADVANCE_DELAY_TICKS) {
      auto_advance_timer = 0;
      // Exit returns to caller automatically (FINGERPRINTS_MENU, ONBOARDING, or SCAN)
      return flow_result_exit_with_transition(fwpb_display_transition_DISPLAY_TRANSITION_FADE,
                                              TRANSITION_DURATION_STANDARD);
    }
  }

  return flow_result_handled();
}

static void display_controller_fingerprint_handle_progress_event(
  display_controller_t* controller, bool good_scan, const enrollment_progress_data_t* progress) {
  // Only handle progress events when on scanning page
  if (controller->nav.fingerprint.current_page != PAGE_SCANNING) {
    return;
  }

  if (good_scan && progress) {
    // Update total_samples if not set yet (can happen with internal enrollment)
    if (controller->nav.fingerprint.total_samples == 0 && progress->total_samples > 0) {
      controller->nav.fingerprint.total_samples = progress->total_samples;
    }

    // Calculate samples done and progress percentage
    controller->nav.fingerprint.samples_done =
      controller->nav.fingerprint.total_samples - progress->samples_remaining;

    if (progress->samples_remaining == 0) {
      controller->nav.fingerprint.samples_done = controller->nav.fingerprint.total_samples;

      // Move to success page
      controller->nav.fingerprint.current_page = PAGE_SUCCESS;
      controller->show_screen.params.fingerprint.progress_percent = 100;
      auto_advance_timer = 0;  // Start auto-advance timer

    } else if (controller->nav.fingerprint.samples_done > 0) {
      // Update message to show actual remaining count
      controller->show_screen.params.fingerprint.status =
        fwpb_display_params_fingerprint_display_params_fingerprint_status_ENROLL_REPEAT;
      controller->show_screen.params.fingerprint.samples_remaining = progress->samples_remaining;
    }
  } else {
    // Bad scan - show "Try again" with error flag
    controller->show_screen.params.fingerprint.status =
      fwpb_display_params_fingerprint_display_params_fingerprint_status_ENROLL_TRY_AGAIN;
  }

  // Calculate progress percentage
  uint8_t progress_percent = 0;
  if (controller->nav.fingerprint.total_samples > 0) {
    progress_percent =
      (controller->nav.fingerprint.samples_done * 100) / controller->nav.fingerprint.total_samples;
  }
  controller->show_screen.params.fingerprint.progress_percent = progress_percent;

  // Refresh the screen to show updated progress
  flow_update_current_screen(controller, fwpb_display_transition_DISPLAY_TRANSITION_NONE,
                             TRANSITION_DURATION_NONE);
}

static void display_controller_fingerprint_handle_complete_event(display_controller_t* controller) {
  // Directly transition to success page
  controller->nav.fingerprint.samples_done = controller->nav.fingerprint.total_samples;
  controller->nav.fingerprint.current_page = PAGE_SUCCESS;
  controller->show_screen.params.fingerprint.progress_percent = 100;

  // Start auto-advance timer
  auto_advance_timer = 0;

  // Refresh the screen to show completion
  flow_update_current_screen(controller, fwpb_display_transition_DISPLAY_TRANSITION_NONE,
                             TRANSITION_DURATION_NONE);
}

static void display_controller_fingerprint_handle_failed_event(display_controller_t* controller) {
  // Only handle failure on scanning page
  if (controller->nav.fingerprint.current_page != PAGE_SCANNING) {
    return;
  }

  controller->show_screen.params.fingerprint.status =
    fwpb_display_params_fingerprint_display_params_fingerprint_status_ENROLL_FAILED;

  // Keep current progress but show failure message
  flow_update_current_screen(controller, fwpb_display_transition_DISPLAY_TRANSITION_NONE,
                             TRANSITION_DURATION_NONE);
}

flow_action_result_t display_controller_fingerprint_on_event(display_controller_t* controller,
                                                             ui_event_type_t event,
                                                             const void* data, uint32_t len) {
  switch (event) {
    case UI_EVENT_ENROLLMENT_START:
      // Move from intro page to scanning page
      if (controller->nav.fingerprint.current_page == PAGE_INTRO) {
        controller->nav.fingerprint.current_page = PAGE_SCANNING;
        controller->show_screen.params.fingerprint.status =
          fwpb_display_params_fingerprint_display_params_fingerprint_status_ENROLL_FIRST;
        controller->show_screen.params.fingerprint.progress_percent = 0;

        flow_update_current_screen(controller, fwpb_display_transition_DISPLAY_TRANSITION_FADE,
                                   TRANSITION_DURATION_STANDARD);
      }
      break;

    case UI_EVENT_ENROLLMENT_PROGRESS_GOOD: {
      const enrollment_progress_data_t* progress = NULL;
      if (data && len == sizeof(enrollment_progress_data_t)) {
        progress = (const enrollment_progress_data_t*)data;
      }
      display_controller_fingerprint_handle_progress_event(controller, true, progress);
      break;
    }
    case UI_EVENT_ENROLLMENT_PROGRESS_BAD: {
      const enrollment_progress_data_t* progress = NULL;
      if (data && len == sizeof(enrollment_progress_data_t)) {
        progress = (const enrollment_progress_data_t*)data;
      }
      display_controller_fingerprint_handle_progress_event(controller, false, progress);
      break;
    }
    case UI_EVENT_ENROLLMENT_COMPLETE:
      display_controller_fingerprint_handle_complete_event(controller);
      break;
    case UI_EVENT_ENROLLMENT_FAILED:
      display_controller_fingerprint_handle_failed_event(controller);
      break;
    default:
      // Ignore other events
      break;
  }

  return flow_result_handled();
}

flow_action_result_t display_controller_fingerprint_on_action(
  display_controller_t* controller, fwpb_display_action_display_action_type action, uint32_t data) {
  (void)data;

  if (action == fwpb_display_action_display_action_type_DISPLAY_ACTION_BACK ||
      action == fwpb_display_action_display_action_type_DISPLAY_ACTION_CANCEL ||
      action == fwpb_display_action_display_action_type_DISPLAY_ACTION_EXIT) {
    // Block BACK/CANCEL during required enrollment (when nav_stack_depth == 0, entered from
    // ONBOARDING) User must complete enrollment to proceed
    if (controller->nav_stack_depth == 0) {
      return flow_result_handled();  // Ignore the action
    }

    // Optional enrollment (entered from MENU) - allow cancellation
    return flow_result_exit_with_transition(fwpb_display_transition_DISPLAY_TRANSITION_FADE,
                                            TRANSITION_DURATION_STANDARD);
  }

  return flow_result_handled();
}
