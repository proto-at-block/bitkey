#include "assert.h"
#include "attributes.h"
#include "display_controller_internal.h"
#include "log.h"
#include "ui_events.h"

#ifdef EMBEDDED_BUILD
#include "bio.h"
#include "hal_nfc.h"
#include "ipc.h"
#include "power.h"
#include "rtos.h"
#else
// External functions for simulation (provided by ui-simulate/main.c)
extern uint32_t rtos_thread_systime(void);
#endif

// Run-in test power phase configuration - fixed durations
#define RUNIN_PHASE1_DURATION_MS  (10 * 60 * 1000)  // 10 minutes for initial charge
#define RUNIN_PHASE2_DURATION_MS  (30 * 60 * 1000)  // 30 minutes for discharge
#define RUNIN_PHASE3_DURATION_MS  (30 * 60 * 1000)  // 30 minutes for final charge
#define RUNIN_SOC_LOG_INTERVAL_MS 10000             // Log SOC every 10 seconds
// SOC targets in millipercent (1000 = 1%)
#define RUNIN_TARGET_CHARGE_SOC       (65 * 1000)  // Phase 1 charge target SOC 65% (displays as 65%)
#define RUNIN_TARGET_DISCHARGE_SOC    (30 * 1000)  // Phase 2 discharge target SOC 30% (best effort)
#define RUNIN_TARGET_FINAL_CHARGE_SOC (65 * 1000)  // Phase 3 charge target SOC 65%
#define RUNIN_FINGERPRINT_CHECK_MS    (200)        // Check fingerprint sensor every 200ms

// Power cycling phases for run-in test
typedef enum {
  RUNIN_POWER_PHASE_INITIAL_CHARGE = 0,  // Phase 1: Charge to 65%
  RUNIN_POWER_PHASE_DISCHARGE,           // Phase 2: Discharge to 30%
  RUNIN_POWER_PHASE_FINAL_CHARGE,        // Phase 3: Charge back to 65%
  RUNIN_POWER_PHASE_COMPLETE,            // All phases done
} runin_power_phase_t;

// Run-in states
typedef enum {
  RUNIN_STATE_START_SCREEN = 0,
  RUNIN_STATE_COUNTDOWN,
  RUNIN_STATE_STATUS,
  RUNIN_STATE_ANIMATION,
  RUNIN_STATE_RED,
  RUNIN_STATE_GREEN,
  RUNIN_STATE_BLUE,
  RUNIN_STATE_WHITE,
  RUNIN_STATE_BLACK,
  RUNIN_STATE_NFC,
  RUNIN_STATE_BURNIN_GRID,
  RUNIN_STATE_COMPLETE,
} runin_state_t;

// Run-in context
typedef struct {
  runin_state_t state;            // Current display state
  uint32_t state_start_ms;        // When current display state started
  uint32_t test_start_ms;         // When entire test started
  uint32_t loop_count;            // Completed display loops
  uint32_t initial_soc;           // Initial battery %
  bool plugged_in;                // USB plugged status at test start
  uint32_t captouch_events;       // Phantom capacitive touch events
  uint32_t display_touch_events;  // Phantom display touch events
  uint32_t fingerprint_events;    // Phantom fingerprint events
  bool finger_down;               // `True` if finger is currently down on fingerprint sensor
  uint32_t countdown_value;       // Current countdown value (5..1)
  // Power cycling state
  runin_power_phase_t power_phase;  // Current power cycling phase
  uint32_t power_phase_start_ms;    // When current power phase started
  uint32_t min_soc;                 // Minimum SOC observed during test
  bool target_reached;              // True if SOC target reached (holding)
  bool power_phase_failed;          // True if a power phase timed out or unplugged
} runin_context_t;

static UI_TASK_DATA runin_context_t runin_ctx = {0};

static void _display_controller_show_countdown(display_controller_t* controller);

