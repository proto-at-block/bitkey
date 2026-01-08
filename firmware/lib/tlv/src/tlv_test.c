#include "fff.h"
#include "tlv.h"
#include "tlv_impl.h"

#include <criterion/criterion.h>

#include <string.h>

DEFINE_FFF_GLOBALS;

// Test buffer size
#define TEST_BUFFER_SIZE 1024

// Helper function to create a test buffer
static uint8_t test_buffer[TEST_BUFFER_SIZE];

// Setup function to clear the buffer before each test
void setup(void) {
  memset(test_buffer, 0, sizeof(test_buffer));
}

// Teardown function
void teardown(void) {
  // Nothing to tear down
}

// ============================================================================
// tlv_init tests
// ============================================================================

TestSuite(tlv_init, .init = setup, .fini = teardown);

Test(tlv_init, null_tlv_returns_error) {
  tlv_result_t result = tlv_init(NULL, test_buffer, TEST_BUFFER_SIZE);
  cr_assert_eq(result, TLV_ERR_INVALID_PARAM);
}

Test(tlv_init, null_buffer_returns_error) {
  tlv_t tlv;
  tlv_result_t result = tlv_init(&tlv, NULL, TEST_BUFFER_SIZE);
  cr_assert_eq(result, TLV_ERR_INVALID_PARAM);
}

Test(tlv_init, zero_capacity_returns_error) {
  tlv_t tlv;
  tlv_result_t result = tlv_init(&tlv, test_buffer, 0);
  cr_assert_eq(result, TLV_ERR_INVALID_PARAM);
}

Test(tlv_init, empty_buffer_initializes_successfully) {
  tlv_t tlv;
  tlv_result_t result = tlv_init(&tlv, test_buffer, TEST_BUFFER_SIZE);

  cr_assert_eq(result, TLV_SUCCESS);
  cr_assert_eq(tlv.buffer, test_buffer);
  cr_assert_eq(tlv.capacity, TEST_BUFFER_SIZE);
  cr_assert_eq(tlv.size, 0);
}

Test(tlv_init, existing_valid_tlv_data_is_recognized) {
  // Manually create a valid TLV structure.
  tlv_tag_t* tag = (tlv_tag_t*)test_buffer;
  tag->tag = 0x12345678;
  tag->length = 4;
  memcpy(tag->value, "test", 4);

  tlv_t tlv;
  tlv_result_t result = tlv_init(&tlv, test_buffer, TEST_BUFFER_SIZE);

  cr_assert_eq(result, TLV_SUCCESS);
  cr_assert_eq(tlv.size, sizeof(tlv_tag_t) + 4);
}

Test(tlv_init, corrupted_tlv_data_returns_error) {
  // Create a corrupted TLV (length extends beyond capacity).
  tlv_tag_t* tag = (tlv_tag_t*)test_buffer;
  tag->tag = 0x12345678;
  tag->length = TEST_BUFFER_SIZE;

  tlv_t tlv;
  tlv_result_t result = tlv_init(&tlv, test_buffer, TEST_BUFFER_SIZE);

  cr_assert_eq(result, TLV_ERR_CORRUPTED);
  cr_assert_eq(tlv.size, 0);
}

Test(tlv_init, end_sentinel_stops_validation) {
  // Create a TLV with end sentinel.
  tlv_tag_t* tag1 = (tlv_tag_t*)test_buffer;
  tag1->tag = 0x11111111;
  tag1->length = 4;
  memcpy(tag1->value, "data", 4);

  // Add end sentinel.
  size_t offset = sizeof(tlv_tag_t) + 4;
  tlv_tag_t* tag2 = (tlv_tag_t*)&test_buffer[offset];
  tag2->tag = TLV_TAG_END_SENTINEL;
  tag2->length = 0;

  tlv_t tlv;
  tlv_result_t result = tlv_init(&tlv, test_buffer, TEST_BUFFER_SIZE);

  cr_assert_eq(result, TLV_SUCCESS);
  cr_assert_eq(tlv.size, offset);
}

// ============================================================================
// tlv_add tests
// ============================================================================

TestSuite(tlv_add, .init = setup, .fini = teardown);

Test(tlv_add, null_tlv_returns_error) {
  uint8_t value[] = {1, 2, 3, 4};
  tlv_result_t result = tlv_add(NULL, 0x1234, value, sizeof(value));
  cr_assert_eq(result, TLV_ERR_INVALID_PARAM);
}

