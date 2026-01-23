#include "secutils.h"

#include "secutils_impl.h"

#include <assert.h>
#include <stdint.h>

// These numbers were determined emperically on the EFR32MG24 running at 78Mhz.
// An experiment was ran with the following results.
// The experiment set a fixed delay for each random delay call to the loop counter specified.
// loop counter - pulse width        - boot time
// 0            - 212   microseconds - (0.300 second boot)
// 100          - 243   microseconds - (0.300 second boot)
// 1000         - 530   microseconds - (0.320 second boot)
// 2000         - 846   microseconds - (0.358 second boot)
// 5000         - 1800  microseconds - (0.440 second boot)
// 10000        - 3390  microseconds - (0.600 second boot)
// 100000       - 31372 microseconds - (3.320 second boot)
//
// Time per loop iteration
// (31372 - 212) / 100000 = 311 [ns]
// (3390  - 212) / 10000  = 317 [ns]
// (1800  - 212) / 5000   = 317 [ns]
// (846   - 212) / 2000   = 317 [ns]
// 317 [ns] / 13 [ns] = 24.4 [cycles]
// 1 loop is ~24.3 cycles @ 78MHz processor speed with 13 nanosecond CPU cycle
//
// Boot time per delay
// 10000 microseconds - 1.200 seconds
//  5000 microseconds - 0.780 seconds
//  2000 microseconds - 0.485 seconds
//  1000 microseconds - 0.390 seconds
//   100 microseconds - 0.308 seconds
//     0 microseconds - 0.212 seconds

// Delay parameters in microseconds
// Note: These parameters equate to roughly 0.395 second boot time
#define MIN_DELAY_US    (100U)
#define MAX_DELAY_US    (2000U)
#define CYCLES_PER_LOOP (25)

static struct {
  secutils_api_t api;
  unsigned int glitch_counter;
  secure_bool_t initialized;
} secutils_priv;

SHARED_TASK_DATA bool _secutils_fixed_true = true;
SHARED_TASK_DATA volatile bool* secutils_fixed_true = &_secutils_fixed_true;

void secutils_init(secutils_api_t api) {
  if (secutils_priv.initialized == SECURE_TRUE) {
    return;
  }

  secutils_priv.api = api;
  secutils_priv.glitch_counter = 0;
  secutils_priv.initialized = SECURE_TRUE;
}

void secure_glitch_detect(void) {
  ASSERT(secutils_priv.initialized == SECURE_TRUE);
  ASSERT(&secutils_priv.api != NULL);
  ASSERT(secutils_priv.api.detect_glitch != NULL);

  secutils_priv.glitch_counter++;
  secutils_priv.api.detect_glitch();
}

unsigned int secure_glitch_get_count(void) {
  ASSERT(secutils_priv.initialized == SECURE_TRUE);

  return secutils_priv.glitch_counter;
}

void __secure_glitch_random_delay(void) {
  ASSERT(secutils_priv.initialized == SECURE_TRUE);
  ASSERT(&secutils_priv.api != NULL);
  ASSERT(secutils_priv.api.secure_random != NULL);

  // Freq is CPU cycles per second. Divide by 1,000,000 to get cycles per microsecond. (us)
  uint32_t cpu_freq_hz = secutils_priv.api.cpu_freq();
  uint32_t cycles_per_us = cpu_freq_hz / 1000000;

  // This ranges from 0 - 65535 (16-bits) ticks
  unsigned int random_value = secutils_priv.api.secure_random();

  // Map the random range to our defined ranged
  // Math is: random_value / 65535 = us_to_wait / (MAX_DELAY - MIN_DELAY). Solve for us_to_wait.
  uint32_t us_to_wait = (random_value * (MAX_DELAY_US - MIN_DELAY_US) / 65535) + (MIN_DELAY_US);

  // Compute the total loops required
  unsigned int loops = us_to_wait * cycles_per_us / CYCLES_PER_LOOP;

  // Delay!
  volatile unsigned int i;
  volatile unsigned int j;
  for (i = 0, j = loops; i < loops; i++, j--) {
    if (i + j != loops) {
      secure_glitch_detect();
    }
  }

  // Check the final values
  SECURE_IF_FAILIN(i != loops) { secure_glitch_detect(); }
}