void display_controller_mfg_on_enter(display_controller_t* controller, const void* entry_data) {
  ASSERT(controller);

  // Always reset context on entry to prevent state leakage between test runs
  memset(&runin_ctx, 0, sizeof(runin_ctx));

  // Set which_params for mfg screens
  controller->show_screen.which_params = fwpb_display_show_screen_mfg_tag;

  // If entry_data provided, initialize immediately (no need to wait for event)
  // If no entry_data (e.g., entering from menu), default to START_SCREEN
  if (entry_data) {
    const mfgtest_show_screen_payload_t* payload = (const mfgtest_show_screen_payload_t*)entry_data;

    // test_mode == 0 means this was an exit request - don't initialize anything
    // The flow will exit immediately via on_event handler
    if (payload->test_mode == 0) {
      return;
    }

    // Check if we are starting a standalone touch test
    if (payload->test_mode == fwpb_display_mfg_test_mode_DISPLAY_MFG_TEST_MODE_TOUCH_TEST_BOXES) {
      controller->touch_test.active = true;
      controller->touch_test.end_time_ms = rtos_thread_systime() + payload->timeout_ms;
    }

    // Initialize run-in context if showing START_SCREEN
    if (payload->test_mode == fwpb_display_mfg_test_mode_DISPLAY_MFG_TEST_MODE_START_SCREEN) {
      runin_ctx.state = RUNIN_STATE_START_SCREEN;
      runin_ctx.test_start_ms = rtos_thread_systime();
      runin_ctx.state_start_ms = runin_ctx.test_start_ms;
#ifdef EMBEDDED_BUILD
      runin_ctx.plugged_in = power_is_plugged_in();
#else
      runin_ctx.plugged_in = true;
#endif
      runin_ctx.initial_soc = controller->battery_percent;
      runin_ctx.min_soc = controller->battery_percent;
      runin_ctx.power_phase = RUNIN_POWER_PHASE_INITIAL_CHARGE;
      runin_ctx.power_phase_start_ms = runin_ctx.test_start_ms;
    }

    // Setup screen params
    controller->show_screen.params.mfg.test_mode = payload->test_mode;
    controller->show_screen.params.mfg.custom_rgb = payload->custom_rgb;
    controller->show_screen.params.mfg.brightness_percent = payload->brightness_percent;

    // Set battery info for START_SCREEN
    if (payload->test_mode == fwpb_display_mfg_test_mode_DISPLAY_MFG_TEST_MODE_START_SCREEN) {
      controller->show_screen.params.mfg.initial_soc = runin_ctx.initial_soc;
      controller->show_screen.params.mfg.battery_percent = controller->battery_percent;
      controller->show_screen.params.mfg.is_charging = controller->is_charging;
      controller->show_screen.params.mfg.plugged_in = runin_ctx.plugged_in;
    }

    display_controller_show_screen(controller, fwpb_display_show_screen_mfg_tag,
                                   fwpb_display_transition_DISPLAY_TRANSITION_FADE,
                                   TRANSITION_DURATION_STANDARD);
  } else {
    // No entry_data - default to START_SCREEN (e.g., when entering from menu)
    runin_ctx.state = RUNIN_STATE_START_SCREEN;
    runin_ctx.test_start_ms = rtos_thread_systime();
    runin_ctx.state_start_ms = runin_ctx.test_start_ms;
#ifdef EMBEDDED_BUILD
    runin_ctx.plugged_in = power_is_plugged_in();
#else
    runin_ctx.plugged_in = true;
#endif
    runin_ctx.initial_soc = controller->battery_percent;
    runin_ctx.min_soc = controller->battery_percent;
    runin_ctx.power_phase = RUNIN_POWER_PHASE_INITIAL_CHARGE;
    runin_ctx.power_phase_start_ms = runin_ctx.test_start_ms;

    // Setup START_SCREEN params
    controller->show_screen.params.mfg.test_mode =
      fwpb_display_mfg_test_mode_DISPLAY_MFG_TEST_MODE_START_SCREEN;
    controller->show_screen.params.mfg.initial_soc = runin_ctx.initial_soc;
    controller->show_screen.params.mfg.battery_percent = controller->battery_percent;
    controller->show_screen.params.mfg.is_charging = controller->is_charging;
    controller->show_screen.params.mfg.plugged_in = runin_ctx.plugged_in;

    display_controller_show_screen(controller, fwpb_display_show_screen_mfg_tag,
                                   fwpb_display_transition_DISPLAY_TRANSITION_FADE,
                                   TRANSITION_DURATION_STANDARD);
  }
}

void display_controller_mfg_on_exit(display_controller_t* controller) {
  // Reset run-in state on exit
  memset(&runin_ctx, 0, sizeof(runin_ctx));

  // Terminate any active touch test and send timeout result
  if (controller->touch_test.active) {
    LOGW("[MFG] Exiting flow with active touch test - sending timeout");
#ifdef EMBEDDED_BUILD
    mfgtest_touch_test_result_internal_t result = {
      .x = controller->touch_test.touch_event.x,
      .y = controller->touch_test.touch_event.y,
      .timeout = true,
      .boxes_remaining = controller->touch_test.boxes_remaining,
    };
    ipc_send(mfgtest_port, &result, sizeof(result), IPC_MFGTEST_TOUCH_TEST_RESULT_INTERNAL);
#endif
    controller->touch_test.active = false;
  }

#ifdef EMBEDDED_BUILD
  // Restore normal power path operation.
  power_usb_suspend(false);
  power_enable_charging();
#endif
}

