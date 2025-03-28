#!/usr/bin/env python3

import json
import socket
import ssl
import time
from typing import Optional, Tuple

def check_node(name: str, host: str, port: int, use_ssl: bool) -> None:
    """Check an Electrum node's height and status."""
    print(f"Checking {name}...")
    
    result = query_node(host, port, use_ssl)
    if result is None:
        print("✗ Error: Connection failed or timed out")
    else:
        try:
            height = result['result']['height']
            print(f"✓ Height: {height}")
        except (KeyError, json.JSONDecodeError):
            print("✗ Error: Received invalid response")
    print()

def query_node(host: str, port: int, use_ssl: bool) -> Optional[dict]:
    """Query an Electrum node and return the parsed JSON response."""
    request = {
        "id": 1,
        "method": "blockchain.headers.subscribe",
        "params": []
    }
    
    try:
        # Create the base socket with timeout
        sock = socket.create_connection((host, port), timeout=5)
        
        if use_ssl:
            context = ssl.create_default_context()
            context.minimum_version = ssl.TLSVersion.TLSv1_2
            context.check_hostname = False
            context.verify_mode = ssl.CERT_NONE
            sock = context.wrap_socket(sock)
        
        # Send request
        sock.sendall(json.dumps(request).encode() + b'\n')
        
        # Read response
        response = sock.recv(1024).decode()
        return json.loads(response)
        
    except (socket.timeout, socket.error, json.JSONDecodeError, ssl.SSLError):
        return None
    finally:
        try:
            sock.close()
        except:
            pass

def main():
    print("=== Checking Electrum Nodes ===\n")
    
    # Blockstream nodes
    nodes = [
        ("Blockstream Virginia, USA", "35.192.20.52", 50001, False),
        ("Blockstream Frankfurt, Germany", "34.147.244.49", 50001, False),
        ("Blockstream Tokyo, Japan", "34.131.144.107", 50001, False),
        
        # Mempool.space nodes
        ("Mempool.space Virginia, USA", "bitkey.va1.mempool.space", 50002, True),
        ("Mempool.space Frankfurt, Germany", "bitkey.fra.mempool.space", 50002, True),
        ("Mempool.space Tokyo, Japan", "bitkey.tk7.mempool.space", 50002, True),
    ]
    
    for name, host, port, use_ssl in nodes:
        check_node(name, host, port, use_ssl)

if __name__ == "__main__":
    main() 