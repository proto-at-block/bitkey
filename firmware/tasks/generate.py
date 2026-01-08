import pprint

from invoke import task

from bitkey.meson import MesonBuild
from bitkey.metadata import Metadata
from config.partitions import LinkerGenerator

from .lib.paths import ROOT_DIR


@task(help={
    "target": "Custom target to update with",
    "generate": "Flag to generate metadata",
    "build_type": "Build type (dev, prod)",
    "fw_image_type": "App or BL",
    "input": "Generated metadata output file",
    "output": "Generated metadata output file",
    "json": "Output file format in json rather than msgpack",
    "print": "Print the metadata"
})
def meta(c, target=None, generate=False, build_type="dev", fw_image_type="", input="", output="", json=False, _print=False, hw_rev=""):
    """Generate and/or print firmware metadata"""
    if input == "":
        target = target if target else c.target

        # Find the actual file associated with this target.
        input = MesonBuild(c, target=target).target.bin
        assert input, f"Could not find {target}"

    m = Metadata(input)

    build_meta = {
        'build_type': build_type[:7],   # Truncate to 7 characters
        'hw_rev': hw_rev[:32]           # Truncate to 32 characters
    }

    if generate:
        m.generate(output, json_out=json, data=build_meta,
                   image_type=fw_image_type)

    if _print or not generate:
        data = m.load()
        pp = pprint.PrettyPrinter(indent=4)
        pp.pprint(data)


@task(help={
    "target": "Custom target to generate linker script for",
    "platform": "Target platform",
    "output": "Generated linker script output file",
})
def linker(c, target=None, platform=None, output=None):
    """Generate the linker script for a target"""
    target = target if target else c.target
    platform = platform if platform else c.platform

    # Get the target information
    mb = MesonBuild(c, target=target, platform=platform)
    lg = LinkerGenerator(target, mb.platform['partitions'], mb.is_bootloader)
    lg.generate(output)