static void advance_runin_state(display_controller_t* controller) {
  runin_ctx.state_start_ms = rtos_thread_systime();

  switch (runin_ctx.state) {
    case RUNIN_STATE_COUNTDOWN:
      // Countdown: 5, 4, 3, 2, 1
      runin_ctx.countdown_value--;
      if (runin_ctx.countdown_value == 0) {
        // Countdown complete, start test loop with STATUS screen
        runin_ctx.state = RUNIN_STATE_STATUS;
#ifdef EMBEDDED_BUILD
        runin_ctx.finger_down = bio_wait_for_finger_non_blocking(BIO_FINGER_DOWN);
        LOGI("[MFG RunIn] Starting Phase 1: Initial charge to %lu%%",
             (unsigned long)(RUNIN_TARGET_CHARGE_SOC / 1000));
#endif
        runin_ctx.test_start_ms = rtos_thread_systime();           // Reset test start time
        runin_ctx.power_phase_start_ms = runin_ctx.test_start_ms;  // Reset power phase timer
        controller->show_screen.params.mfg.test_mode =
          fwpb_display_mfg_test_mode_DISPLAY_MFG_TEST_MODE_STATUS;
      } else {
        controller->show_screen.params.mfg.countdown_value = runin_ctx.countdown_value;
      }
      break;

    case RUNIN_STATE_STATUS:
      runin_ctx.state = RUNIN_STATE_ANIMATION;
      controller->show_screen.params.mfg.test_mode =
        fwpb_display_mfg_test_mode_DISPLAY_MFG_TEST_MODE_ANIMATION;
      break;

    case RUNIN_STATE_ANIMATION:
      runin_ctx.state = RUNIN_STATE_RED;
      controller->show_screen.params.mfg.test_mode =
        fwpb_display_mfg_test_mode_DISPLAY_MFG_TEST_MODE_CUSTOM_COLOR;
      controller->show_screen.params.mfg.custom_rgb = 0xFF0000;
      break;

    case RUNIN_STATE_RED:
      runin_ctx.state = RUNIN_STATE_GREEN;
      controller->show_screen.params.mfg.test_mode =
        fwpb_display_mfg_test_mode_DISPLAY_MFG_TEST_MODE_CUSTOM_COLOR;
      controller->show_screen.params.mfg.custom_rgb = 0x00FF00;
      break;
    case RUNIN_STATE_GREEN:
      runin_ctx.state = RUNIN_STATE_BLUE;
      controller->show_screen.params.mfg.test_mode =
        fwpb_display_mfg_test_mode_DISPLAY_MFG_TEST_MODE_CUSTOM_COLOR;
      controller->show_screen.params.mfg.custom_rgb = 0x0000FF;
      break;
    case RUNIN_STATE_BLUE:
      runin_ctx.state = RUNIN_STATE_WHITE;
      controller->show_screen.params.mfg.test_mode =
        fwpb_display_mfg_test_mode_DISPLAY_MFG_TEST_MODE_CUSTOM_COLOR;
      controller->show_screen.params.mfg.custom_rgb = 0xFFFFFF;
      break;
    case RUNIN_STATE_WHITE:
      runin_ctx.state = RUNIN_STATE_BLACK;
      controller->show_screen.params.mfg.test_mode =
        fwpb_display_mfg_test_mode_DISPLAY_MFG_TEST_MODE_CUSTOM_COLOR;
      controller->show_screen.params.mfg.custom_rgb = 0x000000;
      break;
    case RUNIN_STATE_BLACK:
      runin_ctx.state = RUNIN_STATE_NFC;
      controller->show_screen.params.mfg.test_mode =
        fwpb_display_mfg_test_mode_DISPLAY_MFG_TEST_MODE_NFC_TEST;
#ifdef EMBEDDED_BUILD
      hal_nfc_set_mode(HAL_NFC_MODE_LOOPBACK_A);
#endif
      break;

    case RUNIN_STATE_NFC:
      runin_ctx.state = RUNIN_STATE_BURNIN_GRID;
      controller->show_screen.params.mfg.test_mode =
        fwpb_display_mfg_test_mode_DISPLAY_MFG_TEST_MODE_BURNIN_GRID;
#ifdef EMBEDDED_BUILD
      if (hal_nfc_get_mode() != HAL_NFC_MODE_LISTENER) {
        hal_nfc_set_mode(HAL_NFC_MODE_LISTENER);
      }
#endif
      break;

    case RUNIN_STATE_BURNIN_GRID:
      // Loop back to STATUS screen
      runin_ctx.state = RUNIN_STATE_STATUS;
      runin_ctx.loop_count++;
      controller->show_screen.params.mfg.test_mode =
        fwpb_display_mfg_test_mode_DISPLAY_MFG_TEST_MODE_STATUS;
      LOGI("[MFG RunIn] Loop %lu complete", (unsigned long)runin_ctx.loop_count);
      break;

    default:
      return;
  }

  display_controller_show_screen(controller, fwpb_display_show_screen_mfg_tag,
                                 fwpb_display_transition_DISPLAY_TRANSITION_NONE, 0);
}

