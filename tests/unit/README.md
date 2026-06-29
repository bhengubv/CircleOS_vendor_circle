# Circle OS unit tests (standalone javac/java)

Pure-logic unit tests for the extracted, Android-free cores. They are **not**
wired into any Soong/build module — run them by hand with `javac`/`java`, the
same model used for the mesh crypto tests. "Green" = these pass.

## wallet/
- `WalletMathTest` — money arithmetic: per-tap/daily/funds limits, send/receive/
  settle transitions, daily rollover, no-negative floor. **21/21**.
- `WalletFrameTest` — NFC `[type][json]` frame codec + signed-offer
  canonical-bytes. **16/16**.

Production: `frameworks/base/services/core/java/com/circleos/server/wallet/{WalletMath,WalletFrame}.java`
(ShongololoWalletService delegates to them).

```sh
mkdir -p /tmp/wt/com/circleos/server/wallet && cd /tmp/wt
cp <fb>/.../wallet/WalletMath.java <fb>/.../wallet/WalletFrame.java com/circleos/server/wallet/
cp <here>/wallet/*.java com/circleos/server/wallet/
javac com/circleos/server/wallet/*.java
java com.circleos.server.wallet.WalletMathTest && java com.circleos.server.wallet.WalletFrameTest
```

## endpoints/
- `CircleEndpointsTest` — config resolve/parse precedence (override→default→
  compiled-in fallback), malformed-safe, trailing-slash, fallbacks. **14/14**.

Production: `vendor/circle/aidl/android/circleos/CircleEndpoints.java`. Needs
`org.json` on the classpath (compile `libcore/json` source with its Android
annotation imports stripped, or use any json jar).
