# POSIX RTOS shim — status and recommendation

`rtos_posix.c` implements the `lib/rtos` API (~41 functions: threads,
queues, mutexes, semaphores, event groups, timers) directly on pthreads
(thread notifications are implemented separately in core-sim's
`task_stubs.c`). It exists because the official FreeRTOS POSIX simulator port
(`third-party/FreeRTOS/portable/ThirdParty/GCC/Posix`) could not run the
simulator's tasks — the original author noted "tasks blocked instead of
switching between them" and asked for further exploration. This document
records that exploration and the resulting recommendation.

## Why the official FreeRTOS POSIX port fails for core-sim

### Reproduction (vendored kernel V10.6.2, macOS arm64)

Two independent failures reproduce with a ~60-line program built against
`third-party/FreeRTOS`:

1. **The vendored port does not compile on macOS.**
   `port.c:154` assigns `mach_vm_round_page()` (a `mach_vm_offset_t`) to a
   `StackType_t *` — a hard `-Wint-conversion` error under clang. This was
   fixed upstream in FreeRTOS-Kernel PR #957 ("Fix MacOS Posix port",
   Jan 2024), which postdates the vendored V10.6.2.

2. **With the compile fix applied, a task that blocks on a native host
   primitive starves all lower-priority tasks forever.**
   Repro: task A (priority 4) does `pthread_cond_wait()` on a condvar that
   is never signaled — exactly how `core-sim`'s `bio_sim.c` implements
   `bio_wait_for_finger_blocking()`. Task B (priority 2) prints once per
   second via `vTaskDelay()`. Under the official port, B never runs: the
   simulated scheduler only knows about blocking through FreeRTOS APIs, so
   A remains the highest-priority READY task and is "scheduled" forever
   while actually parked inside the host kernel.
   Control: changing A to block via `vTaskSuspend(NULL)` makes B run
   normally — the port itself is fine; it is core-sim's *native blocking*
   that is incompatible.

```
# repro commands (port_fixed.c = vendored port.c + the PR #957 cast fix)
clang -O0 -g -I. -I$FR/include -I$FR/portable/ThirdParty/GCC/Posix \
  -I$FR/portable/ThirdParty/GCC/Posix/utils \
  main.c $FR/tasks.c $FR/queue.c $FR/list.c $FR/timers.c \
  $FR/portable/MemMang/heap_4.c port_fixed.c \
  $FR/portable/ThirdParty/GCC/Posix/utils/wait_for_event.c -lpthread -o repro
./repro   # "worker alive" never prints
```

This is not a bug to fix but the port's documented model: tasks run one at
a time on suspended pthreads, the tick is a signal delivered to the running
task's thread, and "FreeRTOS tasks using any native blocking mechanism may
be perceived as ready by the simulated scheduler". Threads not created by
the kernel must not call FreeRTOS APIs (newer kernels *assert* on this).

### Does a newer FreeRTOS fix it?

No. FreeRTOS-Kernel 11.x improves the POSIX port — PR #957 (macOS
compile), #1103 (`configUSE_TIME_SLICING` actually honored), #1233 (robust
mutexes to avoid deadlocks), #1223/#1238/#1247 (defined handling/asserts
for non-FreeRTOS pthreads) — but the scheduling model above is unchanged
and is inherent to the port's design. core-sim is built around host
blocking and host threads:

- `bio_sim.c` blocks task threads on host condvars (finger simulation),
- the main thread (not a FreeRTOS task) runs `select()` over stdin and the
  ui-simulate socket and calls into IPC,
- `sim_persistence.c` / `wallet_emulator.c` do synchronous file I/O from
  task context,
- OpenSSL-based crypto runs inside tasks.

Migrating to the official port means rewriting all of those as
event-driven interactions bridged through FreeRTOS primitives by a
dedicated I/O reactor thread, plus auditing every task for accidental
native blocking — a redesign of the simulator's integration strategy, not
a port swap. Upgrading `third-party/FreeRTOS` does not change this
conclusion (and the vendored copy is shared with the real firmware, so it
should be upgraded on its own schedule, not for the simulator).

## Recommendation

Keep the pthread shim and promote it to a first-class supported `lib/rtos`
platform:

1. **Semantics statement (this file).** The shim provides *host-preemptive*
   semantics: every rtos thread is a real pthread, scheduling is the host
   kernel's, `rtos_thread_priority_t` is advisory (not enforced), and tasks
   genuinely run in parallel. Firmware code that relies on FreeRTOS
   single-core implicit serialization may behave differently — this is a
   deliberate fidelity trade-off in exchange for native blocking being
   safe, which the simulator's design depends on. Concurrency bugs the shim
   surfaces are usually real bugs.
   Known gap: `rtos_timer` is poll-only — callbacks registered via
   `rtos_timer_create_static()` are stored but never fired (consumers like
   lib/sleep's auto power-down callback simply never trigger in the sim).
   Promote to a timer thread if a simulated flow ever needs it.
2. **Unit tests.** Add `rtos_posix_test.c` (Criterion, posix-only meson
   `test()` in `lib/rtos/meson.build`) covering: queue FIFO order,
   blocking send/recv with timeout, mutex lock/unlock/trylock across
   threads, counting semaphore give/take, event-group set/wait/clear with
   waiter wakeup, `rtos_thread_sleep`/`systime` monotonicity,
   timer start/stop/restart and callback delivery, and notification
   signal/wait. These pin the API contracts that firmware tasks rely on.
3. **CI.** The test registers with meson, so `inv test` (and the host CI
   job) picks it up automatically.

Revisit only if a future need arises for tick-accurate scheduling fidelity
on the host (e.g. reproducing priority-inversion bugs), in which case the
official port could host a *separate*, I/O-free build profile rather than
replacing the shim.
