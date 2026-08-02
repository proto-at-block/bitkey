#include "hal_nfc_loopback.h"

#include "assert.h"
#include "attributes.h"
#include "hal_nfc.h"
#include "hal_nfc_impl.h"
#include "hal_nfc_loopback_impl.h"
#include "hal_nfc_timer_impl.h"
#include "log.h"
#include "platform.h"
#include "rfal_isoDep.h"
#include "rfal_nfc.h"
#include "rfal_platform.h"
#include "rfal_rf.h"
#include "rtos.h"

#include <stdbool.h>
#include <stdint.h>
#include <string.h>

extern hal_nfc_priv_t hal_nfc_priv;

#if defined(PLATFORM_CFG_NFC_READER_MODE) && (PLATFORM_CFG_NFC_READER_MODE)

typedef enum {
  /**
   * @brief Unused (default).
   */
  HAL_NFC_LOOPBACK_STATE_NONE = 0,

  /**
   * @brief Poll for the existence of a card.
   */
  HAL_NFC_LOOPBACK_STATE_POLL,

  /**
   * @brief Turn off the NFC controller to prepare to poll again.
   */
  HAL_NFC_LOOPBACK_STATE_POLL_RETRY,

  /**
   * @brief Perform anti-collision detection.
   */
  HAL_NFC_LOOPBACK_STATE_ANTI_COLLISION,

  /**
   * @brief Card was detected.
   */
  HAL_NFC_LOOPBACK_STATE_CARD_DETECTED,

  /**
   * @brief Timed out waiting for card detection.
   */
  HAL_NFC_LOOPBACK_STATE_TIMEOUT,

  /**
   * @brief Exiting loopback test mode.
   */
  HAL_NFC_LOOPBACK_STATE_RESET,

  /**
   * @brief Waiting for poll delay to elapse before re-polling in continuous mode.
   */
  HAL_NFC_LOOPBACK_STATE_POLL_DELAY,
} hal_nfc_loopback_state_t;

static struct {
  /**
   * @brief Current loopback test state.
   */
  hal_nfc_loopback_state_t state;

  /**
   * @brief Start time of the loopback test.
   */
  uint32_t loopback_start_time_ms;

  /**
   * @brief Time that the loopback test should end at.
   */
  uint32_t loopback_end_time_ms;

  /**
   * @brief If true, restart polling after card detection instead of stopping.
   */
  bool continuous_mode;

  /**
   * @brief Delay (milliseconds) between card detection and re-polling in continuous mode.
   */
  uint32_t poll_delay_ms;

  /**
   * @brief Time (ms) at which the poll delay started.
   */
  uint32_t poll_delay_start_ms;

  /**
   * @brief Time (ms) at which the poll delay expires.
   */
  uint32_t poll_delay_end_ms;

} hal_nfc_loopback_priv NFC_TASK_DATA;

/**
 * @brief Checks for loopback test time out.
 *
 * @return `true` if test timed out, otherwise `false`.
 */
static bool hal_nfc_loopback_timeout(void);

/**
 * @brief Checks if the poll delay has elapsed.
 *
 * @return `true` if the poll delay has elapsed or the timer wrapped, otherwise `false`.
 */
static bool hal_nfc_loopback_poll_delay_elapsed(void);

/**
 * @brief Runs a step of the loopback test.
 */
static void hal_nfc_loopback_step(void);

/**
 * @brief Polls for the presence of a card.
 *
 * @return `true` if card is detected, otherwise `false`.
 */
static bool hal_nfc_loopback_poll(void);

/**
 * @brief Performs anti-collision testing.
 *
 * @return `true` if anti-collision testing was successful, otherwise `false`.
 */
static bool hal_nfc_loopback_anti_collision(void);

/**
 * @brief Returns true if the NFC mode is valid for loopback operations.
 */
static bool hal_nfc_loopback_mode_is_supported(hal_nfc_mode_t mode);

#endif

