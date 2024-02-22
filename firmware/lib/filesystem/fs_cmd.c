#include "attributes.h"
#include "filesystem.h"
#include "printf.h"
#include "shell_cmd.h"

#define MAX_LS_DEPTH               (128)
#define MAX_BYTES_TO_HUMAN_STR_LEN (4 + 2 + 1)
#define DEFAULT_HEAD_TAIL_LEN      100

static struct { arg_end_t* end; } cmd_du_args;

static struct {
  arg_str_t* dir;
  arg_end_t* end;
} cmd_ls_args;

static struct {
  arg_str_t* file;
  arg_end_t* end;
} cmd_rm_args;

static struct {
  arg_str_t* dir;
  arg_end_t* end;
} cmd_mkdir_args;

static struct {
  arg_str_t* file;
  arg_int_t* n;
  arg_lit_t* hex;
  arg_end_t* end;
} cmd_head_args;

static struct {
  arg_str_t* file;
  arg_int_t* n;
  arg_lit_t* hex;
  arg_end_t* end;
} cmd_tail_args;

static struct {
  arg_str_t* file;
  arg_end_t* end;
} cmd_touch_args;

static void cmd_du_register(void);
static void cmd_ls_register(void);
static void cmd_rm_register(void);
static void cmd_mkdir_register(void);
static void cmd_touch_register(void);
static void cmd_head_register(void);
static void cmd_tail_register(void);

static void cmd_du_run(int argc, char** argv);
static void cmd_ls_run(int argc, char** argv);
static void cmd_rm_run(int argc, char** argv);
static void cmd_mkdir_run(int argc, char** argv);
static void cmd_touch_run(int argc, char** argv);
static void cmd_head_run(int argc, char** argv);
static void cmd_tail_run(int argc, char** argv);

static void print_fs_error(char* command, char* input, const int error);
static void bytes_to_human_str(uint32_t bytes, char* human_str);
static int helper_ls(const char* path);
static int helper_head_tail(const char* path, uint32_t n_bytes, bool format_hex, bool head_tail);

static void cmd_du_register(void) {
  cmd_du_args.end = ARG_END();

  static shell_command_t du_cmd = {
    .command = "du",
    .help = "print filesystem usage",
    .handler = &cmd_du_run,
    .argtable = &cmd_du_args,
  };

  shell_command_register(&du_cmd);
}
SHELL_CMD_REGISTER("du", cmd_du_register);

static void cmd_ls_register(void) {
  cmd_ls_args.dir = ARG_STR_REQ(0, NULL, "directory to list");
  cmd_ls_args.end = ARG_END();

  static shell_command_t ls_cmd = {
    .command = "ls",
    .help = "list directory contents",
    .handler = &cmd_ls_run,
    .argtable = &cmd_ls_args,
  };

  shell_command_register(&ls_cmd);
}
SHELL_CMD_REGISTER("ls", cmd_ls_register);

static void cmd_rm_register(void) {
  cmd_rm_args.file = ARG_STR_REQ(0, NULL, "file to remove");
  cmd_rm_args.end = ARG_END();

  static shell_command_t rm_cmd = {
    .command = "rm",
    .help = "remove a file or directory",
    .handler = &cmd_rm_run,
    .argtable = &cmd_rm_args,
  };

  shell_command_register(&rm_cmd);
}
SHELL_CMD_REGISTER("rm", cmd_rm_register);

static void cmd_mkdir_register(void) {
  cmd_mkdir_args.dir = ARG_STR_REQ(0, NULL, "directory to create");
  cmd_mkdir_args.end = ARG_END();

  static shell_command_t mkdir_cmd = {
    .command = "mkdir",
    .help = "create a new directory",
    .handler = &cmd_mkdir_run,
    .argtable = &cmd_mkdir_args,
  };

  shell_command_register(&mkdir_cmd);
}
SHELL_CMD_REGISTER("mkdir", cmd_mkdir_register);