Test(tlv_add, null_value_with_nonzero_length_returns_error) {
  tlv_t tlv;
  tlv_init(&tlv, test_buffer, TEST_BUFFER_SIZE);

  tlv_result_t result = tlv_add(&tlv, 0x1234, NULL, 10);
  cr_assert_eq(result, TLV_ERR_INVALID_PARAM);
}

Test(tlv_add, zero_length_value_returns_error) {
  tlv_t tlv;
  tlv_init(&tlv, test_buffer, TEST_BUFFER_SIZE);

  uint8_t value[] = {1, 2, 3, 4};
  tlv_result_t result = tlv_add(&tlv, 0x1234, value, 0);
  cr_assert_eq(result, TLV_ERR_INVALID_PARAM);
}

Test(tlv_add, sentinel_tag_returns_error) {
  tlv_t tlv;
  tlv_init(&tlv, test_buffer, TEST_BUFFER_SIZE);

  uint8_t value[] = {1, 2, 3, 4};
  tlv_result_t result = tlv_add(&tlv, TLV_TAG_END_SENTINEL, value, sizeof(value));
  cr_assert_eq(result, TLV_ERR_INVALID_PARAM);
}

Test(tlv_add, single_tag_adds_successfully) {
  tlv_t tlv;
  tlv_init(&tlv, test_buffer, TEST_BUFFER_SIZE);

  uint8_t value[] = {0xAA, 0xBB, 0xCC, 0xDD};
  tlv_result_t result = tlv_add(&tlv, 0x12345678, value, sizeof(value));

  cr_assert_eq(result, TLV_SUCCESS);
  cr_assert_eq(tlv.size, sizeof(tlv_tag_t) + sizeof(value));

  // Verify the tag was written correctly.
  tlv_tag_t* tag = (tlv_tag_t*)test_buffer;
  cr_assert_eq(tag->tag, 0x12345678);
  cr_assert_eq(tag->length, sizeof(value));
  cr_assert_eq(memcmp(tag->value, value, sizeof(value)), 0);
}

Test(tlv_add, multiple_tags_add_successfully) {
  tlv_t tlv;
  tlv_init(&tlv, test_buffer, TEST_BUFFER_SIZE);

  uint8_t value1[] = {1, 2, 3, 4};
  uint8_t value2[] = {5, 6, 7, 8, 9, 10};
  uint8_t value3[] = {11, 12};

  cr_assert_eq(tlv_add(&tlv, 0x1111, value1, sizeof(value1)), TLV_SUCCESS);
  cr_assert_eq(tlv_add(&tlv, 0x2222, value2, sizeof(value2)), TLV_SUCCESS);
  cr_assert_eq(tlv_add(&tlv, 0x3333, value3, sizeof(value3)), TLV_SUCCESS);

  size_t expected_size = 3 * sizeof(tlv_tag_t) + sizeof(value1) + sizeof(value2) + sizeof(value3);
  cr_assert_eq(tlv.size, expected_size);
}

Test(tlv_add, duplicate_tag_returns_error) {
  tlv_t tlv;
  tlv_init(&tlv, test_buffer, TEST_BUFFER_SIZE);

  uint8_t value1[] = {1, 2, 3, 4};
  uint8_t value2[] = {5, 6, 7, 8};

  cr_assert_eq(tlv_add(&tlv, 0x1234, value1, sizeof(value1)), TLV_SUCCESS);
  cr_assert_eq(tlv_add(&tlv, 0x1234, value2, sizeof(value2)), TLV_ERR_DUPLICATE);
}

Test(tlv_add, insufficient_space_returns_error) {
  uint8_t small_buffer[16];
  tlv_t tlv;
  tlv_init(&tlv, small_buffer, sizeof(small_buffer));

  uint8_t large_value[128];
  tlv_result_t result = tlv_add(&tlv, 0x1234, large_value, sizeof(large_value));

  cr_assert_eq(result, TLV_ERR_NO_SPACE);
}

Test(tlv_add, exact_fit_works) {
  // Create a buffer that can fit exactly one tag.
  size_t value_size = 10;
  size_t buffer_size = sizeof(tlv_tag_t) + value_size;
  uint8_t small_buffer[buffer_size];

  tlv_t tlv;
  tlv_init(&tlv, small_buffer, buffer_size);

  uint8_t value[10] = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
  tlv_result_t result = tlv_add(&tlv, 0x1234, value, sizeof(value));

  cr_assert_eq(result, TLV_SUCCESS);
  cr_assert_eq(tlv.size, buffer_size);
}

// ============================================================================
// tlv_lookup tests
// ============================================================================