void hal_nfc_loopback_test_start(hal_nfc_mode_t mode, uint32_t timeout_ms, bool continuous,
                                 uint32_t poll_delay_ms) {
  (void)mode;
  (void)timeout_ms;
  (void)continuous;
  (void)poll_delay_ms;

#if defined(PLATFORM_CFG_NFC_READER_MODE) && (PLATFORM_CFG_NFC_READER_MODE)
  const uint32_t events = rtos_event_group_get_bits(&hal_nfc_priv.nfc_events);
  (void)rtos_event_group_clear_bits(&hal_nfc_priv.nfc_events, events);

  hal_nfc_priv.card_detection_timeout_ms = timeout_ms;
  hal_nfc_loopback_priv.continuous_mode = continuous;
  hal_nfc_loopback_priv.poll_delay_ms =
    ((poll_delay_ms > 0) && (poll_delay_ms < HAL_NFC_LOOPBACK_MIN_RETRY_SLEEP_MS))
      ? HAL_NFC_LOOPBACK_MIN_RETRY_SLEEP_MS
      : poll_delay_ms;
  hal_nfc_set_mode(mode);
#endif
}

bool hal_nfc_loopback_test_passed(void) {
#if defined(PLATFORM_CFG_NFC_READER_MODE) && (PLATFORM_CFG_NFC_READER_MODE)
  const uint32_t events = rtos_event_group_wait_bits(
    &hal_nfc_priv.nfc_events, (HAL_NFC_EVENT_CARD_DETECTED | HAL_NFC_EVENT_CARD_TIMEOUT),
    false /* do not clear */, false /* wait any */, 0u /* non-blocking */);
#else
  const uint32_t events = 0;
#endif

  return (events & HAL_NFC_EVENT_CARD_DETECTED);
}

#if defined(PLATFORM_CFG_NFC_READER_MODE) && (PLATFORM_CFG_NFC_READER_MODE)

void hal_nfc_loopback_init(hal_nfc_mode_t mode) {
  hal_nfc_priv.discovery_cfg.devLimit = 5U;
  hal_nfc_priv.discovery_cfg.notifyCb = NULL;
  hal_nfc_priv.discovery_cfg.compMode = RFAL_COMPLIANCE_MODE_NFC;

  // Set a default totalDuration value for consistency with other discovery
  // configurations. Note: the loopback implementation uses direct RFAL poller
  // APIs and enforces its own timeout via loopback_start_time_ms/loopback_end_time_ms,
  // allowing timeouts larger than the uint16_t totalDuration field limitation (max 65535 ms).
  hal_nfc_priv.discovery_cfg.totalDuration = HAL_NFC_DEFAULT_READER_TIMEOUT_MS;

  uint32_t requested_timeout_ms;
  if (hal_nfc_priv.card_detection_timeout_ms == 0) {
    requested_timeout_ms = HAL_NFC_DEFAULT_READER_TIMEOUT_MS;
  } else {
    requested_timeout_ms = hal_nfc_priv.card_detection_timeout_ms;
    hal_nfc_priv.card_detection_timeout_ms = 0;
  }

  hal_nfc_loopback_priv.state = HAL_NFC_LOOPBACK_STATE_POLL;
  hal_nfc_loopback_priv.loopback_start_time_ms = rtos_thread_systime();
  hal_nfc_loopback_priv.loopback_end_time_ms =
    requested_timeout_ms + hal_nfc_loopback_priv.loopback_start_time_ms;

  switch (mode) {
    case HAL_NFC_MODE_LOOPBACK_A:
      hal_nfc_priv.discovery_cfg.techs2Find = RFAL_NFC_POLL_TECH_A;
      break;

#if defined(RFAL_FEATURE_NFCB) && (RFAL_FEATURE_NFCB)
    case HAL_NFC_MODE_LOOPBACK_B:
      hal_nfc_priv.discovery_cfg.techs2Find = RFAL_NFC_POLL_TECH_B;
      break;
#endif

    default:
      ASSERT(false);
  }
}