static void cmd_touch_register(void) {
  cmd_touch_args.file = ARG_STR_REQ(0, NULL, "file to create");
  cmd_touch_args.end = ARG_END();

  static shell_command_t touch_cmd = {
    .command = "touch",
    .help = "create an empty file",
    .handler = &cmd_touch_run,
    .argtable = &cmd_touch_args,
  };

  shell_command_register(&touch_cmd);
}
SHELL_CMD_REGISTER("touch", cmd_touch_register);

static void cmd_head_register(void) {
  cmd_head_args.file = ARG_STR_REQ(0, NULL, "shows first n (default=100) bytes of file");
  cmd_head_args.n = ARG_INT_OPT('n', "num", "number of bytes to read");
  cmd_head_args.hex = ARG_LIT_OPT('h', "hex", "format: hex");
  cmd_head_args.end = ARG_END();

  static shell_command_t head_cmd = {
    .command = "head",
    .help = "display file contents",
    .handler = &cmd_head_run,
    .argtable = &cmd_head_args,
  };

  shell_command_register(&head_cmd);
}
SHELL_CMD_REGISTER("head", cmd_head_register);

static void cmd_tail_register(void) {
  cmd_tail_args.file = ARG_STR_REQ(0, NULL, "shows first n (default=100) bytes of file");
  cmd_tail_args.n = ARG_INT_OPT('n', "num", "number of bytes to read");
  cmd_tail_args.hex = ARG_LIT_OPT('h', "hex", "format: hex");
  cmd_tail_args.end = ARG_END();

  static shell_command_t tail_cmd = {
    .command = "tail",
    .help = "display file contents",
    .handler = &cmd_tail_run,
    .argtable = &cmd_tail_args,
  };

  shell_command_register(&tail_cmd);
}
SHELL_CMD_REGISTER("tail", cmd_tail_register);

static void cmd_du_run(int UNUSED(argc), char** UNUSED(argv)) {
  int32_t size = fs_used();
  printf("Used %lu bytes\n", size);
}

static void cmd_ls_run(int argc, char** argv) {
  const uint32_t nerrors = shell_argparse_parse(argc, argv, (void**)&cmd_ls_args);

  if (nerrors) {
    return;
  }

  if (cmd_ls_args.dir->header.found) {
    const int ret = helper_ls(cmd_ls_args.dir->value);
    if (ret) {
      print_fs_error("ls", cmd_ls_args.dir->value, ret);
    }
  }
}

static void cmd_rm_run(int argc, char** argv) {
  int nerrors = shell_argparse_parse(argc, argv, (void**)&cmd_rm_args);

  if (nerrors) {
    return;
  }

  if (cmd_rm_args.file->header.found) {
    const int ret = fs_remove(cmd_rm_args.file->value);
    if (ret < 0) {
      print_fs_error("rm", cmd_rm_args.file->value, ret);
    }
  }
}

static void cmd_mkdir_run(int argc, char** argv) {
  int nerrors = shell_argparse_parse(argc, argv, (void**)&cmd_mkdir_args);

  if (nerrors) {
    return;
  }

  if (cmd_mkdir_args.dir->header.found) {
    const int ret = fs_mkdir(cmd_mkdir_args.dir->value);
    if (ret < 0) {
      print_fs_error("mkdir", cmd_mkdir_args.dir->value, ret);
    }
  }
}

static void cmd_touch_run(int argc, char** argv) {
  int nerrors = shell_argparse_parse(argc, argv, (void**)&cmd_touch_args);

  if (nerrors) {
    return;
  }

  if (cmd_touch_args.file->header.found) {
    const int ret = fs_touch(cmd_touch_args.file->value);
    if (ret < 0) {
      print_fs_error("touch", cmd_touch_args.file->value, ret);
    }
  }
}

static void cmd_head_run(int argc, char** argv) {
  bool hex_format = false;
  int nerrors = shell_argparse_parse(argc, argv, (void**)&cmd_head_args);

  if (nerrors) {
    return;
  }

  uint32_t n_bytes = DEFAULT_HEAD_TAIL_LEN;
  if (cmd_head_args.n->header.found) {
    n_bytes = (uint32_t)cmd_head_args.n->value;
  }

  if (cmd_head_args.hex->header.found) {
    hex_format = true;
  }

  if (cmd_head_args.file->header.found) {
    const int ret = helper_head_tail(cmd_head_args.file->value, n_bytes, hex_format, true);
    if (ret < 0) {
      print_fs_error("head", cmd_head_args.file->value, ret);
    }
  }
}

