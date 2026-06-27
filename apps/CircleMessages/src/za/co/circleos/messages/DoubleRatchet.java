/*
 * Copyright (C) 2026 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 *
 * Double Ratchet (Signal-style) over X25519 / HKDF-SHA256 / ChaCha20-Poly1305,
 * giving the Circle mesh forward secrecy and break-in recovery: every message
 * is sealed under a unique message key that is deleted after use, and a fresh
 * DH ratchet step is folded in on every round-trip. A key compromised today
 * cannot read yesterday's traffic, and the channel self-heals afterwards.
 *
 * Pure JCA only (java.security / javax.crypto / java.util.Base64) — no Android
 * imports — so it is unit-testable off-device and runs unchanged on AOSP. State
 * is a plain {@link State} POJO that packs to a byte[] for the caller to persist.
 *
 * Wire envelope ("CE2:" + Base64(header || ciphertext)):
 *   header = [version=2][dhPubLen u16][dhPub X509][PN i32][N i32]
 *   header is the AEAD associated data; per-message (encKey,nonce) are derived
 *   from the message key, so the nonce is not transmitted.
 */
package za.co.circleos.messages;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.NamedParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class DoubleRatchet {

    public static final String PREFIX = "CE2:";

    private static final byte VERSION = 2;
    private static final int MAX_SKIP = 256;   // bound out-of-order key derivation per chain
    private static final int MAX_SKIPPED_STORE = 2000;

    private static final byte[] INFO_RK  = "CircleRatchet-RK".getBytes(StandardCharsets.UTF_8);
    private static final byte[] INFO_MSG = "CircleRatchet-Msg".getBytes(StandardCharsets.UTF_8);
    private static final byte[] INFO_INIT = "CircleRatchet-Init".getBytes(StandardCharsets.UTF_8);

    private DoubleRatchet() {}

    // ── Session state (caller persists pack()) ──

    public static final class State {
        PrivateKey dhsPriv;     // our current ratchet private key
        PublicKey  dhsPub;      // our current ratchet public key
        PublicKey  dhr;         // peer's current ratchet public key (nullable)
        byte[] rk;              // root key (32)
        byte[] cks;             // sending chain key (nullable)
        byte[] ckr;             // receiving chain key (nullable)
        int ns, nr, pn;
        // skipped message keys: "base64(dhPubX509)|N" -> messageKey(32)
        final LinkedHashMap<String, byte[]> skipped = new LinkedHashMap<>();
    }

    /**
     * Bootstrap a session from a shared secret both sides already hold (here the
     * identity-key ECDH). Roles are assigned deterministically by comparing the two
     * identity public keys, so no extra handshake message is needed: the party with
     * the lexicographically larger identity key is the initiator.
     *
     * @param sharedSecret  ECDH(identity) output, same on both peers
     * @param selfIdPub     our identity public key (X25519)
     * @param selfIdPair    our identity key pair (used as the responder's first ratchet key)
     * @param peerIdPub     peer's identity public key (the initiator ratchets against this)
     */
    public static State init(byte[] sharedSecret, PublicKey selfIdPub, KeyPair selfIdPair,
                             PublicKey peerIdPub) throws Exception {
        byte[] sk = hkdf(new byte[32], sharedSecret, INFO_INIT, 32);
        boolean initiator = lexCompare(selfIdPub.getEncoded(), peerIdPub.getEncoded()) > 0;
        State s = new State();
        if (initiator) {
            KeyPair dhs = generateDh();
            s.dhsPriv = dhs.getPrivate();
            s.dhsPub  = dhs.getPublic();
            s.dhr     = peerIdPub;                       // responder's first ratchet key == its identity
            byte[] kk = kdfRk(sk, dh(s.dhsPriv, s.dhr)); // -> rk' || cks
            s.rk  = Arrays.copyOfRange(kk, 0, 32);
            s.cks = Arrays.copyOfRange(kk, 32, 64);
            s.ckr = null;
        } else {
            s.dhsPriv = selfIdPair.getPrivate();          // responder starts on its identity key
            s.dhsPub  = selfIdPair.getPublic();
            s.dhr     = null;
            s.rk      = sk;
            s.cks     = null;
            s.ckr     = null;
        }
        s.ns = s.nr = s.pn = 0;
        return s;
    }

    /** True if this is a "CE2:" ratcheted envelope. */
    public static boolean isRatchet(String wire) {
        return wire != null && wire.startsWith(PREFIX);
    }

    /** True once this session has a sending chain (initiator immediately; responder after first receive). */
    public static boolean canSend(State s) {
        return s != null && s.cks != null;
    }

    /** Encrypt, advancing the sending chain. Returns "CE2:..." or throws on hard failure. */
    public static String encrypt(State s, String plaintext) throws Exception {
        if (s.cks == null) throw new IllegalStateException("no sending chain yet");
        byte[][] ck_mk = kdfCk(s.cks);
        s.cks = ck_mk[0];
        byte[] mk = ck_mk[1];
        byte[] header = header(s.dhsPub, s.pn, s.ns);
        s.ns += 1;
        byte[] ct = aeadSeal(mk, plaintext.getBytes(StandardCharsets.UTF_8), header);
        byte[] out = concat(header, ct);
        return PREFIX + Base64.getEncoder().encodeToString(out);
    }

    /** Decrypt a "CE2:" envelope, performing DH/symmetric ratchet steps as needed. */
    public static String decrypt(State s, String wire) throws Exception {
        byte[] raw = Base64.getDecoder().decode(wire.substring(PREFIX.length()));
        Parsed p = parse(raw);

        // 1) Out-of-order message we already skipped past.
        String skippedKey = skKey(p.dhPubX509, p.n);
        byte[] sk = s.skipped.get(skippedKey);
        if (sk != null) {
            String pt = tryOpen(sk, p);
            if (pt != null) { s.skipped.remove(skippedKey); return pt; }
        }

        // 2) New peer ratchet key -> finish old chain, DH-ratchet.
        PublicKey headerDh = pubFromX509(p.dhPubX509);
        if (s.dhr == null || !Arrays.equals(s.dhr.getEncoded(), p.dhPubX509)) {
            skipMessageKeys(s, p.pn);
            dhRatchet(s, headerDh);
        }
        // 3) Skip within current receiving chain up to N.
        skipMessageKeys(s, p.n);
        // 4) Derive this message's key.
        byte[][] ck_mk = kdfCk(s.ckr);
        s.ckr = ck_mk[0];
        byte[] mk = ck_mk[1];
        s.nr += 1;
        byte[] pt = aeadOpen(mk, p.ciphertext, p.header);
        if (pt == null) throw new SecurityException("ratchet decrypt failed");
        return new String(pt, StandardCharsets.UTF_8);
    }

    // ── ratchet internals ──

    private static void dhRatchet(State s, PublicKey headerDh) throws Exception {
        s.pn = s.ns;
        s.ns = 0;
        s.nr = 0;
        s.dhr = headerDh;
        byte[] kk1 = kdfRk(s.rk, dh(s.dhsPriv, s.dhr));
        s.rk  = Arrays.copyOfRange(kk1, 0, 32);
        s.ckr = Arrays.copyOfRange(kk1, 32, 64);
        KeyPair dhs = generateDh();
        s.dhsPriv = dhs.getPrivate();
        s.dhsPub  = dhs.getPublic();
        byte[] kk2 = kdfRk(s.rk, dh(s.dhsPriv, s.dhr));
        s.rk  = Arrays.copyOfRange(kk2, 0, 32);
        s.cks = Arrays.copyOfRange(kk2, 32, 64);
    }

    private static void skipMessageKeys(State s, int until) throws Exception {
        if (s.ckr == null) return;
        if (until - s.nr > MAX_SKIP) throw new SecurityException("too many skipped messages");
        while (s.nr < until) {
            byte[][] ck_mk = kdfCk(s.ckr);
            s.ckr = ck_mk[0];
            s.skipped.put(skKey(s.dhr.getEncoded(), s.nr), ck_mk[1]);
            s.nr += 1;
            if (s.skipped.size() > MAX_SKIPPED_STORE) {
                // drop oldest to bound memory
                java.util.Iterator<String> it = s.skipped.keySet().iterator();
                if (it.hasNext()) { it.next(); it.remove(); }
            }
        }
    }

    private static String tryOpen(byte[] mk, Parsed p) {
        byte[] pt = aeadOpen(mk, p.ciphertext, p.header);
        return pt == null ? null : new String(pt, StandardCharsets.UTF_8);
    }

    // ── KDFs ──

    /** KDF_RK: HKDF(salt=rk, ikm=dhOut) -> 64 bytes (newRk || newChainKey). */
    private static byte[] kdfRk(byte[] rk, byte[] dhOut) throws Exception {
        return hkdf(rk, dhOut, INFO_RK, 64);
    }

    /** KDF_CK: mk = HMAC(ck,0x01); ck' = HMAC(ck,0x02). Returns {ck', mk}. */
    private static byte[][] kdfCk(byte[] ck) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(ck, "HmacSHA256"));
        byte[] mk = mac.doFinal(new byte[]{0x01});
        mac.reset();
        mac.init(new SecretKeySpec(ck, "HmacSHA256"));
        byte[] nck = mac.doFinal(new byte[]{0x02});
        return new byte[][]{nck, mk};
    }

    /** Derive (encKey32 || nonce12) from a message key. */
    private static byte[] msgKeys(byte[] mk) throws Exception {
        return hkdf(new byte[32], mk, INFO_MSG, 44);
    }

    private static byte[] aeadSeal(byte[] mk, byte[] pt, byte[] ad) throws Exception {
        byte[] km = msgKeys(mk);
        byte[] encKey = Arrays.copyOfRange(km, 0, 32);
        byte[] nonce  = Arrays.copyOfRange(km, 32, 44);
        Cipher c = Cipher.getInstance("ChaCha20-Poly1305");
        c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(encKey, "ChaCha20"), new IvParameterSpec(nonce));
        c.updateAAD(ad);
        return c.doFinal(pt);
    }

    private static byte[] aeadOpen(byte[] mk, byte[] ct, byte[] ad) {
        try {
            byte[] km = msgKeys(mk);
            byte[] encKey = Arrays.copyOfRange(km, 0, 32);
            byte[] nonce  = Arrays.copyOfRange(km, 32, 44);
            Cipher c = Cipher.getInstance("ChaCha20-Poly1305");
            c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(encKey, "ChaCha20"), new IvParameterSpec(nonce));
            c.updateAAD(ad);
            return c.doFinal(ct);
        } catch (Throwable t) {
            return null;
        }
    }

    private static byte[] hkdf(byte[] salt, byte[] ikm, byte[] info, int len) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        if (salt == null || salt.length == 0) salt = new byte[mac.getMacLength()];
        mac.init(new SecretKeySpec(salt, "HmacSHA256"));
        byte[] prk = mac.doFinal(ikm);
        mac.init(new SecretKeySpec(prk, "HmacSHA256"));
        byte[] out = new byte[len];
        byte[] t = new byte[0];
        int pos = 0, counter = 1;
        while (pos < len) {
            mac.reset();
            mac.update(t);
            mac.update(info);
            mac.update((byte) counter);
            t = mac.doFinal();
            int n = Math.min(t.length, len - pos);
            System.arraycopy(t, 0, out, pos, n);
            pos += n;
            counter++;
        }
        return out;
    }

    // ── DH ──

    private static KeyPair generateDh() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("XDH");
        kpg.initialize(NamedParameterSpec.X25519);
        return kpg.generateKeyPair();
    }

    private static byte[] dh(PrivateKey priv, PublicKey pub) throws Exception {
        KeyAgreement ka = KeyAgreement.getInstance("XDH");
        ka.init(priv);
        ka.doPhase(pub, true);
        return ka.generateSecret();
    }

    private static PublicKey pubFromX509(byte[] x509) throws Exception {
        return KeyFactory.getInstance("XDH").generatePublic(new X509EncodedKeySpec(x509));
    }

    // ── header / wire framing ──

    private static byte[] header(PublicKey dhPub, int pn, int n) {
        byte[] pub = dhPub.getEncoded();
        ByteBuffer b = ByteBuffer.allocate(1 + 2 + pub.length + 4 + 4);
        b.put(VERSION);
        b.putShort((short) pub.length);
        b.put(pub);
        b.putInt(pn);
        b.putInt(n);
        return b.array();
    }

    private static final class Parsed {
        byte[] header;       // AD = the header bytes
        byte[] dhPubX509;
        int pn, n;
        byte[] ciphertext;
    }

    private static Parsed parse(byte[] raw) {
        ByteBuffer b = ByteBuffer.wrap(raw);
        byte ver = b.get();
        if (ver != VERSION) throw new IllegalArgumentException("bad version");
        int pubLen = b.getShort() & 0xFFFF;
        byte[] pub = new byte[pubLen];
        b.get(pub);
        int pn = b.getInt();
        int n = b.getInt();
        int headerLen = 1 + 2 + pubLen + 4 + 4;
        byte[] ct = new byte[b.remaining()];
        b.get(ct);
        Parsed p = new Parsed();
        p.header = Arrays.copyOfRange(raw, 0, headerLen);
        p.dhPubX509 = pub;
        p.pn = pn;
        p.n = n;
        p.ciphertext = ct;
        return p;
    }

    // ── helpers ──

    private static String skKey(byte[] dhPubX509, int n) {
        return Base64.getEncoder().encodeToString(dhPubX509) + "|" + n;
    }

    private static int lexCompare(byte[] a, byte[] b) {
        int m = Math.min(a.length, b.length);
        for (int i = 0; i < m; i++) {
            int d = (a[i] & 0xFF) - (b[i] & 0xFF);
            if (d != 0) return d;
        }
        return a.length - b.length;
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    // ── state serialization (caller persists these bytes) ──

    public static byte[] pack(State s) throws Exception {
        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
        java.io.DataOutputStream o = new java.io.DataOutputStream(bo);
        o.writeByte(VERSION);
        writeBytes(o, s.dhsPriv.getEncoded());
        writeBytes(o, s.dhsPub.getEncoded());
        writeBytes(o, s.dhr == null ? null : s.dhr.getEncoded());
        writeBytes(o, s.rk);
        writeBytes(o, s.cks);
        writeBytes(o, s.ckr);
        o.writeInt(s.ns);
        o.writeInt(s.nr);
        o.writeInt(s.pn);
        o.writeInt(s.skipped.size());
        for (Map.Entry<String, byte[]> e : s.skipped.entrySet()) {
            o.writeUTF(e.getKey());
            writeBytes(o, e.getValue());
        }
        o.flush();
        return bo.toByteArray();
    }

    public static State unpack(byte[] data) throws Exception {
        java.io.DataInputStream in = new java.io.DataInputStream(new java.io.ByteArrayInputStream(data));
        byte ver = in.readByte();
        if (ver != VERSION) throw new IllegalArgumentException("bad state version");
        KeyFactory kf = KeyFactory.getInstance("XDH");
        State s = new State();
        s.dhsPriv = kf.generatePrivate(new PKCS8EncodedKeySpec(readBytes(in)));
        s.dhsPub  = kf.generatePublic(new X509EncodedKeySpec(readBytes(in)));
        byte[] dhr = readBytes(in);
        s.dhr = dhr == null ? null : kf.generatePublic(new X509EncodedKeySpec(dhr));
        s.rk  = readBytes(in);
        s.cks = readBytes(in);
        s.ckr = readBytes(in);
        s.ns = in.readInt();
        s.nr = in.readInt();
        s.pn = in.readInt();
        int n = in.readInt();
        for (int i = 0; i < n; i++) {
            String k = in.readUTF();
            s.skipped.put(k, readBytes(in));
        }
        return s;
    }

    private static void writeBytes(java.io.DataOutputStream o, byte[] b) throws Exception {
        if (b == null) { o.writeInt(-1); return; }
        o.writeInt(b.length);
        o.write(b);
    }

    private static byte[] readBytes(java.io.DataInputStream in) throws Exception {
        int len = in.readInt();
        if (len < 0) return null;
        byte[] b = new byte[len];
        in.readFully(b);
        return b;
    }
}