void hal_nfc_loopback_deinit(void) {
  rfalFieldOff();
  nfc_timer_stop_all();
}

void hal_nfc_loopback_run(hal_nfc_callback_t callback) {
  (void)callback;

  switch (rfalNfcGetState()) {
    case RFAL_NFC_STATE_IDLE:
      hal_nfc_loopback_step();
      break;

    default:
      // Necessary to meet NFC timing requirements
      rfalNfcWorker();
      break;
  }
}

bool hal_nfc_loopback_test(hal_nfc_mode_t mode, uint32_t timeout_ms) {
  hal_nfc_loopback_test_start(mode, timeout_ms, false /* continuous */, 0 /* poll_delay_ms */);

  const uint32_t events = rtos_event_group_wait_bits(
    &hal_nfc_priv.nfc_events, (HAL_NFC_EVENT_CARD_DETECTED | HAL_NFC_EVENT_CARD_TIMEOUT),
    true /* clear */, false /* wait any */, RTOS_EVENT_GROUP_TIMEOUT_MAX /* indefinite */);

  return (events & HAL_NFC_EVENT_CARD_DETECTED);
}

static bool hal_nfc_loopback_timeout(void) {
  const uint32_t current_time = rtos_thread_systime();
  return ((current_time < hal_nfc_loopback_priv.loopback_start_time_ms) ||
          (current_time >= hal_nfc_loopback_priv.loopback_end_time_ms));
}

static bool hal_nfc_loopback_poll_delay_elapsed(void) {
  const uint32_t current_time = rtos_thread_systime();
  return ((current_time < hal_nfc_loopback_priv.poll_delay_start_ms) ||
          (current_time >= hal_nfc_loopback_priv.poll_delay_end_ms));
}

