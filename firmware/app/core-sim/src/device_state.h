/**
 * @file device_state.h
 * @brief Centralized device/emulator state management for core-sim
 */

#ifndef DEVICE_STATE_H
#define DEVICE_STATE_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#define EMU_MAX_FINGERPRINTS       3
#define EMU_FINGERPRINT_LABEL_SIZE 32

typedef enum {
  EMU_AUTH_MODE_INSTANT = 0,
  EMU_AUTH_MODE_REALISTIC = 1,
} emu_auth_mode_t;

#define EMU_ENROLLMENT_REQUIRED_PASSES 5
#define EMU_AUTH_EXPIRY_MS             60000
#define EMU_BIO_RATE_LIMIT_MS          1000

typedef struct {
  /* Basic device state */
  bool authenticated;
  bool allow_enrollment;
  uint32_t timestamp;

  /* Enrollment */
  bool enrollment_in_progress;
  uint8_t enrollment_index;
  char enrollment_label[EMU_FINGERPRINT_LABEL_SIZE];
  uint32_t enrollment_pass_count;
  uint32_t enrollment_fail_count;

  /* Auth mode and expiry */
  emu_auth_mode_t auth_mode;
  uint32_t auth_expiry_timestamp;

  /* Biometric rate limiting */
  uint32_t last_bio_fail_timestamp;
} emulator_state_t;

/* State access */
emulator_state_t* emu_state_get(void);
void emu_state_reset(void);

/* Persistence */
bool emu_state_init(void);
void emu_state_save(void);
void emu_state_wipe(void);

/* Basic state */
bool emu_get_authenticated(void);
void emu_set_authenticated(bool authenticated);
bool emu_get_onboarding_complete(void);
bool emu_get_allow_enrollment(void);
void emu_set_allow_enrollment(bool allow);
void emu_set_timestamp(uint32_t timestamp);
uint32_t emu_get_timestamp(void);

/* Enrollment */
void emu_enrollment_start(uint8_t index, const char* label);
void emu_enrollment_cancel(void);
bool emu_enrollment_in_progress(void);
void emu_enrollment_add_pass(void);
void emu_enrollment_add_fail(void);
uint32_t emu_enrollment_get_pass_count(void);
uint32_t emu_enrollment_get_fail_count(void);
bool emu_enrollment_complete(void);
uint32_t emu_enrollment_required_passes(void);

/* Command authorization */
bool device_state_check_command_auth(uint32_t proto_tag);
bool device_state_build_unauth_response(uint32_t proto_tag, uint8_t* rsp, uint32_t* rsp_size);

/* Auth mode */
void emu_set_auth_mode(emu_auth_mode_t mode);
emu_auth_mode_t emu_get_auth_mode(void);

/* Time control */
uint32_t emu_get_current_time(void);
void emu_advance_time(uint32_t ms);
void emu_reset_time(void);

/* Auth expiry */
bool emu_auth_is_expired(void);
void emu_auth_refresh_expiry(void);

/* Biometric rate limiting */
bool emu_bio_rate_limit_check(void);
void emu_bio_record_fail(void);

#endif /* DEVICE_STATE_H */