TestSuite(tlv_lookup, .init = setup, .fini = teardown);

Test(tlv_lookup, null_tlv_returns_error) {
  const uint8_t* value;
  uint16_t length;
  tlv_result_t result = tlv_lookup(NULL, 0x1234, &value, &length);
  cr_assert_eq(result, TLV_ERR_INVALID_PARAM);
}

Test(tlv_lookup, null_value_pointer_returns_error) {
  tlv_t tlv;
  tlv_init(&tlv, test_buffer, TEST_BUFFER_SIZE);

  uint16_t length;
  tlv_result_t result = tlv_lookup(&tlv, 0x1234, NULL, &length);
  cr_assert_eq(result, TLV_ERR_INVALID_PARAM);
}

Test(tlv_lookup, null_length_pointer_returns_error) {
  tlv_t tlv;
  tlv_init(&tlv, test_buffer, TEST_BUFFER_SIZE);

  const uint8_t* value;
  tlv_result_t result = tlv_lookup(&tlv, 0x1234, &value, NULL);
  cr_assert_eq(result, TLV_ERR_INVALID_PARAM);
}

Test(tlv_lookup, nonexistent_tag_returns_not_found) {
  tlv_t tlv;
  tlv_init(&tlv, test_buffer, TEST_BUFFER_SIZE);

  const uint8_t* value;
  uint16_t length;
  tlv_result_t result = tlv_lookup(&tlv, 0x1234, &value, &length);

  cr_assert_eq(result, TLV_ERR_NOT_FOUND);
}

Test(tlv_lookup, existing_tag_is_found) {
  tlv_t tlv;
  tlv_init(&tlv, test_buffer, TEST_BUFFER_SIZE);

  uint8_t test_value[] = {0xAA, 0xBB, 0xCC, 0xDD};
  tlv_add(&tlv, 0x12345678, test_value, sizeof(test_value));

  const uint8_t* value;
  uint16_t length;
  tlv_result_t result = tlv_lookup(&tlv, 0x12345678, &value, &length);

  cr_assert_eq(result, TLV_SUCCESS);
  cr_assert_eq(length, sizeof(test_value));
  cr_assert_eq(memcmp(value, test_value, sizeof(test_value)), 0);
}

Test(tlv_lookup, finds_correct_tag_among_multiple) {
  tlv_t tlv;
  tlv_init(&tlv, test_buffer, TEST_BUFFER_SIZE);

  uint8_t value1[] = {1, 2, 3, 4};
  uint8_t value2[] = {5, 6, 7, 8, 9};
  uint8_t value3[] = {10, 11};

  tlv_add(&tlv, 0x1111, value1, sizeof(value1));
  tlv_add(&tlv, 0x2222, value2, sizeof(value2));
  tlv_add(&tlv, 0x3333, value3, sizeof(value3));

  // Look up the middle tag.
  const uint8_t* value;
  uint16_t length;
  tlv_result_t result = tlv_lookup(&tlv, 0x2222, &value, &length);

  cr_assert_eq(result, TLV_SUCCESS);
  cr_assert_eq(length, sizeof(value2));
  cr_assert_eq(memcmp(value, value2, sizeof(value2)), 0);
}

// ============================================================================
// tlv_update tests
// ============================================================================

TestSuite(tlv_update, .init = setup, .fini = teardown);

Test(tlv_update, null_tlv_returns_error) {
  uint8_t value[] = {1, 2, 3, 4};
  tlv_result_t result = tlv_update(NULL, 0x1234, value, sizeof(value));
  cr_assert_eq(result, TLV_ERR_INVALID_PARAM);
}

Test(tlv_update, null_value_with_nonzero_length_returns_error) {
  tlv_t tlv;
  tlv_init(&tlv, test_buffer, TEST_BUFFER_SIZE);

  tlv_result_t result = tlv_update(&tlv, 0x1234, NULL, 10);
  cr_assert_eq(result, TLV_ERR_INVALID_PARAM);
}

Test(tlv_update, zero_length_value_returns_error) {
  tlv_t tlv;
  tlv_init(&tlv, test_buffer, TEST_BUFFER_SIZE);

  uint8_t value[] = {1, 2, 3, 4};
  tlv_result_t result = tlv_update(&tlv, 0x1234, value, 0);
  cr_assert_eq(result, TLV_ERR_INVALID_PARAM);
}

