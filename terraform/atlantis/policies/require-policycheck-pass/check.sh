#! /usr/bin/env bash

if output=$(conftest test -p /policies/require-policycheck-pass/ $POLICYCHECKFILE 2>&1); then
    :
else
    echo $output;
    exit 1;
fi
