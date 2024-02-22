import click
import shutil

from pathlib import Path
from nanopb.generator.proto import build_nanopb_proto, load_nanopb_pb2


def build(setup_kwargs):
    click.echo(click.style(f'Generating protos', fg='cyan'))
    PROTOBUF_DIR = Path(__file__).parent.absolute()
    generate(
        proto_dir=PROTOBUF_DIR.joinpath('protos'),
        out_dir=PROTOBUF_DIR.joinpath('bitkey_proto')
    )
    return setup_kwargs


def generate(proto_dir=None, out_dir=None):
    """Generate protobuf files"""
    proto_sources = proto_dir.glob('**/*.proto')

    # Build *_pb2.py files along-side *.proto files
    for proto_file in proto_sources:
        click.echo(click.style(f'Building: {proto_file}', fg='cyan'))
        build_nanopb_proto(f"{Path(proto_file).name}", proto_dir)

    # Move the *_pb2.py files into the out_dir
    py_sources = Path(proto_dir).glob('*_pb2.py')
    for py_file in py_sources:
        py_file = Path(py_file)
        shutil.copy(py_file, out_dir)
        py_file.unlink()

    # Copy the latest nanopb_pb2.py file
    nanopb_pb2 = load_nanopb_pb2().__file__
    shutil.copy(nanopb_pb2, out_dir)


if __name__ == "__main__":
    build({})