Test(tlv_update, nonexistent_tag_returns_not_found) {
  tlv_t tlv;
  tlv_init(&tlv, test_buffer, TEST_BUFFER_SIZE);

  uint8_t value[] = {1, 2, 3, 4};
  tlv_result_t result = tlv_update(&tlv, 0x1234, value, sizeof(value));

  cr_assert_eq(result, TLV_ERR_NOT_FOUND);
}

Test(tlv_update, same_size_value_updates_in_place) {
  tlv_t tlv;
  tlv_init(&tlv, test_buffer, TEST_BUFFER_SIZE);

  uint8_t old_value[] = {1, 2, 3, 4};
  uint8_t new_value[] = {5, 6, 7, 8};

  tlv_add(&tlv, 0x1234, old_value, sizeof(old_value));
  size_t old_size = tlv.size;

  tlv_result_t result = tlv_update(&tlv, 0x1234, new_value, sizeof(new_value));

  cr_assert_eq(result, TLV_SUCCESS);

  // Size should not change.
  cr_assert_eq(tlv.size, old_size);

  // Verify the value was updated.
  const uint8_t* value;
  uint16_t length;
  tlv_lookup(&tlv, 0x1234, &value, &length);
  cr_assert_eq(memcmp(value, new_value, sizeof(new_value)), 0);
}

Test(tlv_update, smaller_value_compacts_buffer) {
  tlv_t tlv;
  tlv_init(&tlv, test_buffer, TEST_BUFFER_SIZE);

  uint8_t old_value[] = {1, 2, 3, 4, 5, 6, 7, 8};
  uint8_t new_value[] = {9, 10};

  tlv_add(&tlv, 0x1234, old_value, sizeof(old_value));
  size_t old_size = tlv.size;

  tlv_result_t result = tlv_update(&tlv, 0x1234, new_value, sizeof(new_value));

  cr_assert_eq(result, TLV_SUCCESS);

  // Size should decrease.
  cr_assert_lt(tlv.size, old_size);

  size_t expected_size = sizeof(tlv_tag_t) + sizeof(new_value);
  cr_assert_eq(tlv.size, expected_size);
}

Test(tlv_update, larger_value_expands_buffer) {
  tlv_t tlv;
  tlv_init(&tlv, test_buffer, TEST_BUFFER_SIZE);

  uint8_t old_value[] = {1, 2};
  uint8_t new_value[] = {3, 4, 5, 6, 7, 8, 9, 10};

  tlv_add(&tlv, 0x1234, old_value, sizeof(old_value));
  size_t old_size = tlv.size;

  tlv_result_t result = tlv_update(&tlv, 0x1234, new_value, sizeof(new_value));

  cr_assert_eq(result, TLV_SUCCESS);

  // Size should increase.
  cr_assert_gt(tlv.size, old_size);

  size_t expected_size = sizeof(tlv_tag_t) + sizeof(new_value);
  cr_assert_eq(tlv.size, expected_size);
}

Test(tlv_update, insufficient_space_for_larger_value_returns_error) {
  uint8_t small_buffer[32];
  tlv_t tlv;
  tlv_init(&tlv, small_buffer, sizeof(small_buffer));

  uint8_t old_value[] = {1, 2, 3};
  // Too large.
  uint8_t new_value[128];

  tlv_add(&tlv, 0x1234, old_value, sizeof(old_value));
  tlv_result_t result = tlv_update(&tlv, 0x1234, new_value, sizeof(new_value));

  cr_assert_eq(result, TLV_ERR_NO_SPACE);
}

Test(tlv_update, updates_first_tag_among_multiple) {
  tlv_t tlv;
  tlv_init(&tlv, test_buffer, TEST_BUFFER_SIZE);

  uint8_t value1[] = {1, 2, 3, 4};
  uint8_t value2[] = {5, 6, 7, 8};
  uint8_t value3[] = {9, 10, 11, 12};
  uint8_t new_value1[] = {99, 98, 97, 96};

  tlv_add(&tlv, 0x1111, value1, sizeof(value1));
  tlv_add(&tlv, 0x2222, value2, sizeof(value2));
  tlv_add(&tlv, 0x3333, value3, sizeof(value3));

  tlv_result_t result = tlv_update(&tlv, 0x1111, new_value1, sizeof(new_value1));

  cr_assert_eq(result, TLV_SUCCESS);

  // Verify first tag was updated.
  const uint8_t* value;
  uint16_t length;
  tlv_lookup(&tlv, 0x1111, &value, &length);
  cr_assert_eq(memcmp(value, new_value1, sizeof(new_value1)), 0);

  // Verify other tags are unchanged.
  tlv_lookup(&tlv, 0x2222, &value, &length);
  cr_assert_eq(memcmp(value, value2, sizeof(value2)), 0);
  tlv_lookup(&tlv, 0x3333, &value, &length);
  cr_assert_eq(memcmp(value, value3, sizeof(value3)), 0);
}