flow_action_result_t display_controller_mfg_on_tick(display_controller_t* controller) {
  // Check if a touch test is active or recently completed but the flow has not
  // yet exited.
  if (controller->touch_test.active) {
    // Check for timeout.
    if (rtos_thread_systime() >= controller->touch_test.end_time_ms) {
      controller->touch_test.active = false;
#ifdef EMBEDDED_BUILD
      // Test timed out - send result with timeout flag
      mfgtest_touch_test_result_internal_t result = {
        .x = controller->touch_test.touch_event.x,
        .y = controller->touch_test.touch_event.y,
        .timeout = true,
        .boxes_remaining = controller->touch_test.boxes_remaining,
      };
      ipc_send(mfgtest_port, &result, sizeof(result), IPC_MFGTEST_TOUCH_TEST_RESULT_INTERNAL);
#endif
      return flow_result_exit_to_scan();
    }

    return flow_result_handled();
  }

  // If we're in COMPLETE state, nothing to do
  if (runin_ctx.state == RUNIN_STATE_COMPLETE) {
    return flow_result_handled();
  }

  uint32_t now = rtos_thread_systime();
  uint32_t elapsed_in_state = now - runin_ctx.state_start_ms;
  uint32_t total_elapsed = now - runin_ctx.test_start_ms;

#ifdef EMBEDDED_BUILD
  // Check for finger down here, as the Auth Task does not run in the MfgTest application.
  // Rate-limited to avoid hammering the FPC sensor with SPI transactions.
  // Start checking after countdown completes to avoid false positives during setup.
  static uint32_t last_fingerprint_check_ms = 0;
  if ((runin_ctx.state >= RUNIN_STATE_STATUS) && (runin_ctx.state != RUNIN_STATE_COMPLETE)) {
    if (now - last_fingerprint_check_ms >= RUNIN_FINGERPRINT_CHECK_MS) {
      last_fingerprint_check_ms = now;
      if (bio_wait_for_finger_non_blocking(BIO_FINGER_DOWN)) {
        if (!runin_ctx.finger_down) {
          runin_ctx.fingerprint_events++;
          LOGI("[MFG RunIn] Phantom fingerprint event (total: %lu)",
               (unsigned long)runin_ctx.fingerprint_events);
          runin_ctx.finger_down = true;
        }
      } else {
        runin_ctx.finger_down = false;
      }
    }
  }

  // Test finishes when power cycling is complete (all 3 phases done) or failed
  bool test_finished =
    (runin_ctx.power_phase == RUNIN_POWER_PHASE_COMPLETE) || runin_ctx.power_phase_failed;

  // Power phase state machine - only runs after countdown (during actual test)
  if ((runin_ctx.state >= RUNIN_STATE_STATUS) && !test_finished) {
    uint32_t phase_elapsed = now - runin_ctx.power_phase_start_ms;

    // Get current SOC for phase transitions and min tracking
    uint32_t soc;
    uint32_t vcell;
    int32_t current;
    uint32_t cycles;
    power_get_battery(&soc, &vcell, &current, &cycles);

    // Track minimum SOC
    if (soc < runin_ctx.min_soc) {
      runin_ctx.min_soc = soc;
    }

    // Power phase state machine - fixed durations with SOC holding
    if (!runin_ctx.power_phase_failed) {
      // Debug: log SOC periodically
      static uint32_t last_soc_log_ms = 0;
      if (now - last_soc_log_ms >= RUNIN_SOC_LOG_INTERVAL_MS) {
        LOGI("[MFG RunIn] Phase %d: SOC=%lu.%03lu%%, elapsed=%lu ms, target_reached=%d",
             runin_ctx.power_phase, (unsigned long)(soc / 1000), (unsigned long)(soc % 1000),
             (unsigned long)phase_elapsed, runin_ctx.target_reached);
        last_soc_log_ms = now;
      }

      switch (runin_ctx.power_phase) {
        case RUNIN_POWER_PHASE_INITIAL_CHARGE:
          // Phase 1: Charge to RUNIN_TARGET_CHARGE_SOC within RUNIN_PHASE1_DURATION_MS, then hold
          if (soc >= RUNIN_TARGET_CHARGE_SOC && !runin_ctx.target_reached) {
            // Target reached - start holding (disable charging, keep USB connected)
            LOGI("[MFG RunIn] Phase 1: Target reached (SOC=%lu.%03lu%%), holding",
                 (unsigned long)(soc / 1000), (unsigned long)(soc % 1000));
            power_disable_charging();
            runin_ctx.target_reached = true;
          }
          if (phase_elapsed >= RUNIN_PHASE1_DURATION_MS) {
            if (!runin_ctx.target_reached) {
              // FAIL - didn't reach RUNIN_TARGET_CHARGE_SOC within time limit
              LOGE("[MFG RunIn] FAIL - Phase 1 timeout (SOC=%lu.%03lu%%, needed %lu%%)",
                   (unsigned long)(soc / 1000), (unsigned long)(soc % 1000),
                   (unsigned long)(RUNIN_TARGET_CHARGE_SOC / 1000));
              runin_ctx.power_phase_failed = true;
            } else {
              // Success - move to Phase 2 (discharge)
              LOGI("[MFG RunIn] Phase 1 complete, starting Phase 2: Discharge");
              power_usb_suspend(true);  // Disconnect USB power to force discharge
              // Charging already disabled from hold
              runin_ctx.power_phase = RUNIN_POWER_PHASE_DISCHARGE;
              runin_ctx.power_phase_start_ms = now;
              runin_ctx.target_reached = false;
            }
          }
          break;

        case RUNIN_POWER_PHASE_DISCHARGE:
          // Phase 2: Discharge for RUNIN_PHASE2_DURATION_MS, target RUNIN_TARGET_DISCHARGE_SOC
          if (soc <= RUNIN_TARGET_DISCHARGE_SOC && !runin_ctx.target_reached) {
            // Target reached - start holding (reconnect USB, keep charging disabled)
            LOGI("[MFG RunIn] Phase 2: Target reached (SOC=%lu.%03lu%%), holding",
                 (unsigned long)(soc / 1000), (unsigned long)(soc % 1000));
            power_usb_suspend(false);  // Reconnect USB power
            // Charging still disabled - holds SOC
            runin_ctx.target_reached = true;
          }
          if (phase_elapsed >= RUNIN_PHASE2_DURATION_MS) {
            // Move to Phase 3 (no failure condition for discharge)
            LOGI(
              "[MFG RunIn] Phase 2 complete (min_soc=%lu.%03lu%%), starting Phase 3: Final charge",
              (unsigned long)(runin_ctx.min_soc / 1000), (unsigned long)(runin_ctx.min_soc % 1000));
            power_usb_suspend(false);
            power_enable_charging();
            runin_ctx.power_phase = RUNIN_POWER_PHASE_FINAL_CHARGE;
            runin_ctx.power_phase_start_ms = now;
            runin_ctx.target_reached = false;
          }
          break;

        case RUNIN_POWER_PHASE_FINAL_CHARGE:
          // Phase 3: Charge to RUNIN_TARGET_FINAL_CHARGE_SOC within RUNIN_PHASE3_DURATION_MS, then
          // hold
          if (soc >= RUNIN_TARGET_FINAL_CHARGE_SOC && !runin_ctx.target_reached) {
            // Target reached - start holding
            LOGI("[MFG RunIn] Phase 3: Target reached (SOC=%lu.%03lu%%), holding",
                 (unsigned long)(soc / 1000), (unsigned long)(soc % 1000));
            power_disable_charging();
            runin_ctx.target_reached = true;
          }
          if (phase_elapsed >= RUNIN_PHASE3_DURATION_MS) {
            if (!runin_ctx.target_reached) {
              // FAIL - didn't reach RUNIN_TARGET_FINAL_CHARGE_SOC% within time limit
              LOGE("[MFG RunIn] FAIL - Phase 3 timeout (SOC=%lu.%03lu%%, needed %lu%%)",
                   (unsigned long)(soc / 1000), (unsigned long)(soc % 1000),
                   (unsigned long)(RUNIN_TARGET_FINAL_CHARGE_SOC / 1000));
              runin_ctx.power_phase_failed = true;
            } else {
              // Success - all phases complete!
              LOGI("[MFG RunIn] Phase 3 complete - Power cycling SUCCESS");
              runin_ctx.power_phase = RUNIN_POWER_PHASE_COMPLETE;
            }
          }
          break;

        case RUNIN_POWER_PHASE_COMPLETE:
          // All phases done - nothing to do
          break;
      }
    }
  }
#endif

  // Re-evaluate test_finished after power phase updates
  const bool test_now_finished =
    (runin_ctx.power_phase == RUNIN_POWER_PHASE_COMPLETE) || runin_ctx.power_phase_failed;

  // Check if we are on the status screen or the test has finished.
  if ((runin_ctx.state >= RUNIN_STATE_STATUS) &&
      ((runin_ctx.state == RUNIN_STATE_STATUS) || test_now_finished)) {
    // Determine pass/fail - includes phantom events AND power phase failures
    const bool has_phantom_failures =
      (runin_ctx.captouch_events > 0 || runin_ctx.display_touch_events > 0 ||
       runin_ctx.fingerprint_events > 0);
    const bool has_failures = has_phantom_failures || runin_ctx.power_phase_failed;

    // Calculate phase time remaining
    uint32_t phase_duration_ms = 0;
    switch (runin_ctx.power_phase) {
      case RUNIN_POWER_PHASE_INITIAL_CHARGE:
        phase_duration_ms = RUNIN_PHASE1_DURATION_MS;
        break;
      case RUNIN_POWER_PHASE_DISCHARGE:
        phase_duration_ms = RUNIN_PHASE2_DURATION_MS;
        break;
      case RUNIN_POWER_PHASE_FINAL_CHARGE:
        phase_duration_ms = RUNIN_PHASE3_DURATION_MS;
        break;
      default:
        phase_duration_ms = 0;
        break;
    }
    uint32_t phase_elapsed = now - runin_ctx.power_phase_start_ms;
    uint32_t phase_remaining =
      (phase_elapsed < phase_duration_ms) ? (phase_duration_ms - phase_elapsed) : 0;

    // Populate all test statistics for display.
    controller->show_screen.params.mfg.initial_soc = runin_ctx.initial_soc;
    controller->show_screen.params.mfg.battery_percent = controller->battery_percent;
    controller->show_screen.params.mfg.loop_count = runin_ctx.loop_count;
    controller->show_screen.params.mfg.elapsed_ms = total_elapsed;
    controller->show_screen.params.mfg.plugged_in = runin_ctx.plugged_in;
    controller->show_screen.params.mfg.captouch_events = runin_ctx.captouch_events;
    controller->show_screen.params.mfg.display_touch_events = runin_ctx.display_touch_events;
    controller->show_screen.params.mfg.fingerprint_events = runin_ctx.fingerprint_events;
    controller->show_screen.params.mfg.power_phase = (uint32_t)runin_ctx.power_phase;
    controller->show_screen.params.mfg.phase_time_remaining_ms = phase_remaining;
    controller->show_screen.params.mfg.target_reached = runin_ctx.target_reached;
    controller->show_screen.params.mfg.has_failures = has_failures;
    controller->show_screen.params.mfg.test_complete = test_now_finished;

    if (test_now_finished) {
      LOGI("[MFG RunIn] Test complete after %lu loops, min_soc=%lu%%",
           (unsigned long)runin_ctx.loop_count, (unsigned long)runin_ctx.min_soc);
      runin_ctx.state = RUNIN_STATE_COMPLETE;

      controller->show_screen.params.mfg.test_mode =
        fwpb_display_mfg_test_mode_DISPLAY_MFG_TEST_MODE_STATUS;

#ifdef EMBEDDED_BUILD
      // Send completion results to mfgtest_task via IPC
      mfgtest_runin_complete_internal_t results = {
        .loop_count = runin_ctx.loop_count,
        .initial_soc = runin_ctx.initial_soc,
        .final_soc = controller->battery_percent,
        .min_soc = runin_ctx.min_soc,
        .elapsed_ms = total_elapsed,
        .plugged_in = runin_ctx.plugged_in,
        .button_events = 0,  // No buttons on W3
        .captouch_events = runin_ctx.captouch_events,
        .touch_events = runin_ctx.display_touch_events,
        .fingerprint_events = runin_ctx.fingerprint_events,
        .success = !has_failures,
      };
      ipc_send(mfgtest_port, &results, sizeof(results), IPC_MFGTEST_RUNIN_COMPLETE_INTERNAL);
#endif
    }

    display_controller_show_screen(controller, fwpb_display_show_screen_mfg_tag,
                                   fwpb_display_transition_DISPLAY_TRANSITION_NONE, 0);
  }

  if (test_now_finished) {
    return flow_result_handled();
  }

  // State-specific timeout handling
  uint32_t state_timeout = 0;

  switch (runin_ctx.state) {
    case RUNIN_STATE_COUNTDOWN:
      state_timeout = 1000;  // 1 second per countdown number
      break;

    case RUNIN_STATE_STATUS:
      state_timeout = 10000;  // 10 seconds for status screen
      break;

    case RUNIN_STATE_RED:
    case RUNIN_STATE_GREEN:
    case RUNIN_STATE_BLUE:
    case RUNIN_STATE_WHITE:
    case RUNIN_STATE_BLACK:
      state_timeout = 5000;  // 5 seconds
      break;

    case RUNIN_STATE_ANIMATION:
      state_timeout = 15000;  // 15 seconds
      break;

    case RUNIN_STATE_BURNIN_GRID:
      state_timeout = 60000;  // 60 seconds
      break;

    case RUNIN_STATE_NFC:
      state_timeout = 10000;  // 10 seconds
      break;

    default:
      return flow_result_handled();  // No timeout for other states
  }

  if (elapsed_in_state >= state_timeout) {
    advance_runin_state(controller);
  }

  return flow_result_handled();
}

