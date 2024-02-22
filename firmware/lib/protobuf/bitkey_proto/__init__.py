import os
import sys

# fmt: off
# https://github.com/protocolbuffers/protobuf/issues/881
sys.path.insert(0, os.path.abspath(os.path.dirname(__file__)))
# fmt: on

# fmt: off
from . import wallet_pb2
from . import mfgtest_pb2
from . import ops_keybundle_pb2
from . import ops_keys_pb2
from . import ops_seal_pb2
from . import secure_channel_pb2
# fmt: on
