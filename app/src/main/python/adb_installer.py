import os
import tempfile

from adb_shell.adb_device import AdbDeviceTcp
from adb_shell.auth.keygen import keygen
from adb_shell.auth.sign_pythonrsa import PythonRSASigner
from adb_shell.exceptions import (
    TcpTimeoutException,
    DeviceAuthError,
)

RSA_KEY_DIR = None
REMOTE_APK = "/data/local/tmp/billingps-tv-client.apk"


def adb_set_key_dir(path):
    global RSA_KEY_DIR
    if path:
        os.makedirs(path, exist_ok=True)
        RSA_KEY_DIR = path
    return {"success": True, "path": RSA_KEY_DIR or ""}


def _ensure_key_dir():
    global RSA_KEY_DIR
    if RSA_KEY_DIR is None:
        RSA_KEY_DIR = tempfile.mkdtemp()
    os.makedirs(RSA_KEY_DIR, exist_ok=True)
    return RSA_KEY_DIR


def _key_paths():
    key_dir = _ensure_key_dir()
    private_path = os.path.join(key_dir, "adbkey")
    public_path = private_path + ".pub"
    if not os.path.exists(private_path) or not os.path.exists(public_path):
        keygen(private_path)
    return private_path, public_path


def _get_signer():
    private_path, _ = _key_paths()
    try:
        return PythonRSASigner.FromRSAKeyPath(private_path)
    except Exception:
        keygen(private_path)
        return PythonRSASigner.FromRSAKeyPath(private_path)


def _output_text(value):
    if value is None:
        return ""
    if isinstance(value, bytes):
        return value.decode("utf-8", "replace")
    return str(value)


def _install_apk(device, apk_path):
    if not apk_path or not os.path.exists(apk_path):
        return {"success": False, "message": "File APK tidak ditemukan"}

    try:
        device.push(apk_path, REMOTE_APK, st_mode=0o644)
    except Exception as exc:
        return {"success": False, "message": "Gagal push APK: {}".format(exc)}

    try:
        result = device.shell(
            "pm install -r {}".format(REMOTE_APK),
            timeout_s=90.0,
        )
    finally:
        try:
            device.shell("rm -f {}".format(REMOTE_APK))
        except Exception:
            pass

    output = _output_text(result).strip()
    print("[ADB] install output: {}".format(output), flush=True)
    if "Success" in output:
        return {"success": True, "message": "APK berhasil diinstall!"}
    return {
        "success": False,
        "message": (output or "pm install gagal tanpa pesan")[:500],
    }


def _tcp_install(ip, port, apk_path):
    device = None
    try:
        signer = _get_signer()
        device = AdbDeviceTcp(ip, int(port), default_transport_timeout_s=8.0)
        connected = device.connect(rsa_keys=[signer], auth_timeout_s=10.0)
        if not connected:
            return {"success": False, "message": "ADB tidak terhubung"}
        print("[ADB] Connected to {}:{}. Installing {}".format(ip, port, apk_path), flush=True)
        return _install_apk(device, apk_path)
    except DeviceAuthError:
        return {"success": False, "message": "DEVICE_UNAUTHORIZED"}
    except TcpTimeoutException:
        return {"success": False, "message": "CONNECTION_TIMEOUT"}
    except Exception as exc:
        print("[ADB] TCP install error: {}: {}".format(type(exc).__name__, exc), flush=True)
        return {"success": False, "message": str(exc)}
    finally:
        if device is not None:
            try:
                device.close()
            except Exception:
                pass


def adb_direct_install(ip, port, apk_path):
    print("[ADB] Direct connect to {}:{}".format(ip, port), flush=True)
    return _tcp_install(ip, port, apk_path)


def adb_connect_and_install(ip, connection_port, apk_path):
    print("[ADB] Connecting to {}:{}".format(ip, connection_port), flush=True)
    return _tcp_install(ip, connection_port, apk_path)


