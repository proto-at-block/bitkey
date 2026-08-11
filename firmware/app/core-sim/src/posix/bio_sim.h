/**
 * @file bio_sim.h
 * @brief Fingerprint sensor simulation API
 *
 * Simulates fingerprint sensor using pthread condvars for blocking waits
 * and in-memory template storage with optional persistence.
 */

#pragma once

#include "bio.h"  // For bio_gesture_t, bio_template_id_t

void bio_sim_init(void);

void bio_sim_signal_finger(bio_gesture_t gesture);

void bio_sim_reset(void);

uint32_t bio_sim_get_enrollment_samples_required(void);

void bio_sim_set_enrollment_samples_required(uint32_t samples);