static void _display_controller_show_countdown(display_controller_t* controller) {
  runin_ctx.state = RUNIN_STATE_COUNTDOWN;
  runin_ctx.countdown_value = 5;
  runin_ctx.state_start_ms = rtos_thread_systime();
  controller->show_screen.params.mfg.test_mode =
    fwpb_display_mfg_test_mode_DISPLAY_MFG_TEST_MODE_COUNTDOWN;
  controller->show_screen.params.mfg.countdown_value = runin_ctx.countdown_value;
  display_controller_show_screen(controller, fwpb_display_show_screen_mfg_tag,
                                 fwpb_display_transition_DISPLAY_TRANSITION_NONE,
                                 TRANSITION_DURATION_STANDARD);
  LOGI("[MFG RunIn] Starting countdown");
}

flow_action_result_t display_controller_mfg_on_event(display_controller_t* controller,
                                                     ui_event_type_t event, const void* data,
                                                     uint32_t len) {
  switch (event) {
    case UI_EVENT_MFGTEST_SHOW_SCREEN: {
      // Handle screen updates when flow is already active
      if (data && len == sizeof(mfgtest_show_screen_payload_t)) {
        const mfgtest_show_screen_payload_t* payload = (const mfgtest_show_screen_payload_t*)data;

        // test_mode == 0 means exit MFG flow
        if (payload->test_mode == 0) {
          return flow_result_exit_with_transition(fwpb_display_transition_DISPLAY_TRANSITION_FADE,
                                                  TRANSITION_DURATION_STANDARD);
        }

        // Setup params for the specified test mode
        controller->show_screen.params.mfg.test_mode = payload->test_mode;
        controller->show_screen.params.mfg.custom_rgb = payload->custom_rgb;
        controller->show_screen.params.mfg.brightness_percent = payload->brightness_percent;

        display_controller_show_screen(controller, fwpb_display_show_screen_mfg_tag,
                                       fwpb_display_transition_DISPLAY_TRANSITION_FADE,
                                       TRANSITION_DURATION_STANDARD);
      }
      break;
    }
    case UI_EVENT_BATTERY_SOC:
      // 'break' intentionally omitted.
    case UI_EVENT_CHARGING:
      // 'break' intentionally omitted.
    case UI_EVENT_CHARGING_UNPLUGGED: {
      // Update battery display on START_SCREEN
      if (runin_ctx.state == RUNIN_STATE_START_SCREEN) {
#ifdef EMBEDDED_BUILD
        runin_ctx.plugged_in = power_is_plugged_in();
#else
        runin_ctx.plugged_in = controller->is_charging;
#endif
        controller->show_screen.params.mfg.battery_percent = controller->battery_percent;
        controller->show_screen.params.mfg.is_charging = controller->is_charging;
        controller->show_screen.params.mfg.plugged_in = runin_ctx.plugged_in;
        display_controller_show_screen(controller, fwpb_display_show_screen_mfg_tag,
                                       fwpb_display_transition_DISPLAY_TRANSITION_NONE, 0);
      }
      break;
    }

    case UI_EVENT_CAPTOUCH: {
      // Count phantom capacitive touch events after countdown completes
      if ((runin_ctx.state >= RUNIN_STATE_STATUS) && (runin_ctx.state != RUNIN_STATE_COMPLETE)) {
        runin_ctx.captouch_events++;
        LOGI("[MFG RunIn] Phantom captouch event (total: %lu)",
             (unsigned long)runin_ctx.captouch_events);
      }
      break;
    }

    case UI_EVENT_MFGTEST_TOUCH: {
      // Count phantom display touch events after countdown completes
      if ((runin_ctx.state >= RUNIN_STATE_STATUS) && (runin_ctx.state != RUNIN_STATE_COMPLETE)) {
        runin_ctx.display_touch_events++;
        LOGI("[MFG RunIn] Phantom touch event (total: %lu)",
             (unsigned long)runin_ctx.display_touch_events);
      } else if (controller->touch_test.active) {
        if (data && (len == sizeof(ui_event_touch_t))) {
          const ui_event_touch_t* touch_event = (const ui_event_touch_t*)data;
          controller->touch_test.touch_event.x = touch_event->x;
          controller->touch_test.touch_event.y = touch_event->y;
        }
      }
      break;
    }

    case UI_EVENT_MFGTEST_TOUCH_TEST_STATUS: {
      // Touch test status update (boxes cleared)
      if (data && (len == sizeof(mfgtest_touch_test_status_payload_t))) {
        const mfgtest_touch_test_status_payload_t* status =
          (const mfgtest_touch_test_status_payload_t*)data;

        // Update stored boxes_remaining for timeout reporting
        controller->touch_test.boxes_remaining = status->boxes_remaining;

#ifdef EMBEDDED_BUILD
        // Send result with current status
        mfgtest_touch_test_result_internal_t result = {
          .x = controller->touch_test.touch_event.x,
          .y = controller->touch_test.touch_event.y,
          .timeout = false,
          .boxes_remaining = status->boxes_remaining,
        };
        ipc_send(mfgtest_port, &result, sizeof(result), IPC_MFGTEST_TOUCH_TEST_RESULT_INTERNAL);
#endif

        // Continue test if there are more boxes remaining
        controller->touch_test.active = !!status->boxes_remaining;
        // Exit test flow if there are no more boxes remaining
        if (!status->boxes_remaining) {
          return flow_result_exit_to_scan();
        }
      }
      break;
    }

    case UI_EVENT_FINGER_DOWN_FROM_LOCKED:
      // 'break' intentionally omitted.
    case UI_EVENT_FINGER_DOWN_FROM_UNLOCKED: {
      // Phantom fingerprint event during test (after countdown starts)
      if ((runin_ctx.state >= RUNIN_STATE_STATUS) && (runin_ctx.state != RUNIN_STATE_COMPLETE)) {
        runin_ctx.fingerprint_events++;
        LOGI("[MFG RunIn] Phantom fingerprint event (total: %lu)",
             (unsigned long)runin_ctx.fingerprint_events);
      }
      break;
    }

    default:
      // Ignore other events
      break;
  }

  return flow_result_handled();
}

