#!/usr/bin/env python3
"""
Circle OS - release torrent builder (#23 / SOS-B6 uncensorable distribution).

Creates a BitTorrent .torrent (+ magnet link) for a Circle OS release image,
with public trackers and HTTP/HTTPS web seeds (BEP-19) so the release is
distributable peer-to-peer AND falls back to mirrors. Pure standard library -
no third-party deps, runs anywhere Python 3 does.

Usage:
    python3 make_torrent.py IMAGE [-o OUT.torrent] [-w WEBSEED_URL ...]
        [-t TRACKER_URL ...] [-c "comment"] [-p PIECE_KIB]

Example:
    python3 make_torrent.py circleos-0.1.0-alpha.img.gz \
        -w https://dl.circleos.co.za/circleos-0.1.0-alpha.img.gz \
        -t udp://tracker.opentrackr.org:1337/announce
"""
import argparse, hashlib, os, sys, time
from urllib.parse import quote


def bencode(v):
    if isinstance(v, bool):
        raise TypeError("bool is not bencodable")
    if isinstance(v, int):
        return b"i" + str(v).encode() + b"e"
    if isinstance(v, bytes):
        return str(len(v)).encode() + b":" + v
    if isinstance(v, str):
        return bencode(v.encode("utf-8"))
    if isinstance(v, list):
        return b"l" + b"".join(bencode(x) for x in v) + b"e"
    if isinstance(v, dict):
        out = b"d"
        for k in sorted(v.keys()):
            kk = k.encode("utf-8") if isinstance(k, str) else k
            out += bencode(kk) + bencode(v[k])
        return out + b"e"
    raise TypeError("unbencodable: %r" % type(v))


DEFAULT_TRACKERS = [
    "udp://tracker.opentrackr.org:1337/announce",
    "udp://tracker.openbittorrent.com:6969/announce",
    "udp://open.demonii.com:1337/announce",
    "udp://exodus.desync.com:6969/announce",
]


def build(path, piece_len, trackers, webseeds, comment):
    size = os.path.getsize(path)
    name = os.path.basename(path)
    pieces = bytearray()
    with open(path, "rb") as f:
        while True:
            chunk = f.read(piece_len)
            if not chunk:
                break
            pieces += hashlib.sha1(chunk).digest()
    info = {
        "name": name,
        "length": size,
        "piece length": piece_len,
        "pieces": bytes(pieces),
    }
    info_hash = hashlib.sha1(bencode(info)).hexdigest()
    meta = {
        "announce": trackers[0],
        "announce-list": [[t] for t in trackers],
        "creation date": int(time.time()),
        "created by": "circleos make_torrent.py",
        "info": info,
    }
    if comment:
        meta["comment"] = comment
    if webseeds:
        meta["url-list"] = webseeds  # BEP-19 web seeding
    return meta, info_hash, name, size


def magnet(info_hash, name, trackers, webseeds):
    m = "magnet:?xt=urn:btih:" + info_hash + "&dn=" + quote(name)
    for t in trackers:
        m += "&tr=" + quote(t)
    for w in webseeds:
        m += "&ws=" + quote(w)
    return m


def main():
    ap = argparse.ArgumentParser(description="Build a Circle OS release torrent.")
    ap.add_argument("image")
    ap.add_argument("-o", "--out")
    ap.add_argument("-w", "--webseed", action="append", default=[])
    ap.add_argument("-t", "--tracker", action="append", default=[])
    ap.add_argument("-c", "--comment",
                    default="Circle OS release - verify the signature before flashing.")
    ap.add_argument("-p", "--piece-kib", type=int, default=1024)
    a = ap.parse_args()
    if not os.path.isfile(a.image):
        sys.exit("not a file: " + a.image)
    trackers = a.tracker or DEFAULT_TRACKERS
    out = a.out or (a.image + ".torrent")
    meta, ih, name, size = build(a.image, a.piece_kib * 1024, trackers, a.webseed, a.comment)
    with open(out, "wb") as f:
        f.write(bencode(meta))
    mag = magnet(ih, name, trackers, a.webseed)
    print("torrent : " + out)
    print("name    : %s (%d bytes, %.1f MiB)" % (name, size, size / 1048576.0))
    print("pieces  : %d x %d KiB" % (-(-size // (a.piece_kib * 1024)), a.piece_kib))
    print("infohash: " + ih)
    print("magnet  : " + mag)
    with open(out + ".magnet.txt", "w") as f:
        f.write(mag + "\n")
    print("wrote   : " + out + ".magnet.txt")


if __name__ == "__main__":
    main()
