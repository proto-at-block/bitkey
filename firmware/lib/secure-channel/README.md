# secure-channel

A secure channel for encrypting fields of protobufs.

One-way authenticated ECDH with x25519 and aes-gcm-256. The bitkey's ephemeral keypair is signed with its unique device identity.

## shared memory

HEY! This library uses a shared context so that multiple tasks can access the derived sesion key. This is cleaner than each task needing IPC messages to receive an encrypted message back.

The shared key is guarded by a mutex.

This would be better from a security POV as a separate service to benefit from task isolation.

## design
The *right* approach to implement a secure channel would be to extend WCA to support it. This would especially be true if we want to apply security to all protobufs. But, we don't: the overhead is too high, given NFC's slow speed. (There are tradeoffs to be made to deal with that, e.g. establish a long-term key; but we'll ignore that here.)

But either way: extending WCA is complicated, and only a few protos would benefit from it. So, instead, for now, we have a secure channel to encrypt individual proto fields.

## usage

A shared session key is derived by sending `secure_channel_establish_cmd`. Then, any `secure_channel_message` will be wrapped with the session key.
In python, we have the `SecureChannel` wrapper to handle this.