static void cmd_tail_run(int argc, char** argv) {
  bool hex_format = false;
  int nerrors = shell_argparse_parse(argc, argv, (void**)&cmd_tail_args);

  if (nerrors) {
    return;
  }

  uint32_t n_bytes = DEFAULT_HEAD_TAIL_LEN;
  if (cmd_tail_args.n->header.found) {
    n_bytes = (uint32_t)cmd_tail_args.n->value;
  }

  if (cmd_tail_args.hex->header.found) {
    hex_format = true;
  }

  if (cmd_tail_args.file->header.found) {
    const int ret = helper_head_tail(cmd_tail_args.file->value, n_bytes, hex_format, false);
    if (ret < 0) {
      print_fs_error("head", cmd_tail_args.file->value, ret);
    }
  }
}

static void print_fs_error(char* command, char* input, const int error) {
  char* fs_err = fs_error_str(error);
  printf("%s: (%s): %s\n", command, input, fs_err);
}

/* Utility for printing the directory */
static int helper_ls(const char* path) {
  fs_dir_t dir;
  char byte_string[MAX_BYTES_TO_HUMAN_STR_LEN] = {0};
  uint32_t i;

  int err = fs_dir_open(&dir, path);
  if (err) {
    return err;
  }

  fs_dir_info_t info;
  for (i = 0; i < MAX_LS_DEPTH; i++) {
    int res = fs_dir_read(&dir, &info);
    if (res < 0) {
      fs_dir_close(&dir);
      return res;
    }

    if (res == 0) {
      break;
    }

    switch (info.type) {
      case FS_FILE_TYPE_REG:
        printf("reg ");
        break;
      case FS_FILE_TYPE_DIR:
        printf("dir ");
        break;
      case FS_FILE_TYPE_ERR:
      default:
        printf("?   ");
        break;
    }

    bytes_to_human_str(info.size, byte_string);
    printf("%s %s\n", byte_string, info.name);
  }

  err = fs_dir_close(&dir);
  if (err) {
    return err;
  }

  return 0;
}

static void bytes_to_human_str(uint32_t bytes, char* human_str) {
  int i;
  static const char* prefixes[] = {"", "K", "M", "G"};
  for (i = sizeof(prefixes) / sizeof(prefixes[0]) - 1; i >= 0; i--) {
    if (bytes >= (1UL << 10 * i) - 1) {
      snprintf(human_str, MAX_BYTES_TO_HUMAN_STR_LEN - 1, "%*lu%sB", 4 - (i != 0), bytes >> 10 * i,
               prefixes[i]);
      break;
    }
  }
}

/* Utility for printing contents of a file in %02x format */
static int helper_head_tail(const char* path, uint32_t n_bytes, bool format_hex, bool head_tail) {
  int ret;
  static fs_file_t fd;
  uint8_t c;
  int32_t file_size;

  ret = fs_open(&fd, path, FS_O_RDONLY);
  if (ret < 0) {
    return ret;
  }

  file_size = fs_file_size(&fd);
  if (file_size < 0) {
    goto close_return;
  }
  if (n_bytes > (uint32_t)file_size) {
    n_bytes = (uint32_t)file_size;
  }

  if (!head_tail) {
    /* Seek to n bytes from end */
    ret = fs_file_seek(&fd, -((int32_t)n_bytes), FS_SEEK_END);
    if (ret < 0) {
      goto close_return;
    }
  }

  for (uint32_t i = 0; i < n_bytes; i++) {
    ret = fs_file_read(&fd, &c, 1);
    if (ret < 0) {
      printf("err");
      goto close_return;
    } else if (ret == 0) {
      break;
    } else {  // >0
      if (format_hex) {
        printf("\\x%02x", c);
      } else {
        printf("%c", c);
      }
    }
  }

  printf("\n");

  ret = fs_close(&fd);
  return ret;

close_return:
  fs_close(&fd);
  return ret;
}
