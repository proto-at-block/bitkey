#!/usr/bin/env bash

/usr/local/bin/socat tcp4-listen:7446,reuseaddr,fork vsock-connect:1234:7446