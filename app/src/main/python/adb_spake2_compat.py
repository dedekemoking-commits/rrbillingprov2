import hashlib
import os

from spake2 import ed25519_basic as _ed


_M = _ed.ElementOfUnknownGroup(
    _ed.xform_affine_to_extended(
        _ed.decodepoint(bytes.fromhex(
            "5ada7e4bf6ddd9adb6626d32131c6b5c"
            "51a1e347a3478f53cfcf441b88eed12e"
        ))
    )
)
_N = _ed.ElementOfUnknownGroup(
    _ed.xform_affine_to_extended(
        _ed.decodepoint(bytes.fromhex(
            "10e3df0ae37d8e7a99b5fe74b4467210"
            "3dbddcbd06af680d71329a11693bc778"
        ))
    )
)
_ORDER = _ed.L
_MAX_SCALAR = (1 << 256) - 1


def _length_prefix(data):
    return len(data).to_bytes(8, "little") + data


def _password_values(password):
    digest = hashlib.sha512(password).digest()
    value = int.from_bytes(digest, "little") % _ORDER
    if value & 1:
        value += _ORDER
    if value & 2:
        value += 2 * _ORDER
    if value & 4:
        value += 4 * _ORDER
    return digest, value


def _negate(point):
    x, y = _ed.xform_extended_to_affine(point.XYTZ)
    return _ed.ElementOfUnknownGroup(
        _ed.xform_affine_to_extended(((-x) % _ed.Q, y))
    )


class _Spake2Compat(object):
    _IS_ALICE = False

    def __init__(self, my_name, their_name):
        self._my_name = my_name + b"\0"
        self._their_name = their_name + b"\0"
        self._my_msg = None
        self._private_scalar = None
        self._password_hash = None
        self._password_scalar = None

    def generate_msg(self, password):
        if self._my_msg is not None:
            raise ValueError("SPAKE2 message already generated")

        reduced = int.from_bytes(os.urandom(64), "little") % _ORDER
        self._private_scalar = (reduced << 3) & _MAX_SCALAR
        self._password_hash, self._password_scalar = _password_values(password)

        my_point = _ed.Base.scalarmult(self._private_scalar)
        mask_point = _M if self._IS_ALICE else _N
        mask = mask_point.scalarmult(self._password_scalar)
        self._my_msg = my_point.add(mask).to_bytes()
        return self._my_msg

    def process_msg(self, their_msg):
        if self._my_msg is None or len(their_msg) != 32:
            raise ValueError("invalid SPAKE2 message")

        peer_affine = _ed.decodepoint(their_msg)
        peer_point = _ed.ElementOfUnknownGroup(
            _ed.xform_affine_to_extended(peer_affine)
        )
        peer_mask_point = _N if self._IS_ALICE else _M
        peer_mask = peer_mask_point.scalarmult(self._password_scalar)
        unmasked = peer_point.add(_negate(peer_mask))
        shared = unmasked.scalarmult(self._private_scalar).to_bytes()

        transcript = hashlib.sha512()
        if self._IS_ALICE:
            transcript.update(_length_prefix(self._my_name))
            transcript.update(_length_prefix(self._their_name))
            transcript.update(_length_prefix(self._my_msg))
            transcript.update(_length_prefix(their_msg))
        else:
            transcript.update(_length_prefix(self._their_name))
            transcript.update(_length_prefix(self._my_name))
            transcript.update(_length_prefix(their_msg))
            transcript.update(_length_prefix(self._my_msg))
        transcript.update(_length_prefix(shared))
        transcript.update(_length_prefix(self._password_hash))
        return transcript.digest()


class Spake2_Alice(_Spake2Compat):
    _IS_ALICE = True


class Spake2_Bob(_Spake2Compat):
    _IS_ALICE = False


def install():
    import spake2.spake2 as module

    module.Spake2_Alice = Spake2_Alice
    module.Spake2_Bob = Spake2_Bob
