#ifdef EMBEDDED_BUILD

#include "assert.h"

NO_RETURN void __attribute__((weak)) assert_platform_handler(void* pc, void* lr) {
  (void)pc;
  (void)lr;

  while (true) {
  }
}

void _assert_handler(void) {
  void* pc;
  __asm__ __volatile__("mov %0, pc" : "=r"(pc));
  void* lr = __builtin_return_address(0);
  assert_platform_handler(pc, lr);
}

#endif
