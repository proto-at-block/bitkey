#! /usr/bin/env bash

if output=$(conftest test -p /policies/provider-allowlist/ providers-schema.json 2>&1); then
    :
else
    echo $output;
    exit 1;
fi
