#!/usr/bin/env python3
"""Verify customer-like Android Firebase config emitted by google-services."""

from pathlib import Path
import json
import sys
import xml.etree.ElementTree as ET

PACKAGE_NAME = "world.bitkey.app"
EXPECTED_PROJECT_NUMBER = "496981653612"
EXPECTED_PROJECT_ID = "sq-bitkey-prod"
EXPECTED_GOOGLE_APP_ID = "1:496981653612:android:c676cea4fe475e0a75c049"
EXPECTED_GOOGLE_API_KEYS = {
    "AIzaSyCH2YB54if-kY_yTj_meXvSABBbukohVVg",
}

GOOGLE_SERVICES_JSON = Path("android/app/google-services.json")


def variant_task_name(variant: str) -> str:
    return variant[:1].upper() + variant[1:]


def generated_values_candidates(variant: str) -> list[Path]:
    variant_task = variant_task_name(variant)
    return [
        Path(f"android/app/_build/generated/res/process{variant_task}GoogleServices/values/values.xml"),
        Path(f"android/app/_build/generated/res/google-services/{variant}/values/values.xml"),
    ]


def generated_values_file(variant: str) -> Path:
    for candidate in generated_values_candidates(variant):
        if candidate.exists():
            return candidate

    candidates = "\n".join(f"- {candidate}" for candidate in generated_values_candidates(variant))
    raise FileNotFoundError(
        f"Missing generated google-services values for variant {variant!r}. Run "
        f"`bin/ai-gradle :android:app:process{variant_task_name(variant)}GoogleServices` first. "
        f"Checked:\n{candidates}"
    )


def customer_client_config() -> dict[str, str]:
    data = json.loads(GOOGLE_SERVICES_JSON.read_text())
    project_info = data["project_info"]

    for client in data["client"]:
        client_info = client["client_info"]
        android_info = client_info["android_client_info"]
        if android_info["package_name"] == PACKAGE_NAME:
            return {
                "gcm_defaultSenderId": project_info["project_number"],
                "google_api_key": client["api_key"][0]["current_key"],
                "google_app_id": client_info["mobilesdk_app_id"],
                "project_id": project_info["project_id"],
            }

    raise ValueError(f"No google-services client found for {PACKAGE_NAME}")


def generated_values(variant: str) -> dict[str, str | None]:
    root = ET.parse(generated_values_file(variant)).getroot()
    return {
        element.attrib["name"]: element.text
        for element in root.findall("string")
        if "name" in element.attrib
    }


def display_value(key: str, value: str | None) -> str:
    if key == "google_api_key" and value is not None:
        return "<redacted API key>"
    return repr(value)


def validate_source_of_truth(expected_values: dict[str, str]) -> list[str]:
    failures = []
    expected_static_values = {
        "gcm_defaultSenderId": EXPECTED_PROJECT_NUMBER,
        "google_app_id": EXPECTED_GOOGLE_APP_ID,
        "project_id": EXPECTED_PROJECT_ID,
    }
    for key, expected in expected_static_values.items():
        actual = expected_values.get(key)
        if actual != expected:
            failures.append(
                f"{GOOGLE_SERVICES_JSON} {key}: expected {display_value(key, expected)}, "
                f"got {display_value(key, actual)}"
            )

    selected_api_key = expected_values.get("google_api_key")
    if selected_api_key not in EXPECTED_GOOGLE_API_KEYS:
        failures.append(
            f"{GOOGLE_SERVICES_JSON} google_api_key: expected an approved customer API key, "
            f"got {display_value('google_api_key', selected_api_key)}"
        )

    return failures


def main() -> int:
    variant = sys.argv[1] if len(sys.argv) > 1 else "customer"

    try:
        expected_values = customer_client_config()
        actual_values = generated_values(variant)
    except (FileNotFoundError, ValueError, KeyError) as error:
        print(error, file=sys.stderr)
        return 1

    failures = validate_source_of_truth(expected_values)
    for key, expected in expected_values.items():
        actual = actual_values.get(key)
        if actual != expected:
            failures.append(
                f"{variant} {key}: expected {display_value(key, expected)}, "
                f"got {display_value(key, actual)}"
            )

    if failures:
        print(
            f"Customer google-services output is invalid ({len(failures)} checks failed).",
            file=sys.stderr,
        )
        return 1

    print(f"Customer google-services output is valid for {variant}.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
