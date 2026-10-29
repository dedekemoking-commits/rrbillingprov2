import asyncio
import os
import ssl
import sys
import tempfile
import threading
import time

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
    _locks_guard = threading.Lock()
    _ip_locks = {}

    @classmethod
    def _lock_for(cls, ip):
        with cls._locks_guard:
            lock = cls._ip_locks.get(ip)
            if lock is None:
                lock = threading.Lock()
                cls._ip_locks[ip] = lock
            return lock

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

    @staticmethod
    def _write_cert_files(cert_dir, cert_pem, key_pem):
        cert_path = os.path.join(cert_dir, "cert.pem")
        key_path = os.path.join(cert_dir, "key.pem")
        with open(cert_path, "w") as f:
            f.write(cert_pem)
        with open(key_path, "w") as f:
            f.write(key_pem)
        return cert_path, key_path

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
        try:
            await asyncio.wait_for(
                remote.async_connect(), timeout=cls.CONNECT_TIMEOUT
            )
            # Beri waktu remote service mendaftarkan uinput device. Tanpa
            # jeda ini, key pertama sering hilang (penyebab harus spam klik).
            await asyncio.sleep(cls.KEY_SETTLE_DELAY)
            remote.send_key_command(key_code)
            await asyncio.sleep(0.5)
        finally:
            cls._cancel_idle_task(remote)
            try:
                remote.disconnect()
            except Exception:
                pass

    @classmethod
    async def _do_send_keys(cls, cert_path, key_path, ip, keys):
        remote = AndroidTVRemote("RR BILLING PRO", cert_path, key_path, ip)
        try:
            await asyncio.wait_for(
                remote.async_connect(), timeout=cls.CONNECT_TIMEOUT
            )
            await asyncio.sleep(cls.KEY_SETTLE_DELAY)
            for key in keys:
                remote.send_key_command(key)
                await asyncio.sleep(0.25)
            await asyncio.sleep(0.4)
        finally:
            cls._cancel_idle_task(remote)
            try:
                remote.disconnect()
            except Exception:
                pass

    CONNECT_TIMEOUT = 3.0
    CONNECT_ATTEMPTS = 3
    CONNECT_RETRY_DELAY = 0.4
    KEY_SETTLE_DELAY = 0.3
    POWER_FALLBACK_ATTEMPTS = 3
    POWER_STATE_DELAY = 4.0
    POWER_WAKE_GRACE = 2.0
    POWER_SLEEP_GRACE = 0.8

    @staticmethod
    def _remote_is_closed(remote):
        protocol = getattr(remote, "_remote_message_protocol", None)
        if protocol is None:
            return True
        transport = getattr(protocol, "transport", None)
        if transport is None:
            return True
        return transport.is_closing()

    @staticmethod
    def _cancel_idle_task(remote):
        protocol = getattr(remote, "_remote_message_protocol", None)
        task = getattr(protocol, "_idle_disconnect_task", None)
        if task is not None:
            try:
                task.cancel()
            except Exception:
                pass

    @staticmethod
    def _power_message(target_on, confirmed, already=False):
        if already:
            return "TV sudah menyala" if target_on else "TV sudah tidur"
        if confirmed:
            return "TV mengonfirmasi menyala" if target_on else "TV mengonfirmasi tidur"
        return (
            "Perintah nyala terkirim ke TV"
            if target_on
            else "Perintah tidur terkirim ke TV"
        )

    @classmethod
    async def _do_send_power(cls, cert_path, key_path, ip, on):
        remote = AndroidTVRemote("RR BILLING PRO", cert_path, key_path, ip)
        try:
            await asyncio.wait_for(
                remote.async_connect(), timeout=cls.CONNECT_TIMEOUT
            )
            await asyncio.sleep(cls.KEY_SETTLE_DELAY)
            current = remote.is_on
            target = on
            if target is None:
                if current is None:
                    return {
                        "sent": False,
                        "confirmed": False,
                        "error": "Status power TV tidak diketahui.",
                    }
                target = not current
            target = bool(target)

            # Jangan kirim apa pun bila TV sudah di target. Ini penting agar
            # fallback POWER tidak membalik state yang sudah benar.
            if current is not None and current == target:
                return {
                    "sent": True,
                    "confirmed": True,
                    "changed": False,
                    "is_on": current,
                    "previous_on": current,
                    "target_on": target,
                    "key": None,
                    "message": cls._power_message(target, True, already=True),
                }

            # Key eksplisit bersifat idempoten. Sebagian TV tidak mendukung
            # SLEEP/WAKEUP; fallback POWER ditangani oleh send_power().
            key = "WAKEUP" if target else "SLEEP"
            print(
                f"[BillingPS] send_power {ip}: current={current} "
                f"target={target} key={key}",
                flush=True,
            )
            remote.send_key_command(key)
            await asyncio.sleep(0.5)
            closed = cls._remote_is_closed(remote)
            confirmed = closed or remote.is_on == target
            return {
                "sent": True,
                "confirmed": confirmed,
                "changed": True,
                "is_on": target,
                "previous_on": current,
                "target_on": target,
                "key": key,
                "closed": closed,
                "message": cls._power_message(target, confirmed),
            }
        finally:
            cls._cancel_idle_task(remote)
            try:
                remote.disconnect()
            except Exception:
                pass

    @classmethod
    async def _do_send_power_fallback(cls, cert_path, key_path, ip, target):
        """Fallback untuk TV yang tidak mengenal KEYCODE_WAKEUP/SLEEP.

        POWER adalah toggle, jadi state dibaca ulang dari koneksi baru
        sebelum setiap penekanan. Bila TV sudah di target, POWER tidak
        dikirim sehingga tidak terjadi toggle bolak-balik.
        """
        remote = AndroidTVRemote("RR BILLING PRO", cert_path, key_path, ip)
        try:
            await asyncio.wait_for(
                remote.async_connect(), timeout=cls.CONNECT_TIMEOUT
            )
            await asyncio.sleep(cls.KEY_SETTLE_DELAY)
            current = remote.is_on
            if current is None:
                return {
                    "sent": False,
                    "confirmed": False,
                    "error": "Status power TV tidak diketahui.",
                }
            if current == target:
                return {
                    "sent": True,
                    "confirmed": True,
                    "changed": False,
                    "is_on": current,
                    "previous_on": current,
                    "target_on": target,
                    "key": None,
                    "message": cls._power_message(target, True, already=True),
                }
            print(
                f"[BillingPS] send_power fallback {ip}: current={current} "
                f"target={target} key=POWER",
                flush=True,
            )
            remote.send_key_command("POWER")
            await asyncio.sleep(0.7)
            closed = cls._remote_is_closed(remote)
            confirmed = closed or remote.is_on == target
            return {
                "sent": True,
                "confirmed": confirmed,
                "changed": True,
                "is_on": target,
                "previous_on": current,
                "target_on": target,
                "key": "POWER",
                "closed": closed,
                "message": cls._power_message(target, confirmed),
            }
        finally:
            cls._cancel_idle_task(remote)
            try:
                remote.disconnect()
            except Exception:
                pass

    @classmethod
    def send_key(cls, ip, cert_pem, key_pem, key_code):
        print(f"[BillingPS] send_key ip={ip} key={key_code}", flush=True)
        with cls._lock_for(ip):
            cert_dir = tempfile.mkdtemp()
            cert_path, key_path = cls._write_cert_files(cert_dir, cert_pem, key_pem)
            loop = asyncio.new_event_loop()
            asyncio.set_event_loop(loop)
            try:
                for attempt in range(2):
                    try:
                        loop.run_until_complete(
                            cls._do_send_key(cert_path, key_path, ip, key_code)
                        )
                        return {"sent": True}
                    except (CannotConnect, asyncio.TimeoutError):
                        if attempt == 0:
                            time.sleep(0.4)
                            continue
                        return {"sent": False, "error": f"Cannot connect to {ip}:6466"}
                    except InvalidAuth:
                        return {"sent": False, "error": "Invalid auth - need to pair first"}
                    except ConnectionClosed as e:
                        return {"sent": False, "error": f"Connection closed: {e}"}
                    except Exception as e:
                        print(f"[BillingPS] send_key exception: {type(e).__name__}: {e}", flush=True)
                        return {"sent": False, "error": str(e)}
                return {"sent": False, "error": "Tidak dapat terhubung ke TV"}
            finally:
                loop.close()
                try:
                    for f in os.listdir(cert_dir):
                        os.unlink(os.path.join(cert_dir, f))
                    os.rmdir(cert_dir)
                except OSError:
                    pass

    @classmethod
    def send_keys(cls, ip, cert_pem, key_pem, keys_csv):
        keys = [k.strip() for k in str(keys_csv or "").split(",") if k.strip()]
        print(f"[BillingPS] send_keys ip={ip} keys={keys}", flush=True)
        if not keys:
            return {"sent": False, "error": "Key sequence kosong"}
        with cls._lock_for(ip):
            cert_dir = tempfile.mkdtemp()
            cert_path, key_path = cls._write_cert_files(cert_dir, cert_pem, key_pem)
            loop = asyncio.new_event_loop()
            asyncio.set_event_loop(loop)
            try:
                for attempt in range(2):
                    try:
                        loop.run_until_complete(
                            cls._do_send_keys(cert_path, key_path, ip, keys)
                        )
                        return {"sent": True, "count": len(keys)}
                    except (CannotConnect, asyncio.TimeoutError):
                        if attempt == 0:
                            time.sleep(0.4)
                            continue
                        return {"sent": False, "error": f"Cannot connect to {ip}:6466"}
                    except InvalidAuth:
                        return {"sent": False, "error": "Invalid auth - need to pair first"}
                    except ConnectionClosed as e:
                        return {"sent": False, "error": f"Connection closed: {e}"}
                    except Exception as e:
                        print(f"[BillingPS] send_keys exception: {type(e).__name__}: {e}", flush=True)
                        return {"sent": False, "error": str(e)}
                return {"sent": False, "error": "Tidak dapat terhubung ke TV"}
            finally:
                loop.close()
                try:
                    for f in os.listdir(cert_dir):
                        os.unlink(os.path.join(cert_dir, f))
                    os.rmdir(cert_dir)
                except OSError:
                    pass

    @classmethod
    def send_power(cls, ip, cert_pem, key_pem, on=None):
        print(f"[BillingPS] send_power ip={ip} on={on}", flush=True)
        with cls._lock_for(ip):
            cert_dir = tempfile.mkdtemp()
            cert_path, key_path = cls._write_cert_files(cert_dir, cert_pem, key_pem)
            loop = asyncio.new_event_loop()
            asyncio.set_event_loop(loop)
            try:
                last_error = "Tidak dapat terhubung ke TV"
                initial = None
                for attempt in range(cls.CONNECT_ATTEMPTS):
                    try:
                        initial = loop.run_until_complete(
                            cls._do_send_power(cert_path, key_path, ip, on)
                        )
                        break
                    except InvalidAuth:
                        return {"sent": False, "confirmed": False,
                                "error": "Invalid auth - need to pair first"}
                    except (CannotConnect, asyncio.TimeoutError) as e:
                        last_error = f"Tidak dapat terhubung ke {ip}:6466"
                        print(
                            f"[BillingPS] send_power attempt {attempt + 1} gagal: {e}",
                            flush=True,
                        )
                    except ConnectionClosed as e:
                        last_error = f"Koneksi TV terputus: {e}"
                        print(
                            f"[BillingPS] send_power attempt {attempt + 1} gagal: {e}",
                            flush=True,
                        )
                    except Exception as e:
                        print(
                            f"[BillingPS] send_power exception: {type(e).__name__}: {e}",
                            flush=True,
                        )
                        return {"sent": False, "confirmed": False, "error": str(e)}
                    if attempt < cls.CONNECT_ATTEMPTS - 1:
                        time.sleep(cls.CONNECT_RETRY_DELAY)

                if initial is None:
                    return {"sent": False, "confirmed": False, "error": last_error}
                if initial.get("confirmed") or not initial.get("changed", True):
                    return initial

                target = initial.get("target_on")
                if target is None:
                    return initial

                # Beri TV waktu memproses WAKEUP/SLEEP sebelum fallback.
                grace = cls.POWER_WAKE_GRACE if target else cls.POWER_SLEEP_GRACE
                time.sleep(grace)

                last = initial
                for attempt in range(cls.POWER_FALLBACK_ATTEMPTS):
                    try:
                        fb = loop.run_until_complete(
                            cls._do_send_power_fallback(
                                cert_path, key_path, ip, target
                            )
                        )
                    except InvalidAuth:
                        return {"sent": False, "confirmed": False,
                                "error": "Invalid auth - need to pair first"}
                    except (CannotConnect, asyncio.TimeoutError, ConnectionClosed) as e:
                        # Setelah key terkirim, TV tidak bisa dihubungi:
                        # biasanya sedang transisi (boot/standby).
                        print(
                            f"[BillingPS] send_power fallback probe gagal: {e}",
                            flush=True,
                        )
                        return {
                            **last,
                            "confirmed": True,
                            "message": cls._power_message(target, True),
                        }
                    except Exception as e:
                        print(
                            f"[BillingPS] send_power fallback exception: "
                            f"{type(e).__name__}: {e}",
                            flush=True,
                        )
                        return {**last, "confirmed": False, "error": str(e)}

                    if fb.get("confirmed") or not fb.get("changed", True):
                        return fb
                    last = fb
                    if attempt < cls.POWER_FALLBACK_ATTEMPTS - 1:
                        time.sleep(cls.POWER_STATE_DELAY)
                return last
            finally:
                loop.close()
                try:
                    for f in os.listdir(cert_dir):
                        os.unlink(os.path.join(cert_dir, f))
                    os.rmdir(cert_dir)
                except OSError:
                    pass

    @classmethod
    def send_volume_up(cls, ip, cert_pem, key_pem):
        return cls.send_key(ip, cert_pem, key_pem, "VOLUME_UP")

    @classmethod
    def send_volume_down(cls, ip, cert_pem, key_pem):
        return cls.send_key(ip, cert_pem, key_pem, "VOLUME_DOWN")
