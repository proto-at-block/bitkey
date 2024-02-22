# Shell

These sources provide a simple command line shell for intracting with the device from a VT100 compatible terminal.

This shell is inspired by [cofyc/argparse](https://github.com/cofyc/argparse) and [argtable3](https://github.com/argtable/argtable3), which were both unsuitable for embedded use due to their design around a short-lived single use API (eg. calls to `exit`) or their use of dynamic memory allocation (`malloc` etc).

## Features

- Support for break (`CTRL+C`) and backspace editing of the command line
- Shell history with navigation using up/down arrow keys
- Definition of commands with optional and required arguments
- Initcall style command registration so that any commands built will be included in the shell

### Limitations

- Argument tags and values must be separated by a single space (eg. `led --red 50`). Equal sign separators are not supported (eg. `led --red=50`)

### Future work

- Arguments with optional short or long names
- In-place editing of the command line with back/forward arrow keys
- Clean interposing of `printf` style debug and log output with command line prompt

## Adding a command

Below is a sample for defining a simple command to return the sum of two numbers.

First a struct with pointers to the arguments and a terminator argument needs to be declared.

```c
static struct {
  arg_int_t* a;
  arg_int_t* b;
  arg_end_t* end;
} cmd_multiply_args;
```

Next, a register function and a handler function must be declared.

```c
static void multiply_cmd_register(void);
static void multiply_cmd_handler(int argc, char** argv);
```

Inside the register function, the command and it's arguments optional or required arguments are defined.

_Note: Each of the arguments (excluding the special `ARG_END()` terminator) can be defined as required `ARG_x_REQ` or optional `ARG_x_OPT`_

```c
static void multiply_cmd_register(void) {
  cmd_multiply_args.a = ARG_INT_REQ('a', "avalue", "an integer");
  cmd_multiply_args.b = ARG_INT_REQ('b', "bvalue", "another integer");
  cmd_multiply_args.end = ARG_END();

  static shell_command_t test_cmd = {
      .command = "multiply",
      .help = "multiply two numbers and print the output",
      .handler = multiply_cmd_handler,
      .argtable = &cmd_multiply_args,
  };

  shell_command_register(&test_cmd);
}
```

To define a positional argument the arg must be defined with a shortname of `0` and a longname of `NULL`. For example

```c
ARG_INT_REQ(foo, 0, NULL, "an integer");
```

A special [initcall](https://0xax.gitbooks.io/linux-insides/content/Concepts/linux-cpu-3.html) style macro is used to place a pointer to the register function in a designated area of memory, so that all handlers added during compilation can be executed, without the need for a centralised initialisation function.

```c
SHELL_CMD_REGISTER("multiply", multiply_cmd_register);
```

The command handler is passed `argc` and `argv` values from the shell interpreter, which the handler can ignore, or use to parse argument values.

To parse the arguments, `shell_argparse_parse()` is called and will return the number of errors encountered.

Any valid arguments that are found are marked as such in the `header` value of each argument within the struct defined earlier.

```c
static void multiply_cmd_handler(int argc, char** argv) {
  shell_argparse_parse(argc, argv, (void**)&cmd_multiply_args);

  if (cmd_multiply_args.a->header.found && cmd_multiply_args.a->header.found) {
    const int a = cmd_multiply_args.a->value;
    const int b = cmd_multiply_args.a->value;
    printf("%i x %i == %i\n", a, b, (a * b));
  }
}
```

Below is a complete snippet of the code above.

```c
static struct {
  arg_int_t* a;
  arg_int_t* b;
  arg_end_t* end;
} cmd_multiply_args;

static void multiply_cmd_register(void);
static void multiply_cmd_handler(int argc, char** argv);

static void multiply_cmd_register(void) {
  cmd_multiply_args.a = ARG_INT_REQ(foo, 'a', "avalue", "an integer");
  cmd_multiply_args.b = ARG_INT_REQ(foo, 'b', "bvalue", "another integer");
  cmd_multiply_args.end = ARG_END();

  static shell_command_t test_cmd = {
      .command = "multiply",
      .help = "multiply two numbers and print the output",
      .handler = multiply_cmd_handler,
      .argtable = &cmd_multiply_args,
  };

  shell_command_register(&test_cmd);
}
SHELL_CMD_REGISTER("multiply", multiply_cmd_register);

static void multiply_cmd_handler(int argc, char** argv) {
  shell_argparse_parse(argc, argv, (void**)&cmd_multiply_args);

  if (cmd_multiply_args.a->header.found && cmd_multiply_args.a->header.found) {
    const int a = cmd_multiply_args.a->value;
    const int b = cmd_multiply_args.a->value;
    printf("%i x %i == %i\n", a, b, (a * b));
  }
}
```

## Special Commands

The built-in `help` command will list all available commands, for example:

```text
W1> help
help        prints all available commands
multiply    multiply two numbers and print the output
```

Each command also has a built-in argument `-h` and `--help` which will print that commands usage. This is generated using the command help string and the argument help descriptions, for example:

```text
W1> multiply -h
Usage:
multiply two numbers and print the output
    -a, --avalue=<int>    an integer
    -a, --bvalue=<int>    another integer
```
