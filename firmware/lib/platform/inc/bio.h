/**
 * @file bio.h
 * @brief POSIX passthrough for bio.h.
 *
 * The canonical hal/biometrics header (and the FPC BEP headers it pulls in)
 * are plain declarations that compile on POSIX; core-sim's meson.build adds
 * the hal/biometrics and third-party/fpc-bep include paths. The simulated
 * sensor implementation lives in app/core-sim/src/posix/bio_sim.c.
 */

#pragma once

#include "../../../hal/biometrics/inc/bio.h"
