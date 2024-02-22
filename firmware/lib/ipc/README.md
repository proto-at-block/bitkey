# IPC

The IPC module provides inter-task communication via "ports" (think sockets) and a pair of send/recv functions, backed by FreeRTOS queues. The API is inspired by POSIX sockets and Mach ports.

This layer of abstraction enables adding features on top of RTOS task communication, such as message tracing, fault injection, etc.

Unless you have a strong reason, prefer IPC over using FreeRTOS queues for task communication.

## Overview

The IPC system is comprised of:

1. A library `libipc`
2. IPC definition files ending with `_ipc.yaml`
3. Build system hooks for code generation

IPC definition files are placed in task directories. These files specify port names
and message structs. An example:

```yaml
port_name: foo_port

messages:
  structs:
    - name: foo_msg_t
      fields:
        - uint32_t a
        - size_t b

  protos:
    - name: fwpb_foo
```

This file, via code generation, will create an `ipc_port_t` variable named `foo_port`,
a struct named `foo_msg_t`, and enums to identify each message.
These values are then used as arguments to `ipc_send` and `ipc_recv`.

## Developer guide

### Sending and receiving messages

`ipc_send` is used to send messages to a specified port. To send a `foo_msg_t`
to `foo_port`, do:

```c
foo_msg_t msg = {.a = 1, .b = 2};
ipc_send(foo_port, &msg, sizeof(msg), IPC_FOO_MSG);
```

Note the third argument -- this enum is a tag used by the receiver to identify the message.

* For structs, the format is `IPC_` + upper case `struct` name, without `_t`.
* For proto, the format is `IPC_PROTO_` + upper case proto name.

In the receiving task, to receive the message, do the following:

```c
ipc_ref_t tlv = {0};
ipc_recv(foo_port, &tlv);

switch (tlv.tag) {  // Identify the message and handle depending on type
  case IPC_PROTO_FOO:
    handle_proto_msg((fwpb_foo*)tlv.object);
    break;
  case IPC_FOO_MSG:
    handle_foo_msg((foo_msg_t*)tlv.object);
    break;
}
```

Note that the memory must remain valid until the receiver has finished processing the message, unless
`take_ownership` is set to true. In this case, the receiver must release the memory with `ipc_release()`
after receiving. See `ipc_test.c` for example usage.

### IPC definition files

IPC definition files allow you to specify new ports and message types.
A message can either be a `struct` or a `proto`.

`struct` messages generate C structs, as specified by the `fields` list.
For example, the message `foo_msg_t` from above generates the following code:

```c
typedef struct {
  uint32_t a;
  size_t b;
} foo_msg_t;
```

`proto` messages just generate an enum so that tasks identify which proto was sent, without
using the `oneof` pattern everywhere.

Use a `proto` message for tasks which receive protos from an external source and wish to pass
those protos to another task, without performing any extra conversions.

## Build system design

The IPC module contains a script `ipc_codegen.py` which uses `jinja2` to generate code.
Jinja templates are found in `templates/`, and generated code goes in `generated/`.

Code generation is performed _before_ the Meson build. When using `custom_target()` to generate
code, a race condition was present where sometimes sources wouldn't get generated before
libraries that transitively depended on them. This can probably be worked around, but I couldn't
figure out how! A similar issue is described [here](https://gitlab.gnome.org/GNOME/gimp/-/issues/6257).

`ipc_codegen.py` checks the `mtime` of IPC definition files, protos, and Jinja templates to
determine if a rebuild is necessary.
