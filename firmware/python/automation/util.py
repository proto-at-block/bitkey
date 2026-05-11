"""Helper functions for the functional testing."""

import re

# Known hardware revisions (ordered longest-first for proper matching).
_REVISIONS = ('proto', 'pdvt', 'dvt', 'evt')


def convert_target_app_name(
    target: str,
    config: str,
    revision: str,
    variant: str | None = None,
    slot: str | None = None
) -> str:
    """Convert a base target name to a specific configuration.

    Takes a base target from `platforms.yaml` and transforms it based on the
    provided build configuration and hardware revision.

    Target format: `{hardware}-{revision}-app-{slot}-{build_env}`

    :param target: base target name (e.g. 'w1a-evt-app-a-dev').
    :param config: build config (e.g. 'dev', 'prod', 'mfgtest-dev').
    :param revision: hardware revision (e.g. 'evt', 'dvt').
    :param variant: optional variant to prepend to config (e.g. 'mfgtest').
    :param slot: optional slot override ('a' or 'b').
    :return: transformed target name.
    """
    m = re.match(r'^(.+)-app-([ab])-(.+)$', target)
    if not m:
        raise ValueError(f"Invalid target format: {target}")

    hw_rev_prefix, base_slot, base_config = m.groups()

    # Replace revision in the hardware-revision prefix
    new_hw_rev: str = hw_rev_prefix
    if revision:
        for rev in _REVISIONS:
            if hw_rev_prefix.endswith(f'-{rev}'):
                new_hw_rev = hw_rev_prefix[:-len(rev)] + revision
                break

    new_slot: str = slot if slot else base_slot

    if variant:
        base = config if config else base_config.split('-')[-1]
        new_config = f"{variant}-{base}"
    else:
        new_config = config if config else base_config

    return f"{new_hw_rev}-app-{new_slot}-{new_config}"