flow_action_result_t display_controller_mfg_on_action(
  display_controller_t* controller, fwpb_display_action_display_action_type action, uint32_t data) {
  (void)data;

  // Handle approve action on START_SCREEN - start countdown
  if (action == fwpb_display_action_display_action_type_DISPLAY_ACTION_APPROVE &&
      runin_ctx.state == RUNIN_STATE_START_SCREEN) {
#ifdef EMBEDDED_BUILD
    // Re-check USB status before starting countdown - block if unplugged
    runin_ctx.plugged_in = power_is_plugged_in();
    if (!runin_ctx.plugged_in) {
      LOGW("[MFG RunIn] Cannot start - USB not plugged in");
      return flow_result_handled();  // Ignore button press
    }
#endif
    _display_controller_show_countdown(controller);
    return flow_result_handled();
  }

  // Handle back button - allow exit from START_SCREEN, COMPLETE, or STATUS states
  if (action == fwpb_display_action_display_action_type_DISPLAY_ACTION_BACK) {
    if (runin_ctx.state == RUNIN_STATE_START_SCREEN || runin_ctx.state == RUNIN_STATE_COMPLETE ||
        runin_ctx.state == RUNIN_STATE_STATUS) {
      return flow_result_exit_with_transition(fwpb_display_transition_DISPLAY_TRANSITION_FADE,
                                              TRANSITION_DURATION_STANDARD);
    }
    // Ignore back button during other display states (countdown, animations, color tests)
    return flow_result_handled();
  }

  // Legacy EXIT action - allow exit
  if (action == fwpb_display_action_display_action_type_DISPLAY_ACTION_EXIT) {
    return flow_result_exit_with_transition(fwpb_display_transition_DISPLAY_TRANSITION_FADE,
                                            TRANSITION_DURATION_STANDARD);
  }

  return flow_result_handled();
}
