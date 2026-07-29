import os
import sys
import tempfile
import json

from adb_shell.adb_device import AdbDeviceTcp
from adb_shell.auth.sign_pythonrsa import RSASigner
from adb_shell.exceptions import (
    TcpTimeoutException,
    DeviceAuthError,
)

RSA_KEY_DIR = None


def _ensure_key_dir():
    global RSA_KEY_DIR
    if RSA_KEY_DIR is None:
        RSA_KEY_DIR = tempfile.mkdtemp()
    return RSA_KEY_DIR


def _get_rsa_signer():
    key_dir = _ensure_key_dir()
    priv_path = os.path.join(key_dir, "adbkey")
    pub_path = os.path.join(key_dir, "adbkey.pub")

    if not os.path.exists(priv_path):
        rsa_signer = RSASigner.Create()
        with open(priv_path, "w") as f:
            f.write(rsa_signer.GetRsaKeyString())
        with open(pub_path, "w") as f:
            f.write(rsa_signer.GetPublicKeyString())
    else:
        with open(priv_path) as f:
            rsa_signer = RSASigner.Load(f.read())
    return rsa_signer


def adb_direct_install(ip, port, apk_path):
    """
    Try direct ADB connection and APK installation.
    Works on Android 10- or devices that are already paired.
    For Android 11+, connect to the random port shown after pairing.
    """
    print(f"[ADB] Direct connect to {ip}:{port}", flush=True)
    try:
        signer = _get_rsa_signer()
        device = AdbDeviceTcp(ip, port)
        device.connect(rsa_keys=[signer], auth_timeout_s=5)
        print(f"[ADB] Connected! Installing {apk_path}", flush=True)
        result = device.install(apk_path)
        print(f"[ADB] Install result: {result}", flush=True)
        device.close()
        return {"success": True, "message": "APK berhasil diinstall!"}
    except DeviceAuthError:
        return {"success": False, "message": "DEVICE_UNAUTHORIZED"}
    except TcpTimeoutException:
        return {"success": False, "message": "CONNECTION_TIMEOUT"}
    except Exception as e:
        print(f"[ADB] Direct install error: {type(e).__name__}: {e}", flush=True)
        return {"success": False, "message": str(e)}


def adb_connect_and_install(ip, connection_port, apk_path):
    """
    Connect to device (connection port from Wireless Debugging) and install APK.
    The device must already be paired (via PC 'adb pair' or previous pairing).
    """
    print(f"[ADB] Connecting to {ip}:{connection_port}", flush=True)
    try:
        signer = _get_rsa_signer()
        device = AdbDeviceTcp(ip, connection_port)
        device.connect(rsa_keys=[signer], auth_timeout_s=10)
        print(f"[ADB] Connected! Installing {apk_path}", flush=True)

        if not os.path.exists(apk_path):
            return {"success": False, "message": f"File APK tidak ditemukan"}

        result = device.install(apk_path)
        print(f"[ADB] Install result: {result}", flush=True)
        device.close()
        return {"success": True, "message": "APK berhasil diinstall!"}

    except DeviceAuthError as e:
        return {"success": False, "message": f"Autentikasi gagal. Lakukan pairing ulang."}
    except TcpTimeoutException:
        return {"success": False, "message": f"Timeout koneksi. Coba lagi."}
    except Exception as e:
        print(f"[ADB] Install error: {type(e).__name__}: {e}", flush=True)
        return {"success": False, "message": str(e)}
