#include "secutils.h"

#include <criterion/criterion.h>

static unsigned int detect_glitch_call_count = 0;
static uint32_t cpu_freq_hz = 78000000U;

static void detect_glitch_cb(void) {
  detect_glitch_call_count++;
}

static uint16_t secure_random_cb(void) {
  return 0U;
}

static uint32_t cpu_freq_cb(void) {
  return cpu_freq_hz;
}

static void setup(void) {
  detect_glitch_call_count = 0;
  cpu_freq_hz = 78000000U;

  secutils_init((secutils_api_t){
    .detect_glitch = detect_glitch_cb,
    .secure_random = secure_random_cb,
    .cpu_freq = cpu_freq_cb,
  });
}

Test(secutils, random_delay_detects_invalid_cpu_frequency, .init = setup) {
  cpu_freq_hz = 999999U;
  secure_glitch_random_delay();
  cr_assert_eq(secure_glitch_get_count(), 1U);
  cr_assert_eq(detect_glitch_call_count, 1U);

  cpu_freq_hz = 78000000U;
  secure_glitch_random_delay();
  cr_assert_eq(secure_glitch_get_count(), 1U);
  cr_assert_eq(detect_glitch_call_count, 1U);

  cpu_freq_hz = 0U;
  secure_glitch_random_delay();
  cr_assert_eq(secure_glitch_get_count(), 2U);
  cr_assert_eq(detect_glitch_call_count, 2U);
}
