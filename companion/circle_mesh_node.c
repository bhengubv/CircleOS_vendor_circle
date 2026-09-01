/*
 * SPDX-License-Identifier: Apache-2.0
 * Circle OS - companion mesh node (the sub-Android tier of one-codebase/every-device).
 *
 * The tiniest devices (ESP32 / nRF52 sensors) cannot run AOSP. This "companion" is a
 * tiny C program that carries the device's Circle identity and joins the Aether mesh,
 * so even a cheap chip is part of your Circle. It links against aether-protocol's C
 * port -- the sanction-proof, embedded C implementation of the same protocol the
 * phones speak (kept byte-identical by the shared fixtures).
 *
 * Build: needs libsodium + libaether-protocol (aether-protocol/c). POSIX for dev;
 * ESP-IDF / nRF Connect SDK for the real chips. NOT part of the AOSP build.
 */
#include <stdio.h>
#include <unistd.h>
#include <sodium.h>

/*
 * Aether mesh C API -- the integration seam, provided by libaether-protocol
 * (aether-protocol/c/include/aethernet/*.h) once that port is synced into this
 * tree. Declared here so the companion's own logic is complete and reviewable;
 * exact signatures are adopted from the header when the lib lands.
 */
extern int aethernet_node_init(const unsigned char *sk, const unsigned char *pk);
extern int aethernet_mesh_join(void);
extern int aethernet_presence_beacon(void);

#define HEARTBEAT_SECONDS 30

int main(void) {
    if (sodium_init() < 0) {
        fprintf(stderr, "circle-mesh-node: libsodium init failed\n");
        return 1;
    }

    /* Device identity: an Ed25519 keypair IS this node's AetherTag on the chip.
       One device, one identity -- the same principle as the phones. */
    unsigned char pk[crypto_sign_PUBLICKEYBYTES];
    unsigned char sk[crypto_sign_SECRETKEYBYTES];
    crypto_sign_keypair(pk, sk);

    if (aethernet_node_init(sk, pk) != 0) {
        fprintf(stderr, "circle-mesh-node: node init failed\n");
        return 1;
    }
    if (aethernet_mesh_join() != 0) {
        fprintf(stderr, "circle-mesh-node: mesh join failed\n");
        return 1;
    }

    printf("circle-mesh-node: up, identity carried, mesh joined\n");
    for (;;) {
        aethernet_presence_beacon();
        sleep(HEARTBEAT_SECONDS);
    }
    return 0;
}