Test(tlv_update, updates_middle_tag_among_multiple) {
  tlv_t tlv;
  tlv_init(&tlv, test_buffer, TEST_BUFFER_SIZE);

  uint8_t value1[] = {1, 2, 3, 4};
  uint8_t value2[] = {5, 6, 7, 8};
  uint8_t value3[] = {9, 10, 11, 12};
  uint8_t new_value2[] = {88, 87, 86, 85};

  tlv_add(&tlv, 0x1111, value1, sizeof(value1));
  tlv_add(&tlv, 0x2222, value2, sizeof(value2));
  tlv_add(&tlv, 0x3333, value3, sizeof(value3));

  tlv_result_t result = tlv_update(&tlv, 0x2222, new_value2, sizeof(new_value2));

  cr_assert_eq(result, TLV_SUCCESS);

  // Verify middle tag was updated.
  const uint8_t* value;
  uint16_t length;
  tlv_lookup(&tlv, 0x2222, &value, &length);
  cr_assert_eq(memcmp(value, new_value2, sizeof(new_value2)), 0);

  // Verify other tags are unchanged.
  tlv_lookup(&tlv, 0x1111, &value, &length);
  cr_assert_eq(memcmp(value, value1, sizeof(value1)), 0);
  tlv_lookup(&tlv, 0x3333, &value, &length);
  cr_assert_eq(memcmp(value, value3, sizeof(value3)), 0);
}

// ============================================================================
// tlv_delete tests
// ============================================================================

TestSuite(tlv_delete, .init = setup, .fini = teardown);

Test(tlv_delete, null_tlv_returns_error) {
  tlv_result_t result = tlv_delete(NULL, 0x1234);
  cr_assert_eq(result, TLV_ERR_INVALID_PARAM);
}

Test(tlv_delete, nonexistent_tag_returns_not_found) {
  tlv_t tlv;
  tlv_init(&tlv, test_buffer, TEST_BUFFER_SIZE);

  tlv_result_t result = tlv_delete(&tlv, 0x1234);
  cr_assert_eq(result, TLV_ERR_NOT_FOUND);
}

Test(tlv_delete, single_tag_deletes_successfully) {
  tlv_t tlv;
  tlv_init(&tlv, test_buffer, TEST_BUFFER_SIZE);

  uint8_t value[] = {1, 2, 3, 4};
  tlv_add(&tlv, 0x1234, value, sizeof(value));

  tlv_result_t result = tlv_delete(&tlv, 0x1234);

  cr_assert_eq(result, TLV_SUCCESS);
  cr_assert_eq(tlv.size, 0);

  // Verify tag no longer exists.
  const uint8_t* lookup_value;
  uint16_t length;
  cr_assert_eq(tlv_lookup(&tlv, 0x1234, &lookup_value, &length), TLV_ERR_NOT_FOUND);
}

Test(tlv_delete, deletes_first_tag_among_multiple) {
  tlv_t tlv;
  tlv_init(&tlv, test_buffer, TEST_BUFFER_SIZE);

  uint8_t value1[] = {1, 2, 3, 4};
  uint8_t value2[] = {5, 6, 7, 8};
  uint8_t value3[] = {9, 10, 11, 12};

  tlv_add(&tlv, 0x1111, value1, sizeof(value1));
  tlv_add(&tlv, 0x2222, value2, sizeof(value2));
  tlv_add(&tlv, 0x3333, value3, sizeof(value3));

  tlv_result_t result = tlv_delete(&tlv, 0x1111);

  cr_assert_eq(result, TLV_SUCCESS);

  // Verify first tag is gone.
  const uint8_t* value;
  uint16_t length;
  cr_assert_eq(tlv_lookup(&tlv, 0x1111, &value, &length), TLV_ERR_NOT_FOUND);

  // Verify other tags still exist.
  cr_assert_eq(tlv_lookup(&tlv, 0x2222, &value, &length), TLV_SUCCESS);
  cr_assert_eq(memcmp(value, value2, sizeof(value2)), 0);
  cr_assert_eq(tlv_lookup(&tlv, 0x3333, &value, &length), TLV_SUCCESS);
  cr_assert_eq(memcmp(value, value3, sizeof(value3)), 0);
}