def adb_pair(ip, pairing_port, pairing_code):
    try:
        from adb_spake2_compat import install as install_spake2_compat
        install_spake2_compat()
        from adb_shell_wifi.pairing import pair as wifi_pair
    except Exception as exc:
        return {
            "success": False,
            "message": "Pairing module tidak tersedia: {}".format(exc),
        }

    code = str(pairing_code or "").strip()
    if not code:
        return {"success": False, "message": "Kode pairing kosong"}
    try:
        port = int(pairing_port)
    except Exception:
        return {"success": False, "message": "Port pairing tidak valid"}

    try:
        private_path, public_path = _key_paths()
        with open(private_path, "rb") as private_file:
            private_pem = private_file.read()
        with open(public_path, "rb") as public_file:
            public_key = public_file.read()
        print("[ADB] Pairing {}:{}".format(ip, port), flush=True)
        peer = wifi_pair(
            ip,
            port,
            code,
            private_pem,
            public_key,
            timeout_s=30.0,
        )
        peer_data = getattr(peer, "data", b"")
        return {
            "success": True,
            "message": "Pairing berhasil",
            "peer": _output_text(peer_data)[:200],
        }
    except Exception as exc:
        print("[ADB] Pairing error: {}: {}".format(type(exc).__name__, exc), flush=True)
        return {
            "success": False,
            "message": "Pairing gagal: {}".format(exc),
        }


def adb_tls_install(ip, connect_port, apk_path):
    try:
        from adb_shell_wifi.adb_device import AdbDeviceTls
    except Exception as exc:
        return {
            "success": False,
            "message": "TLS installer tidak tersedia: {}".format(exc),
        }

    try:
        port = int(connect_port)
    except Exception:
        return {"success": False, "message": "Port koneksi tidak valid"}

    device = None
    try:
        private_path, _ = _key_paths()
        with open(private_path, "rb") as private_file:
            private_pem = private_file.read()
        device = AdbDeviceTls(ip, port, default_transport_timeout_s=10.0)
        connected = device.connect(
            rsa_keys=[],
            auth_timeout_s=10.0,
            tls_priv_pem=private_pem,
        )
        if not connected:
            return {"success": False, "message": "ADB TLS tidak terhubung"}
        print("[ADB] TLS connected to {}:{}. Installing {}".format(ip, port, apk_path), flush=True)
        return _install_apk(device, apk_path)
    except Exception as exc:
        print("[ADB] TLS install error: {}: {}".format(type(exc).__name__, exc), flush=True)
        return {"success": False, "message": str(exc)}
    finally:
        if device is not None:
            try:
                device.close()
            except Exception:
                pass


def _discover_services(ip, service_type, timeout_s):
    if service_type == "pairing":
        from adb_shell_wifi.mdns import discover_pairing_services
        services = discover_pairing_services(timeout_s=timeout_s)
    else:
        from adb_shell_wifi.mdns import discover_connect_services
        services = discover_connect_services(timeout_s=timeout_s)

    target = str(ip).strip()
    matched = [service for service in services if str(service.host).strip() == target]
    if not matched and len(services) == 1:
        matched = list(services)

    if matched:
        service = matched[0]
        return {
            "success": True,
            "host": service.host,
            "port": int(service.port),
            "name": service.name,
        }

    return {
        "success": False,
        "message": "Service ADB tidak ditemukan di jaringan",
        "services": [
            {
                "host": service.host,
                "port": int(service.port),
                "name": service.name,
            }
            for service in services
        ],
    }


def adb_discover_connect_port(ip, timeout_s=6.0):
    try:
        return _discover_services(ip, "connect", float(timeout_s))
    except Exception as exc:
        print("[ADB] mDNS connect error: {}: {}".format(type(exc).__name__, exc), flush=True)
        return {"success": False, "message": str(exc)}


def adb_discover_pairing_port(ip, timeout_s=6.0):
    try:
        return _discover_services(ip, "pairing", float(timeout_s))
    except Exception as exc:
        print("[ADB] mDNS pairing error: {}: {}".format(type(exc).__name__, exc), flush=True)
        return {"success": False, "message": str(exc)}
