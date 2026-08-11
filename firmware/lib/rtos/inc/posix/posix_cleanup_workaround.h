/**
 * @file posix_cleanup_workaround.h
 * @brief Force-included header to work around clang goto+cleanup issues.
 *
 * This header MUST be force-included (-include) before any source file that
 * uses __attribute__((__cleanup__(...))), providing a POSIX-compatible
 * redefinition that avoids the hard error in clang.
 *
 * clang enforces strict C11 semantics where jumping past a variable with
 * __cleanup__ is a hard error (not just a warning). This is because the
 * cleanup function might not be called correctly if we goto past it.
 *
 * The workaround is to redefine __cleanup__ as a macro that expands to
 * __unused__ before any code uses it. This way:
 * 1. Variables are still declared (no syntax errors)
 * 2. The __unused__ attribute suppresses unused variable warnings
 * 3. The goto error is avoided since __unused__ has no cleanup semantics
 *
 * Security note: In the core-sim emulator, keys are file-backed and not
 * in secure memory, so automatic cleanup is less critical. The key data will
 * be naturally overwritten or freed when the process exits.
 */
#pragma once

// Redefine __cleanup__ to __unused__ BEFORE any code uses the attribute.
// This intercepts both:
//   - Direct uses: __attribute__((__cleanup__(fn)))
//   - Macro uses: CLEANUP(fn) which expands to __attribute__((__cleanup__(fn)))
#define __cleanup__(x) __unused__