Test(tlv_delete, deletes_middle_tag_among_multiple) {
  tlv_t tlv;
  tlv_init(&tlv, test_buffer, TEST_BUFFER_SIZE);

  uint8_t value1[] = {1, 2, 3, 4};
  uint8_t value2[] = {5, 6, 7, 8};
  uint8_t value3[] = {9, 10, 11, 12};

  tlv_add(&tlv, 0x1111, value1, sizeof(value1));
  tlv_add(&tlv, 0x2222, value2, sizeof(value2));
  tlv_add(&tlv, 0x3333, value3, sizeof(value3));

  tlv_result_t result = tlv_delete(&tlv, 0x2222);

  cr_assert_eq(result, TLV_SUCCESS);

  // Verify middle tag is gone.
  const uint8_t* value;
  uint16_t length;
  cr_assert_eq(tlv_lookup(&tlv, 0x2222, &value, &length), TLV_ERR_NOT_FOUND);

  // Verify other tags still exist.
  cr_assert_eq(tlv_lookup(&tlv, 0x1111, &value, &length), TLV_SUCCESS);
  cr_assert_eq(memcmp(value, value1, sizeof(value1)), 0);
  cr_assert_eq(tlv_lookup(&tlv, 0x3333, &value, &length), TLV_SUCCESS);
  cr_assert_eq(memcmp(value, value3, sizeof(value3)), 0);
}

Test(tlv_delete, deletes_last_tag_among_multiple) {
  tlv_t tlv;
  tlv_init(&tlv, test_buffer, TEST_BUFFER_SIZE);

  uint8_t value1[] = {1, 2, 3, 4};
  uint8_t value2[] = {5, 6, 7, 8};
  uint8_t value3[] = {9, 10, 11, 12};

  tlv_add(&tlv, 0x1111, value1, sizeof(value1));
  tlv_add(&tlv, 0x2222, value2, sizeof(value2));
  tlv_add(&tlv, 0x3333, value3, sizeof(value3));

  tlv_result_t result = tlv_delete(&tlv, 0x3333);

  cr_assert_eq(result, TLV_SUCCESS);

  // Verify last tag is gone.
  const uint8_t* value;
  uint16_t length;
  cr_assert_eq(tlv_lookup(&tlv, 0x3333, &value, &length), TLV_ERR_NOT_FOUND);

  // Verify other tags still exist.
  cr_assert_eq(tlv_lookup(&tlv, 0x1111, &value, &length), TLV_SUCCESS);
  cr_assert_eq(memcmp(value, value1, sizeof(value1)), 0);
  cr_assert_eq(tlv_lookup(&tlv, 0x2222, &value, &length), TLV_SUCCESS);
  cr_assert_eq(memcmp(value, value2, sizeof(value2)), 0);
}

// ============================================================================
// Helper function tests
// ============================================================================

TestSuite(tlv_helpers, .init = setup, .fini = teardown);

Test(tlv_helpers, get_size_on_null_tlv_returns_zero) {
  size_t size = tlv_get_size(NULL);
  cr_assert_eq(size, 0);
}

Test(tlv_helpers, get_size_on_empty_buffer_returns_zero) {
  tlv_t tlv;
  tlv_init(&tlv, test_buffer, TEST_BUFFER_SIZE);

  size_t size = tlv_get_size(&tlv);
  cr_assert_eq(size, 0);
}

Test(tlv_helpers, get_size_returns_correct_size) {
  tlv_t tlv;
  tlv_init(&tlv, test_buffer, TEST_BUFFER_SIZE);

  uint8_t value1[] = {1, 2, 3, 4};
  uint8_t value2[] = {5, 6, 7, 8, 9, 10};

  tlv_add(&tlv, 0x1111, value1, sizeof(value1));
  tlv_add(&tlv, 0x2222, value2, sizeof(value2));

  size_t expected_size = 2 * sizeof(tlv_tag_t) + sizeof(value1) + sizeof(value2);
  cr_assert_eq(tlv_get_size(&tlv), expected_size);
}

Test(tlv_helpers, get_remaining_capacity_on_null_tlv_returns_zero) {
  size_t capacity = tlv_get_remaining_capacity(NULL);
  cr_assert_eq(capacity, 0);
}

Test(tlv_helpers, get_remaining_capacity_on_empty_buffer_returns_full_capacity) {
  tlv_t tlv;
  tlv_init(&tlv, test_buffer, TEST_BUFFER_SIZE);

  size_t capacity = tlv_get_remaining_capacity(&tlv);
  cr_assert_eq(capacity, TEST_BUFFER_SIZE);
}

