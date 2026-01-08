    .syntax unified
    .arch armv8-m.main

    .text

    .thumb_func
    .global bootload_trampoline

bootload_trampoline:
    ldr     r3, [r0]            @ R0 contains a pointer to the address of the vector table.
    mov     sp, r3              @ Update stack pointer to initial stack pointer from the vector table.
    add     r0, #4              @ Offset R0 to account for the initial stack pointer.
    ldr     r3, [r0]            @ Load into R3 the address of the reset handler.
    bx      r3                  @ Jump to reset handler.
