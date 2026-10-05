from typing import TypeVar


SemverT = TypeVar("SemverT")

MAX_VERSION_MAJOR = 99
MAX_VERSION_MINOR = 99
MAX_VERSION_PATCH = 255

SEMVER_COMPONENT_LIMITS = (
    ("major", MAX_VERSION_MAJOR),
    ("minor", MAX_VERSION_MINOR),
    ("patch", MAX_VERSION_PATCH),
)


def validate_semver(version: SemverT) -> SemverT:
    if any(
        getattr(version, identifier, None) is not None
        for identifier in ("prerelease", "build")
    ):
        raise ValueError(
            f"Version {version} cannot be represented by firmware formats: "
            "prerelease and build identifiers are not supported"
        )

    for component, maximum in SEMVER_COMPONENT_LIMITS:
        if getattr(version, component) > maximum:
            raise ValueError(
                f"Version {version} cannot be represented by firmware formats: "
                f"{component} must not exceed {maximum}"
            )

    return version
