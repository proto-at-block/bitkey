#!/usr/bin/env python

import pathlib
import glob
import yaml
import jinja2
import os
import click
import hashlib

cli = click.Group()


HERE = pathlib.Path(__file__).parent.resolve()
OUTPUT_DIR = os.path.join(HERE, "generated")
TEMPLATE_DIR = os.path.join(HERE, "templates")
IPC_CACHE_FILE = os.path.join(OUTPUT_DIR, ".ipc_cache")
PROJECT_ROOT = os.path.join(HERE, "..", "..")

AUTH_GROUP_NEVER = "protos_that_never_require_auth"
AUTH_GROUP_ALWAYS = "protos_that_always_require_auth"
AUTH_GROUP_AFTER_ONBOARDING = "protos_that_require_auth_after_onboarding"


ENV = jinja2.Environment(
    loader=jinja2.FileSystemLoader(HERE / "templates"),
    trim_blocks=True,
    lstrip_blocks=True)


def _sha256(s):
    ctx = hashlib.sha256()
    ctx.update(s.encode(encoding='UTF-8'))
    return ctx.hexdigest()


def _deterministic_glob(s):
    """Glob files and sort the output -- use to ensure deterministic builds."""
    return sorted(glob.glob(s, recursive=True))


def _collect_definitions():
    """Locate all IPC message definition files."""
    return _deterministic_glob(f"{str(PROJECT_ROOT)}/**/*ipc.yaml")


def _collect_templates():
    """Locate all template files."""
    return _deterministic_glob(f"{TEMPLATE_DIR}/**/*.[c|h]")


def _collect_protos():
    """Locate protobuf definitions."""
    return _deterministic_glob(f"{str(PROJECT_ROOT)}/lib/**/*.proto")


def _render_template(template_name, output_dir, dict):
    template = ENV.get_template(template_name)
    out_file = os.path.join(output_dir, os.path.basename(
        template.filename.replace(".jinja", "")))
    open(out_file, "w+").write(template.render(dict))


def compute_cache_hash(files):
    """Check the st_mtime result from stat'ing each file, and "add" them together by hashing them."""
    mtimes = []
    for file in files:
        mtimes.append(str(os.stat(file).st_mtime))
    return _sha256(''.join(mtimes))


def cache_is_unchanged():
    """Check if any template, IPC definition YAML, or protobuf source changed.
    Return `true` if no inputs changed, i.e. the stored cache hash is the same as the one we
    just computed."""
    files = _collect_templates() + _collect_definitions() + _collect_protos()
    current_cache_hash = compute_cache_hash(files)

    ipc_cache_file = pathlib.Path(IPC_CACHE_FILE)
    ipc_cache_file.touch(exist_ok=True)

    with open(ipc_cache_file, "r+") as f:
        stored_cache_hash = f.read()
        f.seek(0)
        f.write(current_cache_hash)
        f.truncate()
    return current_cache_hash == stored_cache_hash


def cache_destroy():
    """Delete the IPC cache file."""
    ipc_cache_file = pathlib.Path(IPC_CACHE_FILE)
    ipc_cache_file.unlink()


def add_to_auth_group(proto, gen):
    try:
        group = proto["auth"]
    except KeyError:
        raise KeyError(
            f"All protos must specify an auth group, but {proto['name']} does not.")
    if group == "never":
        gen[AUTH_GROUP_NEVER].append(proto["name"])
    elif group == "always":
        gen[AUTH_GROUP_ALWAYS].append(proto["name"])
    elif group == "after_onboarding":
        gen[AUTH_GROUP_AFTER_ONBOARDING].append(proto["name"])


def _generate_to_dir(output_dir, ignore_cache=False):
    """Generate code into specified output directory."""

    if not os.path.exists(output_dir):
        os.makedirs(output_dir)

    if not ignore_cache and cache_is_unchanged():
        return

    template = ENV.get_template("ipc_messages.jinja.h")

    # Enforce a 1:1 mapping of proto to port.
    protos_seen = set()

    gen = {"headers": [], "ports": [], "port_to_proto": {},
           AUTH_GROUP_NEVER: [],
           AUTH_GROUP_ALWAYS: [],
           AUTH_GROUP_AFTER_ONBOARDING: [],
           }
    proto_headers = []
    for proto in _collect_protos():
        proto_headers.append(os.path.basename(
            proto.replace(".proto", ".pb.h")))

    for file in _collect_definitions():
        with open(file, "r") as stream:
            defn = yaml.safe_load(stream)
            defn["proto_headers"] = proto_headers
            base_header = os.path.basename(
                template.filename.replace(".jinja", f"_{defn['port_name']}"))
            out_file = os.path.join(output_dir, base_header)
            open(out_file, "w+").write(template.render(defn))
            gen["headers"].append(base_header)
            gen["ports"].append(defn["port_name"])

            # Grab all the proto messages, and build a mapping of
            # port name --> proto.
            if "protos" in defn["messages"].keys():
                for proto in defn["messages"]["protos"]:
                    n = proto["name"]
                    add_to_auth_group(proto, gen)
                    if n in protos_seen:
                        raise Exception("Duplicate proto %s" % n)
                    protos_seen.add(n)
                    if defn["port_name"] not in gen["port_to_proto"]:
                        gen["port_to_proto"][defn["port_name"]] = []
                    gen["port_to_proto"][defn["port_name"]].append(n)

    # Collect all generated headers and port structs into a single file, for inclusion by ipc.h
    _render_template("ipc_port_gen.jinja.h", output_dir, gen)

    gen["num_ports"] = len(gen["ports"])
    _render_template("ipc_internal.jinja.c", output_dir, gen)


def generate_to_dir(output_dir, ignore_cache=False):
    try:
        _generate_to_dir(output_dir, ignore_cache=ignore_cache)
    except Exception as e:
        # Invalidate cache if we fail to generate to ensure
        # we try again next time.
        cache_destroy()
        raise e


def generated_sources_in_dir(output_dir):
    sources = _deterministic_glob(f"{output_dir}/**/*.[c|h]")
    for source in sources:
        print(source, end=" ")
    print()


@cli.command(help="Generate code for IPC subsystem.")
@click.option("--ignore-cache", is_flag=True)
def generate(ignore_cache):
    generate_to_dir(OUTPUT_DIR, ignore_cache=ignore_cache)


@cli.command(help="Output list of files generated by this script.")
def collect_generated():
    generated_sources_in_dir(OUTPUT_DIR)


if __name__ == "__main__":
    cli()
