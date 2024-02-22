#!/bin/bash

# By default, the enclave has lo (the loopback adapter) turned off. we need to flip it on
/sbin/ifconfig lo up
ROCKET_ADDRESS=0.0.0.0 ROCKET_PORT=8080 /wsm-enclave start-server &
socat vsock-listen:7446,fork,reuseaddr tcp4-connect:127.0.0.1:8080