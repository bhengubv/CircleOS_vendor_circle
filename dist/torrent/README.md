# Circle OS — signed BitTorrent distribution (#23 / SOS-B6)

Uncensorable distribution of Circle OS images. A central download host can be
blocked; a swarm can't. `make-torrent.sh` packages a release image as a
`.torrent` (+ magnet link) whose infohash pins the exact bytes, and pairs it with
the detached release signature from [`../sign-release.sh`](../sign-release.sh) so
every downloader can prove the image is the authentic, untampered Circle OS build
regardless of which peer or mirror delivered it.

## Flow

```sh
# 1. Sign the release manifest (authenticity):
vendor/circle/dist/sign-release.sh -k release_key.pem circleos-0.1.0.img
#    -> SHA256SUMS, SHA256SUMS.sig

# 2. Make the signed torrent (uncensorable delivery):
vendor/circle/dist/torrent/make-torrent.sh \
    -i circleos-0.1.0.img \
    -u https://cdn.thegeek.co.za/circleos/0.1.0/circleos-0.1.0.img \  # web seed
    -s SHA256SUMS.sig -k circleos-release.pub
#    -> circleos-0.1.0.img.torrent, magnet link, verify-*.txt

# 3. Publish the .torrent + magnet + SHA256SUMS + SHA256SUMS.sig + circleos-release.pub.
#    Seed from a seedbox; the CDN URL rides along as a BitTorrent web seed so the
#    swarm bootstraps even with zero peers.
```

## Why both a torrent and a signature

- **Torrent infohash** → you got the *exact bytes* the publisher meant (integrity).
- **Release signature over SHA256SUMS** → those bytes are the *real Circle OS*, not
  a look-alike a malicious seeder injected (authenticity).

Either alone is insufficient; together they make distribution both uncensorable
and trustworthy. Requires `mktorrent` or `transmission-create` on the build host.
