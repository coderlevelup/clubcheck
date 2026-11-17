"""Writes core/src/test/resources/vectors.json: payload and Reed-Solomon vectors from the spike
reference implementation, so the Kotlin codec can be checked against it.
Run: python vectors.py (needs reedsolo, opencv, numpy, pillow; imports spike.py from this folder)."""
import json, os, struct, importlib.util, sys
here = os.path.dirname(os.path.abspath(__file__))
spec = importlib.util.spec_from_file_location('spike', os.path.join(here, 'spike.py'))
sp = importlib.util.module_from_spec(spec); spec.loader.exec_module(sp)

def build(name, event, ts, tz15, lat_h, lon_h, hand, cam, point, photos, inst, url):
    u = url.encode()
    body = struct.pack('>B6sIbhhHHHHHB4sB', 1, bytes.fromhex(event), ts, tz15, lat_h, lon_h,
                       hand[0], hand[1], cam[0], cam[1], point, photos, bytes.fromhex(inst), len(u)) + u
    payload = body + struct.pack('>H', sp.crc16(body))
    coded = bytes(sp.rsc.encode(payload))
    return dict(name=name, eventId=event, utcSeconds=ts, tz15=tz15, latHundredths=lat_h, lonHundredths=lon_h,
                handLo=hand[0], handHi=hand[1], camLo=cam[0], camHi=cam[1], camPoint=point, photos=photos,
                installId=inst, url=url, payloadHex=payload.hex(), codedHex=coded.hex())

NONE = -32768
vectors = [
    build('full', 'e705d9a5bc34', 1794905621, 8, -3390, 1840, (55, 65), (56, 74), 60, 1, 'a1b2c3d4',
          'rsvp.withgoogle.com/events/android-developer-day-capetown'),
    build('no-location', '0102030405ff', 1700000000, -20, NONE, NONE, (3, 3), (0, 0), 0, 2, '00000001', 'club.example/x'),
    build('max-url', 'ffffffffffff', 4294967295, 22, 5100, -12200, (0, 65535), (1, 2), 1, 9, 'deadbeef', 'a' * 65),
]
out = os.path.join(here, '..', '..', 'core', 'src', 'test', 'resources', 'vectors.json')
os.makedirs(os.path.dirname(out), exist_ok=True)
json.dump(vectors, open(out, 'w'), indent=1)
print('wrote', len(vectors), 'vectors to', os.path.normpath(out))
