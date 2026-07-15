import asyncio
import os
import ssl
import sys
import tempfile

from androidtvremote2 import AndroidTVRemote, InvalidAuth, CannotConnect, ConnectionClosed

# Monkey-patch bug in androidtvremote2: duplicate load_cert_chain call
_orig_create_ssl_context = AndroidTVRemote._create_ssl_context


def _patched_create_ssl_context(self):
    if self._ssl_context:
        return self._ssl_context
    ctx = ssl.SSLContext(ssl.PROTOCOL_TLS_CLIENT)
    ctx.check_hostname = False
    ctx.verify_mode = ssl.VerifyMode.CERT_NONE
    try:
        ctx.load_cert_chain(self._certfile, self._keyfile)
    except (FileNotFoundError, ssl.SSLError) as exc:
        raise InvalidAuth from exc
    self._ssl_context = ctx
    return self._ssl_context


AndroidTVRemote._create_ssl_context = _patched_create_ssl_context


class TvController:
    _loop = None
    _remote = None
    _ip = None
    _cert_dir = None

    @classmethod
    def _ensure_loop(cls):
        if cls._loop is None:
            cls._loop = asyncio.new_event_loop()
            asyncio.set_event_loop(cls._loop)

    @classmethod
    def _cleanup(cls):
        if cls._loop is not None:
            cls._loop.close()
        cls._loop = None
        cls._remote = None
        cls._ip = None
        cls._cert_dir = None

    @classmethod
    def _make_cert_dir(cls):
        d = tempfile.mkdtemp()
        cls._cert_dir = d
        return os.path.join(d, "cert.pem"), os.path.join(d, "key.pem")

    @classmethod
    def _cleanup_dir(cls):
        if cls._cert_dir:
            try:
                for f in os.listdir(cls._cert_dir):
                    os.unlink(os.path.join(cls._cert_dir, f))
                os.rmdir(cls._cert_dir)
            except OSError:
                pass
            cls._cert_dir = None

    @classmethod
    def _read_cert_files(cls):
        if not cls._cert_dir:
            return None, None
        cert_path = os.path.join(cls._cert_dir, "cert.pem")
        key_path = os.path.join(cls._cert_dir, "key.pem")
        try:
            with open(cert_path) as f:
                cert = f.read()
            with open(key_path) as f:
                key = f.read()
            return cert, key
        except OSError:
            return None, None

    @classmethod
    async def _do_pairing(cls, cert_path, key_path, ip):
        remote = AndroidTVRemote("RR BILLING PRO", cert_path, key_path, ip)
        await remote.async_generate_cert_if_missing()
        await remote.async_start_pairing()
        return remote

    @classmethod
    def start_pairing(cls, ip):
        print(f"[BillingPS] start_pairing ip={ip}", flush=True)
        cls._ensure_loop()
        cls._ip = ip
        cert_path, key_path = cls._make_cert_dir()
        print(f"[BillingPS] cert_path={cert_path} key_path={key_path}", flush=True)
        try:
            cls._remote = cls._loop.run_until_complete(
                cls._do_pairing(cert_path, key_path, ip)
            )
            cert_pem, key_pem = cls._read_cert_files()
            return {"status": "pin_shown_on_tv", "cert_pem": cert_pem, "key_pem": key_pem}
        except CannotConnect as e:
            print(f"[BillingPS] CannotConnect: {e}", flush=True)
            cls._cleanup()
            return {"status": "error", "error": f"Cannot connect to {ip}:6467"}
        except ConnectionClosed as e:
            print(f"[BillingPS] ConnectionClosed: {e}", flush=True)
            cls._cleanup()
            return {"status": "error", "error": f"Connection closed: {e}"}
        except InvalidAuth as e:
            print(f"[BillingPS] InvalidAuth: {e}", flush=True)
            cls._cleanup()
            return {"status": "error", "error": f"Invalid certificate: {e}"}
        except Exception as e:
            print(f"[BillingPS] start_pairing exception: {type(e).__name__}: {e}", flush=True)
            cls._cleanup()
            return {"status": "error", "error": str(e)}

    @classmethod
    def finish_pairing(cls, pin):
        print(f"[BillingPS] finish_pairing pin={pin}", flush=True)
        if cls._remote is None or cls._loop is None:
            print("[BillingPS] finish_pairing: remote or loop is None", flush=True)
            return {"paired": False, "error": "start_pairing first"}
        try:
            asyncio.set_event_loop(cls._loop)
            cls._loop.run_until_complete(cls._remote.async_finish_pairing(pin))
            cls._remote.disconnect()
            print("[BillingPS] finish_pairing OK", flush=True)
            return {"paired": True}
        except Exception as e:
            print(f"[BillingPS] finish_pairing exception: {type(e).__name__}: {e}", flush=True)
            return {"paired": False, "error": str(e)}
        finally:
            cls._cleanup()
            cls._cleanup_dir()

    @classmethod
    async def _do_send_key(cls, cert_path, key_path, ip, key_code):
        remote = AndroidTVRemote("RR BILLING PRO", cert_path, key_path, ip)
        await remote.async_connect()
        remote.send_key_command(key_code)
        remote.disconnect()

    @classmethod
    def send_key(cls, ip, cert_pem, key_pem, key_code):
        print(f"[BillingPS] send_key ip={ip} key={key_code}", flush=True)
        loop = asyncio.new_event_loop()
        asyncio.set_event_loop(loop)
        cert_path, key_path = cls._make_cert_dir()
        with open(cert_path, "w") as f:
            f.write(cert_pem)
        with open(key_path, "w") as f:
            f.write(key_pem)
        try:
            loop.run_until_complete(
                cls._do_send_key(cert_path, key_path, ip, key_code)
            )
            return {"sent": True}
        except CannotConnect as e:
            return {"sent": False, "error": f"Cannot connect to {ip}:6466"}
        except InvalidAuth as e:
            return {"sent": False, "error": "Invalid auth - need to pair first"}
        except Exception as e:
            print(f"[BillingPS] send_key exception: {type(e).__name__}: {e}", flush=True)
            return {"sent": False, "error": str(e)}
        finally:
            loop.close()
            cls._cleanup_dir()

    @classmethod
    def send_power(cls, ip, cert_pem, key_pem):
        return cls.send_key(ip, cert_pem, key_pem, "POWER")

    @classmethod
    def send_volume_up(cls, ip, cert_pem, key_pem):
        return cls.send_key(ip, cert_pem, key_pem, "VOLUME_UP")

    @classmethod
    def send_volume_down(cls, ip, cert_pem, key_pem):
        return cls.send_key(ip, cert_pem, key_pem, "VOLUME_DOWN")
