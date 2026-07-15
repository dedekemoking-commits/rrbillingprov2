import asyncio
import socket
from zeroconf import ServiceBrowser, Zeroconf


def scan_tv(timeout=5):
    loop = asyncio.new_event_loop()
    asyncio.set_event_loop(loop)
    try:
        return loop.run_until_complete(_scan(timeout))
    finally:
        loop.close()


def pair_tv(ip_address, pin, cert_dir):
    loop = asyncio.new_event_loop()
    asyncio.set_event_loop(loop)
    try:
        return loop.run_until_complete(_pair(ip_address, pin, cert_dir))
    finally:
        loop.close()


def send_key(ip_address, cert_dir, key):
    loop = asyncio.new_event_loop()
    asyncio.set_event_loop(loop)
    try:
        return loop.run_until_complete(_send_key(ip_address, cert_dir, key))
    finally:
        loop.close()


async def _scan(timeout):
    found_devices = []
    service_type = "_androidtvremote2._tcp.local."

    class TVListener:
        def add_service(self, zeroconf, service_type, name):
            info = zeroconf.get_service_info(service_type, name)
            if info:
                addresses = [socket.inet_ntoa(addr) for addr in info.addresses]
                device = {
                    "name": name.replace("." + service_type, ""),
                    "address": addresses[0] if addresses else "",
                    "port": info.port,
                }
                if device not in found_devices:
                    found_devices.append(device)

        def remove_service(self, zeroconf, service_type, name):
            pass

        def update_service(self, zeroconf, service_type, name):
            pass

    zc = Zeroconf()
    listener = TVListener()
    browser = ServiceBrowser(zc, service_type, listener)
    await asyncio.sleep(timeout)
    browser.cancel()
    zc.close()
    return found_devices


async def _pair(ip_address, pin, cert_dir):
    import os
    from androidtvremote2 import AndroidTVRemote

    certfile = os.path.join(cert_dir, "cert.pem")
    keyfile = os.path.join(cert_dir, "key.pem")

    remote = AndroidTVRemote("RR BILLING PRO", certfile, keyfile, ip_address)

    if not os.path.exists(certfile) or not os.path.exists(keyfile):
        generated = await remote.async_generate_cert_if_missing()
        if not generated:
            await remote.async_generate_cert_if_missing()

    await remote.async_start_pairing()
    await remote.async_finish_pairing(pin)
    remote.disconnect()

    return {"paired": True, "ip": ip_address}


async def _send_key(ip_address, cert_dir, key):
    import os
    from androidtvremote2 import AndroidTVRemote

    certfile = os.path.join(cert_dir, "cert.pem")
    keyfile = os.path.join(cert_dir, "key.pem")

    remote = AndroidTVRemote("RR BILLING PRO", certfile, keyfile, ip_address)
    await remote.async_connect()
    remote.send_key_command(key)
    remote.disconnect()
    return {"sent": True}
