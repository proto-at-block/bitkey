#pragma once

#include "attributes.h"
#include "mcu_reset.h"
#include "secure_rng.h"

#include <stdint.h>

_Static_assert(sizeof(uintptr_t) == sizeof(uint32_t), "unexpected uintptr_t size");

// https://www.redhat.com/en/blog/security-technologies-stack-smashing-protection-stackguard
#define STACK_CANARY_FALLBACK_VALUE 0x000d0aff

extern uintptr_t __stack_chk_guard;

NO_RETURN void __wrap___stack_chk_fail(void);

inline __attribute__((always_inline)) NO_STACK_CANARY void canary_init(void) {
  uintptr_t stack_guard = 0u;

  if (crypto_random((uint8_t*)&stack_guard, sizeof(stack_guard))) {
    // force terminator canary over a null canary
    if (stack_guard == 0u) {
      stack_guard = STACK_CANARY_FALLBACK_VALUE;
    }
    __stack_chk_guard = stack_guard;
    stack_guard = 0;
    // success case
    return;
  }
  // fail case will reset
  mcu_reset_with_reason(MCU_RESET_STACK_CANARY_NOT_SET);
}
