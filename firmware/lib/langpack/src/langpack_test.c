#include "fff.h"
#include "langpack.h"
#include "langpack_ids.h"

#include <criterion/criterion.h>

#include <stddef.h>
#include <stdint.h>

DEFINE_FFF_GLOBALS;

void setup(void) {
  langpack_load_default();
}

void teardown(void) {
  // No-op.
}

TestSuite(langpack, .init = setup, .fini = teardown);

Test(langpack, langpack_get_string) {
  const char* string = langpack_get_string(LANGPACK_ID_TEST_STRING);
  cr_assert_str_eq(string, "Hello World");
}
