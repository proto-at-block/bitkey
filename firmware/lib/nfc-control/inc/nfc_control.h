/**
 * @file
 *
 * @brief NFC disable/enable control with timeout and multi-caller safety.
 *
 * Allows tasks to temporarily disable NFC while handling operations that must
 * not be interrupted by NFC activity (e.g. FWUP verification, signing).
 *
 * NFC remains disabled until all outstanding disable tokens have been released
 * or expired. This prevents one caller from accidentally re-enabling NFC while
 * another still needs it disabled.
 *
 * @{
 */

#pragma once

#include <stdbool.h>
#include <stdint.h>

#define NFC_CONTROL_MAX_TOKENS    (4)
#define NFC_CONTROL_INVALID_TOKEN (UINT32_MAX)

typedef uint32_t nfc_disable_token_t;

/**
 * nfc_control_init() - Initialize the NFC control module.
 *
 * Must be called once during startup before any other nfc_control functions.
 */
void nfc_control_init(void);

/**
 * nfc_control_poll() - Expire timed-out tokens and re-enable NFC if needed.
 *
 * Called from the NFC task's worker loop (~10ms). This is the mechanism by
 * which timeout-based tokens are expired — no RTOS timer is used.
 */
void nfc_control_poll(void);

/**
 * nfc_disable() - Temporarily disable NFC.
 * @timeout_ms: Maximum duration in milliseconds. After this, the token
 *              auto-expires and NFC may be re-enabled (if no other tokens
 *              remain). Pass 0 for no timeout (must be explicitly re-enabled).
 *
 * The current NFC mode is saved on the first disable call and restored when
 * all outstanding tokens are released or expired.
 *
 * The mode change is applied at the start of the next hal_nfc_worker()
 * cycle, after rfalNfcWorker() has pushed any queued TX to the NFC chip.
 * Callers that send a proto response and then call nfc_disable must ensure
 * the NFC task has had a chance to queue the response (via
 * rfalNfcDataExchangeStart) first. In the current firmware this is
 * guaranteed: all callers (UI task, FWUP task) run at equal or lower
 * RTOS priority than the NFC task (HIGH), so the NFC task preempts
 * after the proto-response semaphore is given and queues the TX before the
 * caller resumes.
 *
 * Return: A token to pass to nfc_enable(), or NFC_CONTROL_INVALID_TOKEN if
 *         no slots are available.
 */
nfc_disable_token_t nfc_disable(uint32_t timeout_ms);

/**
 * nfc_enable() - Re-enable NFC by releasing a disable token.
 * @token: Token previously returned by nfc_disable().
 *
 * The previous NFC mode is restored only when all outstanding tokens have
 * been released or expired. Passing NFC_CONTROL_INVALID_TOKEN or an
 * already-released token is a safe no-op.
 */
void nfc_enable(nfc_disable_token_t token);

/** @} */
