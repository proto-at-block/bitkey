import importlib.util
import os
import shlex
import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest import mock


IPC_CODEGEN_PATH = (
    Path(__file__).resolve().parents[2] / "lib" / "ipc" / "ipc_codegen.py"
)
IPC_CODEGEN_SPEC = importlib.util.spec_from_file_location(
    "ipc_codegen", IPC_CODEGEN_PATH
)
assert IPC_CODEGEN_SPEC is not None
assert IPC_CODEGEN_SPEC.loader is not None
ipc_codegen = importlib.util.module_from_spec(IPC_CODEGEN_SPEC)
IPC_CODEGEN_SPEC.loader.exec_module(ipc_codegen)


def preprocess(source: str, *defines: str) -> str:
    source_without_includes = "\n".join(
        line for line in source.splitlines() if not line.startswith("#include")
    )
    compiler = shlex.split(os.environ.get("CC", "cc"))
    environment = os.environ.copy()
    # The firmware environment points GCC at the ARM cross-compiler internals,
    # which prevents the native compiler from locating its preprocessor.
    environment.pop("GCC_EXEC_PREFIX", None)
    result = subprocess.run(
        [
            *compiler,
            "-E",
            "-P",
            "-x",
            "c",
            *(f"-D{define}" for define in defines),
            "-",
        ],
        input=source_without_includes,
        capture_output=True,
        env=environment,
        text=True,
    )
    if result.returncode != 0:
        raise RuntimeError(result.stderr)
    return result.stdout


class TestIpcCodegen(unittest.TestCase):
    def test_dev_only_proto_is_not_routed_in_production(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            temp_dir = Path(temp_dir)
            definition = temp_dir / "test_ipc.yaml"
            definition.write_text(
                """\
port_name: test_port
messages:
  protos:
    - name: fwpb_reset_cmd
      auth: dev
    - name: fwpb_meta_cmd
      auth: never
  structs: []
"""
            )
            proto = temp_dir / "wallet.proto"
            proto.touch()
            output_dir = temp_dir / "generated"

            with mock.patch.object(
                ipc_codegen, "_collect_definitions", return_value=[str(definition)]
            ), mock.patch.object(
                ipc_codegen, "_collect_protos", return_value=[str(proto)]
            ):
                ipc_codegen._generate_to_dir(str(output_dir), ignore_cache=True)

            generated = (output_dir / "ipc_internal.c").read_text()

        production_source = preprocess(generated, "CONFIG_PROD")
        development_source = preprocess(generated)

        self.assertNotIn("fwpb_wallet_cmd_reset_cmd_tag", production_source)
        self.assertIn("fwpb_wallet_cmd_meta_cmd_tag", production_source)
        self.assertIn("fwpb_wallet_cmd_reset_cmd_tag", development_source)


if __name__ == "__main__":
    unittest.main()
