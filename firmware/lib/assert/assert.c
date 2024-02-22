#ifdef EMBEDDED_BUILD

#include "assert.h"

static assert_handler_t SHARED_TASK_DATA __assert_handler;

void assert_init(assert_handler_t handler_cb) {
  __assert_handler = handler_cb;
}

void _assert_handler(void) {
  void* pc;
  __asm__ __volatile__("mov %0, pc" : "=r"(pc));
  void* lr = __builtin_return_address(0);
  __assert_handler(pc, lr);

  while (1) {
  }
}

#endif
