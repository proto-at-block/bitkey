import logging
import os
import sys

import nfc

UNIFFI_PATH='_build/rust/uniffi/python'
sys.path.append(UNIFFI_PATH)
DYLIB_NAME='libcore.dylib'
UNIFFI_DYLIB_PATH=os.path.join(UNIFFI_PATH, DYLIB_NAME)
try:
    os.symlink(os.path.abspath(os.path.join('_build/rust/target/debug/', DYLIB_NAME)), UNIFFI_DYLIB_PATH)
    import core
finally:
    os.remove(UNIFFI_DYLIB_PATH)

logging.basicConfig()
logging.getLogger('nfc').setLevel(logging.DEBUG)

def transaction(klass):
    with nfc.ContactlessFrontend('usb') as clf:
        tag = clf.connect(rdwr={'on-connect': lambda tag: False})

        tx = klass()

        while tx.result() is None:
            command = tx.bytes_to_send()
            response = tag.transceive(bytes(command))
            tx.receive_bytes(response)

        return tx.result()

print("Version:", transaction(core.Version))
print("GenerateKey:", transaction(core.GenerateKey))
