#pragma once

#ifdef __GNUC__

#define UNUSED(x) UNUSED_##x __attribute__((__unused__))
#define USED      __attribute__((used))
#define PACKED    __attribute__((__packed__))

#ifdef EMBEDDED_BUILD
#define RAMFUNC          __attribute__((section(".ram")))
#define SYSCALL          __attribute__((section("freertos_system_calls")))
#define SHARED_TASK_DATA __attribute__((section(".shared_task_data")))
#define SHARED_TASK_BSS  __attribute__((section(".shared_task_bss")))
#define FWUP_TASK_DATA   __attribute__((section(".fwup_task_data")))
#define NFC_TASK_DATA    __attribute__((section(".nfc_task_data")))
#define LED_TASK_DATA    __attribute__((section(".led_task_data")))
#define UI_TASK_DATA     __attribute__((section(".ui_task_data")))
#define USART_TASK_DATA  __attribute__((section(".usart_task_data")))
#define PERIPHERALS_DATA __attribute__((section(".peripherals_data")))
#define SECTION(x)       __attribute__((section(x)))
#else
#define RAMFUNC
#define SYSCALL
#define SHARED_TASK_DATA
#define SHARED_TASK_BSS
#define FWUP_TASK_DATA
#define NFC_TASK_DATA
#define UI_TASK_DATA
#define LED_TASK_DATA
#define USART_TASK_DATA
#define PERIPHERALS_DATA
#define SECTION(x)
#endif

#define CLEANUP(x) __attribute__((__cleanup__(x)))

#define NO_RETURN __attribute__((noreturn))

#ifdef __clang__
#define NO_OPTIMIZE
#define NO_STACK_CANARY __attribute__((no_stack_protector))
#else
#define NO_OPTIMIZE     __attribute__((optimize("O0")))
#define NO_STACK_CANARY __attribute__((__optimize__("no-stack-protector")))
#endif

#else

#define UNUSED(x) UNUSED_##x
#define RAMFUNC
#define SYSCALL
#define SHARED_TASK_DATA
#define SHARED_TASK_BSS
#define FWUP_TASK_DATA
#define NFC_TASK_DATA
#define UI_TASK_DATA
#define LED_TASK_DATA
#define USART_TASK_DATA
#define PERIPHERALS_DATA
#define PACKED

#endif

#ifdef EMBEDDED_BUILD
#define STATIC_VISIBLE_FOR_TESTING     static  // A "static" visible only in native (test) builds.
#define EXTERN_VISIBLE_FOR_TESTING(x)  // A declaration visible only in native (test) builds.
#else
#define STATIC_VISIBLE_FOR_TESTING
#define EXTERN_VISIBLE_FOR_TESTING(x) extern x
#endif
