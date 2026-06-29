package android.circleos;

public class CircleEndpointsTest {
    static int passed = 0, failed = 0;
    static void ck(boolean c, String label) {
        if (c) passed++; else { failed++; System.out.println("FAIL: " + label); }
    }

    public static void main(String[] args) {
        String OV  = "{\"central_api\":\"https://override.circleos.co.za\"}";
        String DEF = "{\"central_api\":\"https://media.circleos.co.za\",\"fallbacks\":[\"https://api2.circleos.co.za\"]}";

        // ---- precedence ----
        ck("https://override.circleos.co.za".equals(CircleEndpoints.resolve(OV, DEF).central),
                "override wins over default");
        ck("https://media.circleos.co.za".equals(CircleEndpoints.resolve(null, DEF).central),
                "null override -> default");
        ck(CircleEndpoints.FALLBACK_CENTRAL.equals(CircleEndpoints.resolve(null, null).central),
                "no files -> compiled-in fallback");
        ck("https://media.circleos.co.za".equals(CircleEndpoints.resolve("{ broken json", DEF).central),
                "malformed override falls through to default");
        ck("https://media.circleos.co.za".equals(CircleEndpoints.resolve("{}", DEF).central),
                "override with no central_api falls through to default");

        // ---- fallbacks parsed ----
        java.util.List<String> fb = CircleEndpoints.resolve(null, DEF).fallbacks;
        ck(fb.size() == 1 && "https://api2.circleos.co.za".equals(fb.get(0)), "fallbacks parsed");
        ck(CircleEndpoints.resolve(OV, OV).fallbacks.isEmpty(), "absent fallbacks -> empty list");

        // ---- trailing slash stripped ----
        ck("https://x.circleos.co.za".equals(
                CircleEndpoints.resolve("{\"central_api\":\"https://x.circleos.co.za/\"}", null).central),
                "trailing slash stripped");

        // ---- parseOne edge cases ----
        ck(CircleEndpoints.parseOne(null) == null, "parseOne(null) -> null");
        ck(CircleEndpoints.parseOne("   ") == null, "parseOne(blank) -> null");
        ck(CircleEndpoints.parseOne("not json") == null, "parseOne(garbage) -> null");
        ck(CircleEndpoints.parseOne(DEF) != null, "parseOne(valid) -> non-null");

        // ---- whitespace-only central treated as empty ----
        ck(CircleEndpoints.FALLBACK_CENTRAL.equals(
                CircleEndpoints.resolve("{\"central_api\":\"   \"}", null).central),
                "whitespace central_api -> fallback");

        // ---- result never null, fallbacks never null ----
        CircleEndpoints.Resolved r = CircleEndpoints.resolve(null, null);
        ck(r.central != null && r.fallbacks != null, "resolve never returns nulls");

        System.out.println("passed=" + passed + " failed=" + failed);
        if (failed > 0) System.exit(1);
        System.out.println("ALL GREEN");
    }
}
