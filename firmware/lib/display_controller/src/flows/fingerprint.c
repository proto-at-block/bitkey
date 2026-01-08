#include "attributes.h"
#include "display_controller.h"
#include "display_controller_internal.h"
#include "ui_events.h"  // For enrollment_progress_data_t

#include <stdio.h>

// Page definitions
#define PAGE_INTRO        0
#define PAGE_SCANNING     1
#define PAGE_SUCCESS      2
#define PAGE_FINISH       3  // For provisioned devices
#define PAGE_APP_DOWNLOAD 4  // For unprovisioned devices
#define PAGE_QR_CODE      5  // For unprovisioned devices

// Timing for auto-advance (2 seconds = 200 ticks at 10ms/tick)
#define AUTO_ADVANCE_DELAY_TICKS 200  // 2 seconds at 10ms/tick

static UI_TASK_DATA uint32_t auto_advance_timer = 0;

void display_controller_fingerprint_on_enter(display_controller_t* controller, const void* data) {
  // Initialize fingerprint enrollment state
  controller->nav.fingerprint.current_page = PAGE_INTRO;
  controller->nav.fingerprint.samples_done = 0;
  auto_advance_timer = 0;

  // Extract total sample count
  if (data) {
    const enrollment_progress_data_t* progress = (const enrollment_progress_data_t*)data;
    controller->nav.fingerprint.total_samples = progress->total_samples;
  }

  // Set up screen params for intro page
  controller->show_screen.params.fingerprint.current_page = PAGE_INTRO;
  controller->show_screen.params.fingerprint.progress_percent = 0;
  controller->show_screen.params.fingerprint.status =
    fwpb_display_params_fingerprint_display_params_fingerprint_status_NONE;

  controller->show_screen.which_params = fwpb_display_show_screen_fingerprint_tag;
}

flow_action_t display_controller_fingerprint_on_button_press(display_controller_t* controller,
                                                             const button_event_payload_t* event) {
  // Page 0 (Intro): L+R button advances to scanning page
  if (controller->nav.fingerprint.current_page == PAGE_INTRO) {
    if (event->type == BUTTON_PRESS_SINGLE && event->button == BUTTON_BOTH) {
      // Move to scanning page
      controller->nav.fingerprint.current_page = PAGE_SCANNING;
      controller->show_screen.params.fingerprint.current_page = PAGE_SCANNING;
      controller->show_screen.params.fingerprint.status =
        fwpb_display_params_fingerprint_display_params_fingerprint_status_ENROLL_FIRST;

      // Refresh screen to show scanning page
      display_controller_show_screen(controller, fwpb_display_show_screen_fingerprint_tag,
                                     fwpb_display_transition_DISPLAY_TRANSITION_NONE,
                                     TRANSITION_DURATION_NONE);

      // Trigger enrollment
      return FLOW_ACTION_START_ENROLLMENT;
    }
  }

  // Page 1 (Scanning): L+R button cancels enrollment
  else if (controller->nav.fingerprint.current_page == PAGE_SCANNING) {
    if (event->type == BUTTON_PRESS_SINGLE && event->button == BUTTON_BOTH) {
      return FLOW_ACTION_CANCEL;
    }
  }

  // Page 4 (App Download): L+R button advances to QR code page
  else if (controller->nav.fingerprint.current_page == PAGE_APP_DOWNLOAD) {
    if (event->type == BUTTON_PRESS_SINGLE && event->button == BUTTON_BOTH) {
      // Move to QR code page
      controller->nav.fingerprint.current_page = PAGE_QR_CODE;
      controller->show_screen.params.fingerprint.current_page = PAGE_QR_CODE;

      display_controller_show_screen(controller, fwpb_display_show_screen_fingerprint_tag,
                                     fwpb_display_transition_DISPLAY_TRANSITION_NONE,
                                     TRANSITION_DURATION_NONE);
      return FLOW_ACTION_NONE;
    }
  }

  // Page 5 (QR Code): L+R button exits to menu
  else if (controller->nav.fingerprint.current_page == PAGE_QR_CODE) {
    if (event->type == BUTTON_PRESS_SINGLE && event->button == BUTTON_BOTH) {
      return FLOW_ACTION_EXIT;  // Will go to menu
    }
  }

  // Pages 2-3: Any button press is ignored (auto-advance handles these)

  return FLOW_ACTION_NONE;
}