Test(tlv_helpers, get_remaining_capacity_decreases_after_adding_tags) {
  tlv_t tlv;
  tlv_init(&tlv, test_buffer, TEST_BUFFER_SIZE);

  uint8_t value[] = {1, 2, 3, 4};
  tlv_add(&tlv, 0x1234, value, sizeof(value));

  size_t expected_remaining = TEST_BUFFER_SIZE - sizeof(tlv_tag_t) - sizeof(value);
  cr_assert_eq(tlv_get_remaining_capacity(&tlv), expected_remaining);
}

// ============================================================================
// Integration tests
// ============================================================================

Test(tlv_helpers, add_lookup_update_delete_integration) {
  uint8_t data[] = {
    0x0B, 0x24, 0x4F, 0x62, 0x34, 0x00, 0x59, 0x6F, 0x75, 0x20, 0x73, 0x75, 0x72, 0x65, 0x20, 0x61,
    0x62, 0x6F, 0x75, 0x74, 0x20, 0x74, 0x68, 0x61, 0x74, 0x3F, 0x20, 0x59, 0x6F, 0x75, 0x20, 0x73,
    0x75, 0x72, 0x65, 0x20, 0x61, 0x62, 0x6F, 0x75, 0x74, 0x20, 0x74, 0x68, 0x61, 0x74, 0x27, 0x73,
    0x20, 0x6E, 0x6F, 0x74, 0x20, 0x77, 0x68, 0x79, 0x3F, 0x00, 0x40, 0xB4, 0xFA, 0x3B, 0x28, 0x00,
    0x54, 0x72, 0x69, 0x70, 0x6C, 0x65, 0x73, 0x20, 0x6D, 0x61, 0x6B, 0x65, 0x73, 0x20, 0x69, 0x74,
    0x20, 0x73, 0x61, 0x66, 0x65, 0x2E, 0x20, 0x54, 0x72, 0x69, 0x70, 0x6C, 0x65, 0x73, 0x20, 0x69,
    0x73, 0x20, 0x62, 0x65, 0x73, 0x74, 0x2E, 0x00, 0x1F, 0x55, 0x26, 0xC6, 0xC1, 0x00, 0x35, 0x35,
    0x20, 0x62, 0x75, 0x72, 0x67, 0x65, 0x72, 0x73, 0x2C, 0x20, 0x35, 0x35, 0x20, 0x66, 0x72, 0x69,
    0x65, 0x73, 0x2C, 0x20, 0x35, 0x35, 0x20, 0x74, 0x61, 0x63, 0x6F, 0x73, 0x2C, 0x20, 0x35, 0x35,
    0x20, 0x70, 0x69, 0x65, 0x73, 0x2C, 0x20, 0x35, 0x35, 0x20, 0x43, 0x6F, 0x6B, 0x65, 0x73, 0x2C,
    0x20, 0x31, 0x30, 0x30, 0x20, 0x74, 0x61, 0x74, 0x65, 0x72, 0x20, 0x74, 0x6F, 0x74, 0x73, 0x2C,
    0x20, 0x31, 0x30, 0x30, 0x20, 0x70, 0x69, 0x7A, 0x7A, 0x61, 0x73, 0x2C, 0x20, 0x31, 0x30, 0x30,
    0x20, 0x74, 0x65, 0x6E, 0x64, 0x65, 0x72, 0x73, 0x2C, 0x20, 0x31, 0x30, 0x30, 0x20, 0x6D, 0x65,
    0x61, 0x74, 0x62, 0x61, 0x6C, 0x6C, 0x73, 0x2C, 0x20, 0x31, 0x30, 0x30, 0x20, 0x63, 0x6F, 0x66,
    0x66, 0x65, 0x65, 0x73, 0x2C, 0x20, 0x35, 0x35, 0x20, 0x77, 0x69, 0x6E, 0x67, 0x73, 0x2C, 0x20,
    0x35, 0x35, 0x20, 0x73, 0x68, 0x61, 0x6B, 0x65, 0x73, 0x2C, 0x20, 0x35, 0x35, 0x20, 0x70, 0x61,
    0x6E, 0x63, 0x61, 0x6B, 0x65, 0x73, 0x2C, 0x20, 0x35, 0x35, 0x20, 0x70, 0x61, 0x73, 0x74, 0x61,
    0x73, 0x2C, 0x20, 0x35, 0x35, 0x20, 0x70, 0x65, 0x70, 0x70, 0x65, 0x72, 0x73, 0x2C, 0x20, 0x61,
    0x6E, 0x64, 0x20, 0x31, 0x35, 0x35, 0x20, 0x74, 0x61, 0x74, 0x65, 0x72, 0x73, 0x2E, 0x00, 0x8B,
    0x61, 0xFA, 0x70, 0x35, 0x00, 0x59, 0x6F, 0x75, 0x20, 0x74, 0x68, 0x69, 0x6E, 0x6B, 0x20, 0x74,
    0x68, 0x69, 0x73, 0x20, 0x69, 0x73, 0x20, 0x73, 0x6C, 0x69, 0x63, 0x6B, 0x65, 0x64, 0x20, 0x62,
    0x61, 0x63, 0x6B, 0x3F, 0x20, 0x54, 0x68, 0x69, 0x73, 0x20, 0x69, 0x73, 0x20, 0x70, 0x75, 0x73,
    0x68, 0x65, 0x64, 0x20, 0x62, 0x61, 0x63, 0x6B, 0x2E, 0x00, 0x61, 0x1B, 0x38, 0xC1, 0x36, 0x00,
    0x49, 0x27, 0x6D, 0x20, 0x77, 0x6F, 0x72, 0x72, 0x69, 0x65, 0x64, 0x20, 0x74, 0x68, 0x61, 0x74,
    0x20, 0x74, 0x68, 0x65, 0x20, 0x62, 0x61, 0x62, 0x79, 0x20, 0x74, 0x68, 0x69, 0x6E, 0x6B, 0x73,
    0x20, 0x70, 0x65, 0x6F, 0x70, 0x6C, 0x65, 0x20, 0x63, 0x61, 0x6E, 0x27, 0x74, 0x20, 0x63, 0x68,
    0x61, 0x6E, 0x67, 0x65, 0x2E, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
  };

  tlv_t tlv;
  const tlv_result_t result = tlv_init(&tlv, data, sizeof(data));
  cr_assert_eq(TLV_SUCCESS, result);

  // Add multiple tags.
  uint8_t value1[] = "Let him hold the baby. People can change.\0";
  uint8_t value2[] = "You expect me to believe that?\0";

  cr_assert_eq(tlv_add(&tlv, 0x0000AAAA, value1, sizeof(value1)), TLV_SUCCESS);
  cr_assert_eq(tlv_add(&tlv, 0x0000BBBB, value2, sizeof(value2)), TLV_SUCCESS);

  const uint8_t* lookup_value;
  uint16_t length;

  // Lookup a new tag.
  cr_assert_eq(tlv_lookup(&tlv, 0x0000AAAA, &lookup_value, &length), TLV_SUCCESS);
  cr_assert_str_eq((const char*)lookup_value, "Let him hold the baby. People can change.");

  // Lookup an existing tag.
  cr_assert_eq(tlv_lookup(&tlv, 0x3BFAB440, &lookup_value, &length), TLV_SUCCESS);
  cr_assert_eq(length, 40u);
  cr_assert_str_eq((const char*)lookup_value, "Triples makes it safe. Triples is best.");

  // Update an existing tag.
  uint8_t new_value[] = "You going to eat that?\0";
  cr_assert_eq(tlv_update(&tlv, 0x0000BBBB, new_value, sizeof(new_value)), TLV_SUCCESS);
  cr_assert_eq(tlv_lookup(&tlv, 0x0000BBBB, &lookup_value, &length), TLV_SUCCESS);
  cr_assert_eq(sizeof(new_value), length);
  cr_assert_str_eq((const char*)lookup_value, "You going to eat that?");

  // Delete a tag.
  cr_assert_eq(tlv_delete(&tlv, 0x0000AAAA), TLV_SUCCESS);
  cr_assert_eq(tlv_lookup(&tlv, 0x0000AAAA, &lookup_value, &length), TLV_ERR_NOT_FOUND);

  // Verify remaining tags still work.
  cr_assert_eq(tlv_lookup(&tlv, 0x624F240B, &lookup_value, &length), TLV_SUCCESS);
  cr_assert_eq(tlv_lookup(&tlv, 0x3BFAB440, &lookup_value, &length), TLV_SUCCESS);
  cr_assert_eq(tlv_lookup(&tlv, 0xC626551F, &lookup_value, &length), TLV_SUCCESS);
  cr_assert_eq(tlv_lookup(&tlv, 0x70FA618B, &lookup_value, &length), TLV_SUCCESS);
  cr_assert_eq(tlv_lookup(&tlv, 0xC1381B61, &lookup_value, &length), TLV_SUCCESS);
}
