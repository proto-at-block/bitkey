#include "shell_argparse.h"

#include "hex.h"
#include "shell.h"
#include "shell_help.h"

#include <ctype.h>
#include <stdbool.h>
#include <stdlib.h>
#include <string.h>

static void parse_tagged(int argc, char** argv, arg_header_t** table, arg_end_t* endtable);
static void parse_untagged(int argc, char** argv, arg_header_t** table, arg_end_t* endtable);
static uint32_t check_required(arg_header_t** table);
static bool get_option_value(arg_header_t* arg, const char* value);
static int32_t short_option_index(arg_header_t** table, char name);
static int32_t long_option_index(arg_header_t** table, char* name);
static arg_header_t* get_positional_option(arg_header_t** table, const uint32_t position);
static void arg_header_init(arg_header_t* header, const char shortname, const char* longname,
                            const char* help, const bool required);
static void reset_argtable(void** argtable);
static uint32_t argtable_end_index(arg_header_t** table);

uint32_t shell_argparse_parse(int argc, char** argv, void** argtable) {
  /* Reset argtable from previous parsings */
  reset_argtable(argtable);

  arg_header_t** table = (arg_header_t**)argtable;
  const uint32_t table_end_idx = argtable_end_index(table);
  arg_end_t* table_end = (arg_end_t*)table[table_end_idx];
  table_end->errors = 0;
  table_end->show_help = false;

  /* Parse tagged arguments */
  parse_tagged(argc, argv, table, table_end);

  /* Show help if -h or --help was found */
  if (table_end->show_help) {
    const shell_command_t* cmd = find_command(argv[0]);
    if (cmd) {
      print_command_usage(cmd);
      reset_argtable((void**)table);
      table_end->errors = 0;
      return table_end->errors;
    }
  }

  /* Parse untagged (positional) arguments */
  parse_untagged(argc, argv, table, table_end);

  if (table_end->errors) {
    return table_end->errors;
  }

  /* Check all required options have been provided */
  const uint32_t missing_arg_count = check_required(table);
  if (missing_arg_count > 0) {
    return missing_arg_count;
  }

  return table_end->errors;
}

typedef enum {
  GETOPT_ERR,
  GETOPT_SHORT,
  GETOPT_LONG,
  GETOPT_OK,
} getopt_result_t;

static getopt_result_t get_option(const char* token) {
  /* Invalid option or single '-' */
  if (token[0] != '-' || token[1] == '\0') {
    return GETOPT_ERR;
  }

  /* Short option */
  if (token[0] == '-' && token[1] != '-') {
    return GETOPT_SHORT;
  }

  /* Long option */
  if (token[0] == '-' && token[1] == '-') {
    return GETOPT_LONG;
  }

  return GETOPT_ERR;
}

static void parse_tagged(int argc, char** argv, arg_header_t** table, arg_end_t* endtable) {
  /* Iterate through the argv tokens */
  for (int i = 1; i < argc; i++) {
    const char* arg = argv[i];

    // printf("processing argc %li argv '%s'\n", i, arg);

    const getopt_result_t option = get_option(arg);
    switch (option) {
      case GETOPT_ERR:
        break;

      case GETOPT_SHORT:
        /* Special handling for '-h' */
        if (arg[1] == 'h') {
          goto show_help;
        }

        /* fall-through */
      case GETOPT_LONG: {
        /* Special handling for '--help' */
        if (strcmp((char*)(arg + 2), "help") == 0) {
          goto show_help;
        }

        // printf("option '%s' found at index %li\n", arg, i);
        const int32_t table_idx = option == GETOPT_SHORT
                                    ? short_option_index(table, arg[1])
                                    : long_option_index(table, (char*)(arg + 2));
        if (table_idx < 0) {
          endtable->errors++;
          break;
        }

        /* Literals have no value and need to be handled as such */
        if (table[table_idx]->type == ARG_TYPE_LIT) {
          table[table_idx]->found = true;
          break;
        }

        /* Ensure argv[i+1] exists */
        if (++i >= argc) {
          break;
        }

        // printf("table_idx = %i\n", table_idx);
        if (!get_option_value(table[table_idx], argv[i])) {
          endtable->errors++;
        }
      } break;

      default:
        break;
    }
  }

  return;

show_help:
  endtable->show_help = true;
}

static void parse_untagged(int argc, char** argv, arg_header_t** table, arg_end_t* endtable) {
  /* Iterate through the argv tokens */
  uint32_t position = 0;
  for (int i = 1; i < argc; i++) {
    const char* arg = argv[i];

    /* Skip tagged arguments */
    const getopt_result_t option = get_option(arg);
    if (option > GETOPT_ERR) {
      i++;
      continue;
    }

    /* Find positional argument */
    arg_header_t* hdr = get_positional_option(table, position);
    if (hdr == NULL) {
      shell_write("unknown argument '%s'\n", arg);
      endtable->errors++;
      return;
    }

    /* Mark the positional argument as found */
    hdr->found = true;
    position++;

    /* Attempt to parse the positional value */
    if (!get_option_value(hdr, argv[i])) {
      endtable->errors++;
    }
  }
}

static uint32_t check_required(arg_header_t** table) {
  uint32_t missing = 0;
  for (uint32_t i = 0; table[i]->type != ARG_TYPE_END; i++) {
    arg_header_t* hdr = table[i];
    if (hdr->required && !hdr->found) {
      if (hdr->shortname == 0 && hdr->longname == NULL) {
        shell_write("required argument missing\n", hdr->longname);
      } else {
        shell_write("required argument '--%s' missing\n", hdr->longname);
      }
      missing++;
    }
  }

  return missing;
}

