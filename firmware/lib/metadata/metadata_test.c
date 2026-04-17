#include "metadata.h"
#include "msgpack.h"

#include <criterion/criterion.h>

#include <stdbool.h>
#include <stdint.h>
#include <string.h>

void _putchar(char c) {
  (void)c;
}
bool rtos_in_isr(void) {
  return false;
}

#define METADATA_HEADER_SIZE 6
#define TEST_BUFFER_SIZE     512

char bl_metadata_size[1];
char bl_metadata_page[1];
char app_a_metadata_size[1];
char app_a_metadata_page[1];
char app_b_metadata_size[1];
char app_b_metadata_page[1];
char active_slot[1];

static size_t write_test_metadata(uint8_t* buffer, const char* git_id, const char* git_branch) {
  cmp_ctx_t cmp = {0};
  msgpack_mem_access_t access = {0};
  msgpack_mem_access_rw_init(&cmp, &access, &buffer[METADATA_HEADER_SIZE],
                             TEST_BUFFER_SIZE - METADATA_HEADER_SIZE);

  cr_assert(cmp_write_map(&cmp, 2));
  cr_assert(cmp_write_str(&cmp, "git_id", strlen("git_id")));
  cr_assert(cmp_write_str(&cmp, git_id, strlen(git_id)));
  cr_assert(cmp_write_str(&cmp, "git_branch", strlen("git_branch")));
  cr_assert(cmp_write_str(&cmp, git_branch, strlen(git_branch)));

  return METADATA_HEADER_SIZE + access.index;
}

Test(metadata, reads_max_length_git_strings) {
  char git_id[METADATA_GIT_STR_MAX_LEN + 1] = {0};
  char git_branch[METADATA_GIT_STR_MAX_LEN + 1] = {0};
  uint8_t buffer[TEST_BUFFER_SIZE] = {0};

  memset(git_id, 'a', METADATA_GIT_STR_MAX_LEN);
  memset(git_branch, 'b', METADATA_GIT_STR_MAX_LEN);

  size_t metadata_size = write_test_metadata(buffer, git_id, git_branch);

  metadata_t metadata = {0};
  cr_assert_eq(metadata_read(&metadata, buffer, metadata_size), METADATA_VALID);
  cr_assert_str_eq(metadata.git.id, git_id);
  cr_assert_str_eq(metadata.git.branch, git_branch);
}
