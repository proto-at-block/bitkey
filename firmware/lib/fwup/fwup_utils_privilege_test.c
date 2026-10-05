#include "fwup_utils.h"
#include "metadata.h"

#include <criterion/criterion.h>
#include <sys/mman.h>
#include <sys/types.h>
#include <sys/wait.h>

#include <signal.h>
#include <stdbool.h>
#include <stddef.h>
#include <string.h>
#include <unistd.h>

#ifndef MAP_ANONYMOUS
#define MAP_ANONYMOUS MAP_ANON
#endif

static void* protected_page = NULL;
static size_t protected_page_size = 0;
static volatile sig_atomic_t privileged = 0;

void fwup_utils_test_set_privileged(bool enable) {
  const int protection = PROT_READ | (enable ? PROT_WRITE : 0);
  if (mprotect(protected_page, protected_page_size, protection) != 0) {
    _exit(2);
  }
  privileged = enable;
}

bool fwup_utils_test_is_privileged(void) {
  return privileged != 0;
}

metadata_result_t metadata_get_active_slot(metadata_t* meta, fwpb_firmware_slot* slot) {
  memset(meta, 0, sizeof(*meta));
  meta->version.major = 1;
  meta->version.minor = 2;
  meta->version.patch = 3;
  *slot = fwpb_firmware_slot_SLOT_A;
  return METADATA_VALID;
}

metadata_result_t metadata_get(metadata_target_t target, metadata_t* meta) {
  if (target != META_TGT_APP_B) {
    _exit(2);
  }
  memset(meta, 0, sizeof(*meta));
  meta->version.major = 4;
  meta->version.minor = 5;
  meta->version.patch = 6;
  return METADATA_VALID;
}

static void handle_protection_fault(int signal_number) {
  (void)signal_number;
  _exit(privileged ? 2 : 0);
}

typedef bool (*version_getter_t)(fwpb_semver* version_out);

static void map_protected_page(void) {
  const long page_size = sysconf(_SC_PAGESIZE);
  cr_assert_gt(page_size, 0);

  protected_page_size = (size_t)page_size;
  protected_page =
    mmap(NULL, protected_page_size, PROT_READ | PROT_WRITE, MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
  cr_assert_neq(protected_page, MAP_FAILED);

  memset(protected_page, 0, sizeof(fwpb_semver));
}

static void unmap_protected_page(void) {
  cr_assert_eq(munmap(protected_page, protected_page_size), 0);
  protected_page = NULL;
  protected_page_size = 0;
}

static void assert_caller_buffer_is_not_written_while_privileged(version_getter_t getter) {
  map_protected_page();
  fwup_utils_test_set_privileged(false);

  const pid_t child = fork();
  cr_assert_neq(child, -1);
  if (child == 0) {
    signal(SIGSEGV, handle_protection_fault);
    signal(SIGBUS, handle_protection_fault);

    (void)getter(protected_page);
    _exit(1);
  }

  int status = 0;
  cr_assert_eq(waitpid(child, &status, 0), child);
  cr_assert(WIFEXITED(status), "getter child terminated with status %d", status);
  cr_assert_eq(WEXITSTATUS(status), 0, "getter wrote the caller buffer while privileged");

  unmap_protected_page();
}

static void assert_privileged_caller_is_preserved(version_getter_t getter,
                                                  const fwpb_semver expected) {
  map_protected_page();
  fwup_utils_test_set_privileged(true);

  cr_assert(getter(protected_page));
  cr_assert(fwup_utils_test_is_privileged());
  cr_assert(fwup_semver_equals(protected_page, &expected));

  fwup_utils_test_set_privileged(false);
  unmap_protected_page();
}

Test(fwup_utils_privilege, self_version_does_not_write_caller_buffer_while_privileged) {
  assert_caller_buffer_is_not_written_while_privileged(fwup_get_self_version);
}

Test(fwup_utils_privilege, target_version_does_not_write_caller_buffer_while_privileged) {
  assert_caller_buffer_is_not_written_while_privileged(fwup_get_target_version);
}

Test(fwup_utils_privilege, self_version_preserves_privileged_caller) {
  assert_privileged_caller_is_preserved(fwup_get_self_version,
                                        (fwpb_semver){.major = 1, .minor = 2, .patch = 3});
}

Test(fwup_utils_privilege, target_version_preserves_privileged_caller) {
  assert_privileged_caller_is_preserved(fwup_get_target_version,
                                        (fwpb_semver){.major = 4, .minor = 5, .patch = 6});
}