static bool get_option_value(arg_header_t* arg, const char* value) {
  // printf("parsing value '%s' for option '%s'\n", value, arg->longname);

  switch (arg->type) {
    case ARG_TYPE_INT: {
      arg_int_t* arg_int = (arg_int_t*)arg->parent;
      arg_int->value = strtol(value, NULL, 0);
      arg_int->header.found = true;
    } break;
    case ARG_TYPE_BOOL: {
      arg_int_t* arg_int = (arg_int_t*)arg->parent;
      const int value_len = strlen(value);
      if (value_len == 0) {
        break;
      } else if (value_len == 1) {
        arg_int->value = (bool)(value[0] - '0' > 0);
        arg_int->header.found = true;
      } else {
        /* lowercase the input string */
        char* lowered = (char*)value;
        for (uint32_t i = 0; value[i]; i++) {
          lowered[i] = tolower(value[i]);
        }
        if (strcmp(lowered, "true") == 0) {
          arg_int->value = true;
        } else if (strcmp(lowered, "false") == 0) {
          arg_int->value = false;
        }
        arg_int->header.found = true;
      }
    } break;
    case ARG_TYPE_STR: {
      arg_str_t* arg_str = (arg_str_t*)arg->parent;
      const size_t len = strlen(value);
      if (len > 0) {
        strncpy(arg_str->value, value, ARG_STR_MAX_LEN);
        arg_str->len = len;
        arg->found = true;
      }
    } break;
    case ARG_TYPE_HEX: {
      arg_hex_t* arg_hex = (arg_hex_t*)arg->parent;
      const size_t len = strlen(value);
      memset(arg_hex->bytes, 0, arg_hex->max_len);
      if ((len / 2) > arg_hex->max_len) {
        arg_hex->len = 0;
        arg->found = true;
      } else if (len > 0) {
        arg_hex->len = parsehex(value, len, arg_hex->bytes);
        arg->found = true;
      }
    } break;
    case ARG_TYPE_LIT:
      /* Literal types have no value */
      /* fall-through */
    default:
      break;
  }

  return true;
}

static int32_t short_option_index(arg_header_t** table, char name) {
  for (uint32_t i = 0; table[i]->type != ARG_TYPE_END; i++) {
    arg_header_t* hdr = table[i];
    if (hdr->shortname == name) {
      return i;
    }
  }

  return -1;
}

static int32_t long_option_index(arg_header_t** table, char* name) {
  for (uint32_t i = 0; table[i]->type != ARG_TYPE_END; i++) {
    arg_header_t* hdr = table[i];
    if (hdr->longname == NULL) {
      /* Ignore positional args */
      continue;
    }
    if (strcmp(hdr->longname, name) == 0) {
      return i;
    }
  }
  return -1;
}

static arg_header_t* get_positional_option(arg_header_t** table, const uint32_t position) {
  uint32_t current = 0;
  for (uint32_t i = 0; table[i]->type != ARG_TYPE_END; i++) {
    arg_header_t* hdr = table[i];
    if (hdr->shortname == 0 && hdr->longname == NULL) {
      if (current == position) {
        return hdr;
      }
      current++;
    }
  }
  return NULL;
}

void arg_int_init(arg_int_t* arg, const char shortname, const char* longname, const char* help,
                  const bool required) {
  arg_header_init(&arg->header, shortname, longname, help, required);
  arg->header.type = ARG_TYPE_INT;
  arg->header.parent = (void*)arg;
}

void arg_bool_init(arg_bool_t* arg, const char shortname, const char* longname, const char* help,
                   const bool required) {
  arg_header_init(&arg->header, shortname, longname, help, required);
  arg->header.type = ARG_TYPE_BOOL;
  arg->header.parent = (void*)arg;
}

void arg_str_init(arg_str_t* arg, const char shortname, const char* longname, const char* help,
                  const bool required) {
  arg_header_init(&arg->header, shortname, longname, help, required);
  arg->header.type = ARG_TYPE_STR;
  arg->header.parent = (void*)arg;
  memset(arg->value, 0, ARG_STR_MAX_LEN + 1); /* +1 for null terminator */
}

void arg_lit_init(arg_lit_t* arg, const char shortname, const char* longname, const char* help,
                  const bool required) {
  arg_header_init(&arg->header, shortname, longname, help, required);
  arg->header.type = ARG_TYPE_LIT;
  arg->header.parent = (void*)arg;
}

void arg_hex_init(arg_hex_t* arg, const char shortname, const char* longname, const char* help,
                  const bool required, const uint8_t* buffer, const size_t buffer_len) {
  arg_header_init(&arg->header, shortname, longname, help, required);
  arg->header.type = ARG_TYPE_HEX;
  arg->header.parent = (void*)arg;
  arg->bytes = (uint8_t*)buffer;
  arg->max_len = buffer_len;
  arg->len = 0;
}

static void arg_header_init(arg_header_t* header, const char shortname, const char* longname,
                            const char* help, const bool required) {
  header->shortname = shortname;
  header->longname = longname;
  header->help = help;
  header->required = required;
  header->found = false;
}

void arg_end_init(arg_end_t* arg) {
  arg->header.type = ARG_TYPE_END;
}

static void reset_argtable(void** argtable) {
  arg_header_t** table = (arg_header_t**)argtable;
  uint32_t i = 0;
  do {
    table[i]->found = false;
  } while (table[i++]->type != ARG_TYPE_END);
}

static uint32_t argtable_end_index(arg_header_t** table) {
  uint32_t i = 0;
  while (table[i]->type != ARG_TYPE_END) i++;
  return i;
}
