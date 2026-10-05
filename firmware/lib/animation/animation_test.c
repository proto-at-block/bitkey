#include "animation.h"

#include <criterion/criterion.h>

// Host assertions exit with 9876, truncated to an 8-bit process status.
#define ASSERT_EXIT_STATUS (9876 & 0xff)

Test(animation_get, rejects_end_sentinel, .exit_code = ASSERT_EXIT_STATUS) {
  (void)animation_get(ANI_MAX);
}

Test(animation_get, rejects_negative_name, .exit_code = ASSERT_EXIT_STATUS) {
  (void)animation_get((animation_name_t)-1);
}