static void hal_nfc_loopback_step(void) {
  switch (hal_nfc_loopback_priv.state) {
    case HAL_NFC_LOOPBACK_STATE_POLL: {
      if (hal_nfc_loopback_timeout()) {
        rfalFieldOff();
        hal_nfc_loopback_priv.state = HAL_NFC_LOOPBACK_STATE_TIMEOUT;
      } else {
        hal_nfc_loopback_priv.state = hal_nfc_loopback_poll()
                                        ? HAL_NFC_LOOPBACK_STATE_ANTI_COLLISION
                                        : HAL_NFC_LOOPBACK_STATE_POLL_RETRY;
      }
      break;
    }

    case HAL_NFC_LOOPBACK_STATE_POLL_RETRY: {
      rfalFieldOff();
      rtos_thread_sleep(HAL_NFC_LOOPBACK_MIN_RETRY_SLEEP_MS);

      if (hal_nfc_loopback_timeout()) {
        hal_nfc_loopback_priv.state = HAL_NFC_LOOPBACK_STATE_TIMEOUT;
      } else {
        hal_nfc_loopback_priv.state = HAL_NFC_LOOPBACK_STATE_POLL;
      }
      break;
    }

    case HAL_NFC_LOOPBACK_STATE_ANTI_COLLISION:
      hal_nfc_loopback_priv.state = hal_nfc_loopback_anti_collision()
                                      ? HAL_NFC_LOOPBACK_STATE_CARD_DETECTED
                                      : HAL_NFC_LOOPBACK_STATE_POLL_RETRY;
      break;

    case HAL_NFC_LOOPBACK_STATE_CARD_DETECTED:
      (void)rtos_event_group_set_bits(&hal_nfc_priv.nfc_events, HAL_NFC_EVENT_CARD_DETECTED);
      if (hal_nfc_loopback_priv.continuous_mode) {
        if (hal_nfc_loopback_priv.poll_delay_ms > 0) {
          rfalFieldOff();
          // Non-blocking delay: record start and deadline, enter POLL_DELAY state
          const uint32_t now_ms = rtos_thread_systime();
          hal_nfc_loopback_priv.poll_delay_start_ms = now_ms;
          hal_nfc_loopback_priv.poll_delay_end_ms = now_ms + hal_nfc_loopback_priv.poll_delay_ms;
          hal_nfc_loopback_priv.state = HAL_NFC_LOOPBACK_STATE_POLL_DELAY;
        } else {
          hal_nfc_loopback_priv.state = HAL_NFC_LOOPBACK_STATE_POLL_RETRY;
        }
      } else {
        // In single-shot mode, reset and exit (existing behavior)
        hal_nfc_loopback_priv.state = HAL_NFC_LOOPBACK_STATE_RESET;
      }
      break;

    case HAL_NFC_LOOPBACK_STATE_POLL_DELAY:
      if (hal_nfc_loopback_timeout()) {
        hal_nfc_loopback_priv.state = HAL_NFC_LOOPBACK_STATE_TIMEOUT;
      } else if (hal_nfc_loopback_poll_delay_elapsed()) {
        hal_nfc_loopback_priv.state = HAL_NFC_LOOPBACK_STATE_POLL;
      }
      // Otherwise stay in POLL_DELAY — delay hasn't elapsed yet
      break;

    case HAL_NFC_LOOPBACK_STATE_TIMEOUT:
      (void)rtos_event_group_set_bits(&hal_nfc_priv.nfc_events, HAL_NFC_EVENT_CARD_TIMEOUT);
      hal_nfc_loopback_priv.state = HAL_NFC_LOOPBACK_STATE_RESET;
      break;

    case HAL_NFC_LOOPBACK_STATE_RESET:
      rfalFieldOff();
      hal_nfc_set_mode(hal_nfc_priv.prev_mode);
      hal_nfc_loopback_priv.state = HAL_NFC_LOOPBACK_STATE_NONE;
      break;

    case HAL_NFC_LOOPBACK_STATE_NONE:
      // The mode transition requested by RESET is asynchronous. Loopback can be dispatched once
      // more before the transition completes, so allow the NFC worker to finish tearing it down.
      LOGW("[NFC] Ignoring stale loopback step after reset");
      break;

    default:
      LOGW("[NFC] Failing loopback test in invalid state %u",
           (unsigned)hal_nfc_loopback_priv.state);
      hal_nfc_loopback_priv.state = HAL_NFC_LOOPBACK_STATE_TIMEOUT;
      break;
  }
}

static bool hal_nfc_loopback_poll(void) {
  const hal_nfc_mode_t mode = hal_nfc_get_mode();

  // Treat stale dispatches and transient RFAL setup failures as unsuccessful poll attempts. This
  // preserves the loopback test's retry/timeout behavior without turning a failed attempt into a
  // pass.
  if (!hal_nfc_loopback_mode_is_supported(mode)) {
    LOGW("[NFC] Ignoring stale loopback poll in mode %u", (unsigned)mode);
    return false;
  }

  st_ret_t err = RFAL_ERR_NONE;
  switch (mode) {
    case HAL_NFC_MODE_LOOPBACK_A: {
      if (rfalGetMode() != RFAL_MODE_POLL_NFCA) {
        err = rfalNfcaPollerInitialize();
        if (err != RFAL_ERR_NONE) {
          LOGW("[NFC] Loopback A poller init failed: %d", (int)err);
          return false;
        }
      }
      break;
    }

#if defined(RFAL_FEATURE_NFCB) && (RFAL_FEATURE_NFCB)
    case HAL_NFC_MODE_LOOPBACK_B: {
      if (rfalGetMode() != RFAL_MODE_POLL_NFCB) {
        err = rfalNfcbPollerInitialize();
        if (err != RFAL_ERR_NONE) {
          LOGW("[NFC] Loopback B poller init failed: %d", (int)err);
          return false;
        }
      }
      break;
    }
#endif
    default:
      return false;
  }

  rfalSetErrorHandling(RFAL_ERRORHANDLING_NONE);
  err = rfalFieldOnAndStartGT();
  if (err != RFAL_ERR_NONE) {
    LOGW("[NFC] Loopback field-on failed: %d", (int)err);
    return false;
  }

  if (mode == HAL_NFC_MODE_LOOPBACK_A) {
    rfalNfcaSensRes sens;
    err = rfalNfcaPollerTechnologyDetection(RFAL_COMPLIANCE_MODE_NFC, &sens);
  } else {
#if defined(RFAL_FEATURE_NFCB) && (RFAL_FEATURE_NFCB)
    rfalNfcbSensbRes sens;
    uint8_t sens_len;
    err = rfalNfcbPollerTechnologyDetection(RFAL_COMPLIANCE_MODE_NFC, &sens, &sens_len);
#else
    return false;
#endif
  }

  return (err == RFAL_ERR_NONE);
}