void display_controller_fingerprint_on_exit(display_controller_t* controller) {
  (void)controller;
  auto_advance_timer = 0;
}

void display_controller_fingerprint_on_tick(display_controller_t* controller) {
  // Handle auto-advance for success and finish pages
  if (controller->nav.fingerprint.current_page == PAGE_SUCCESS ||
      controller->nav.fingerprint.current_page == PAGE_FINISH) {
    auto_advance_timer++;

    if (auto_advance_timer >= AUTO_ADVANCE_DELAY_TICKS) {
      auto_advance_timer = 0;

      if (controller->nav.fingerprint.current_page == PAGE_SUCCESS) {
        // Check if device is provisioned
        if (controller->has_device_info) {
          // Provisioned - go to finish page (will auto-advance to scan)
          controller->nav.fingerprint.current_page = PAGE_FINISH;
          controller->show_screen.params.fingerprint.current_page = PAGE_FINISH;
        } else {
          // Unprovisioned - go to app download page (no auto-advance)
          controller->nav.fingerprint.current_page = PAGE_APP_DOWNLOAD;
          controller->show_screen.params.fingerprint.current_page = PAGE_APP_DOWNLOAD;
          auto_advance_timer = 0;  // Stop auto-advance
        }

        display_controller_show_screen(controller, fwpb_display_show_screen_fingerprint_tag,
                                       fwpb_display_transition_DISPLAY_TRANSITION_NONE,
                                       TRANSITION_DURATION_NONE);
      } else if (controller->nav.fingerprint.current_page == PAGE_FINISH) {
        // Advance from finish page to scan screen
        controller->show_screen.params.scan.action =
          fwpb_display_params_scan_display_params_scan_action_NONE;
        display_controller_show_screen(controller, fwpb_display_show_screen_scan_tag,
                                       fwpb_display_transition_DISPLAY_TRANSITION_NONE,
                                       TRANSITION_DURATION_NONE);

        // Exit fingerprint flow
        controller->show_screen.which_params = fwpb_display_show_screen_scan_tag;
      }
    }
  }
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
      controller->show_screen.params.fingerprint.current_page = PAGE_SUCCESS;
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
  display_controller_show_screen(controller, fwpb_display_show_screen_fingerprint_tag,
                                 fwpb_display_transition_DISPLAY_TRANSITION_NONE,
                                 TRANSITION_DURATION_NONE);
}

static void display_controller_fingerprint_handle_complete_event(display_controller_t* controller) {
  // Directly transition to success page
  controller->nav.fingerprint.samples_done = controller->nav.fingerprint.total_samples;
  controller->nav.fingerprint.current_page = PAGE_SUCCESS;
  controller->show_screen.params.fingerprint.current_page = PAGE_SUCCESS;
  controller->show_screen.params.fingerprint.progress_percent = 100;

  // Only auto-advance if device is provisioned
  if (controller->has_device_info) {
    auto_advance_timer = 0;  // Start auto-advance timer
  }

  // Refresh the screen to show completion
  display_controller_show_screen(controller, fwpb_display_show_screen_fingerprint_tag,
                                 fwpb_display_transition_DISPLAY_TRANSITION_NONE,
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
  display_controller_show_screen(controller, fwpb_display_show_screen_fingerprint_tag,
                                 fwpb_display_transition_DISPLAY_TRANSITION_NONE,
                                 TRANSITION_DURATION_NONE);
}

void display_controller_fingerprint_on_event(display_controller_t* controller,
                                             ui_event_type_t event, const void* data,
                                             uint32_t len) {
  switch (event) {
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
}