static bool hal_nfc_loopback_anti_collision(void) {
  static struct {
    union {
      rfalNfcaListenDevice nfca;
      rfalNfcbListenDevice nfcb;
    };
  } nfc_devs NFC_TASK_DATA = {0};

  const hal_nfc_mode_t mode = hal_nfc_get_mode();
  st_ret_t err = RFAL_ERR_NONE;

  // As in the poll step, returning false keeps the test on its normal retry/timeout path.
  if (!hal_nfc_loopback_mode_is_supported(mode)) {
    LOGW("[NFC] Ignoring stale loopback anti-collision in mode %u", (unsigned)mode);
    return false;
  }

  if (mode == HAL_NFC_MODE_LOOPBACK_A) {
    err = rfalNfcaPollerInitialize();
    if (err != RFAL_ERR_NONE) {
      LOGW("[NFC] Loopback A anti-collision init failed: %d", (int)err);
      return false;
    }
#if defined(RFAL_FEATURE_NFCB) && (RFAL_FEATURE_NFCB)
  } else if (mode == HAL_NFC_MODE_LOOPBACK_B) {
    err = rfalNfcbPollerInitialize();
    if (err != RFAL_ERR_NONE) {
      LOGW("[NFC] Loopback B anti-collision init failed: %d", (int)err);
      return false;
    }
#endif
  }

  err = rfalFieldOnAndStartGT();
  if (err != RFAL_ERR_NONE) {
    LOGW("[NFC] Loopback anti-collision field-on failed: %d", (int)err);
    return false;
  }

  uint8_t num_cards = 0;
  uint8_t num_tries;
  for (num_tries = 0; num_tries < HAL_NFC_LOOPBACK_MAX_RETRIES; num_tries++) {
    if (mode == HAL_NFC_MODE_LOOPBACK_A) {
      err = rfalNfcaPollerFullCollisionResolution(
        RFAL_COMPLIANCE_MODE_NFC, 0 /* collision detection */, &nfc_devs.nfca, &num_cards);
#if defined(RFAL_FEATURE_NFCB) && (RFAL_FEATURE_NFCB)
    } else if (mode == HAL_NFC_MODE_LOOPBACK_B) {
      err = rfalNfcbPollerCollisionResolution(RFAL_COMPLIANCE_MODE_NFC, 0 /* collision detection */,
                                              &nfc_devs.nfcb, &num_cards);
#endif
    }

    if ((err == RFAL_ERR_NONE) && (num_cards == HAL_NFC_LOOPBACK_EXPECTED_CARDS)) {
      // Successfully identified a single card.
      break;
    }
    rtos_thread_sleep(HAL_NFC_LOOPBACK_MIN_RETRY_SLEEP_MS);
  }

  rfalFieldOff();
  return (num_tries < HAL_NFC_LOOPBACK_MAX_RETRIES);
}

static bool hal_nfc_loopback_mode_is_supported(hal_nfc_mode_t mode) {
  switch (mode) {
    case HAL_NFC_MODE_LOOPBACK_A:
      return true;

#if defined(RFAL_FEATURE_NFCB) && (RFAL_FEATURE_NFCB)
    case HAL_NFC_MODE_LOOPBACK_B:
      return true;
#endif

    default:
      return false;
  }
}

#endif
