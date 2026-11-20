import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** JDK 11+ standard library example. No arguments: offline tests only. */
public class OpenApiSigningExample {
    private static final String TEST_SECRET = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=";
    private static final String TS = "1794787200";
    private static final String NONCE = "550e8400-e29b-41d4-a716-446655440000";
    private static final String RID = "7f7c8f45-0c8c-4b56-9b77-4c4bd7de9a41";

    public static String sha256(byte[] body) throws Exception {
        StringBuilder hex = new StringBuilder();
        for (byte b : MessageDigest.getInstance("SHA-256").digest(body))
            hex.append(String.format("%02x", b & 255));
        return hex.toString();
    }

    /** Input is decoded parameter names and values; never pass already encoded %xx. */
    public static String canonicalQuery(Map<String, String> values) {
        Map<String, String> encoded = new TreeMap<>();
        values.forEach((name, value) -> encoded.put(encode(name), encode(value)));
        StringBuilder out = new StringBuilder();
        encoded.forEach((name, value) -> {
            if (out.length() > 0) out.append('&');
            out.append(name).append('=').append(value);
        });
        return out.toString();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8)
            .replace("+", "%20").replace("*", "%2A").replace("%7E", "~");
    }

    public static Map<String, String> headers(String method, String path, String query,
            byte[] body, String client, String key, String secret, String timestamp,
            String nonce, String requestId, String idempotencyKey) throws Exception {
        byte[] keyBytes = Base64.getDecoder().decode(secret);
        if (keyBytes.length != 32 || !Base64.getEncoder().encodeToString(keyBytes).equals(secret))
            throw new IllegalArgumentException("Secret must be canonical Base64 for 32 bytes");
        String hash = sha256(body);
        // Exactly 10 lines, LF separators, no trailing LF. Preserve empty lines 3 and 9.
        String canonical = String.join("\n", method, path, query, client, key,
            timestamp, nonce, requestId, idempotencyKey, hash);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(keyBytes, "HmacSHA256"));
        String signature = Base64.getEncoder().encodeToString(
            mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        Map<String, String> result = new LinkedHashMap<>();
        result.put("X-Client-Id", client); result.put("X-Key-Id", key);
        result.put("X-Timestamp", timestamp); result.put("X-Nonce", nonce);
        result.put("X-Request-Id", requestId); result.put("X-Content-SHA256", hash);
        result.put("X-Signature", signature); result.put("Accept", "application/json");
        if (method.equals("POST")) result.put("Content-Type", "application/json; charset=utf-8");
        if (!idempotencyKey.isEmpty()) result.put("Idempotency-Key", idempotencyKey);
        return result;
    }

    private static void check(String expected, String actual) {
        if (!expected.equals(actual)) throw new AssertionError("Offline signature vector mismatch");
    }

    public static void selfTest() throws Exception {
        byte[] body = "{\"loginAccount\":\"zhangsan\"}".getBytes(StandardCharsets.UTF_8);
        Map<String, String> post = headers("POST", "/open-api/v1/users/verify", "", body,
            "intelligent-office-test", "test-key-01", TEST_SECRET, TS, NONCE, RID, "");
        check("aa6b77fa18f04ad64611d521f86e77117d861ad907d8ff85560a19c426ad3a65", post.get("X-Content-SHA256"));
        check("lbIOMwqPoysaiu2XwGpXrZgXN0pDRAC0pUd31Rn176g=", post.get("X-Signature"));
        Map<String, String> params = new LinkedHashMap<>();
        params.put("pageSize", "100"); params.put("keyword", "华北 销售");
        params.put("deptId", "80001"); params.put("pageNum", "1");
        String query = canonicalQuery(params);
        check("deptId=80001&keyword=%E5%8D%8E%E5%8C%97%20%E9%94%80%E5%94%AE&pageNum=1&pageSize=100", query);
        Map<String, String> get = headers("GET", "/open-api/v1/dictionaries/sale-channels", query,
            new byte[0], "intelligent-office-test", "test-key-01", TEST_SECRET, TS, NONCE, RID, "");
        check("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", get.get("X-Content-SHA256"));
        check("lFOnAJJ/m7QW2ox4ZeyjsEX7LU7elmfxDJkcDJz6hfk=", get.get("X-Signature"));
        check("punctuation=%21%27%28%29%2A~&space=%20", canonicalQuery(Map.of("punctuation", "!'()*~", "space", " ")));
        System.out.println("POST and GET signature vectors passed (offline; no requests sent)");
    }

    private static String env(String name) {
        String value = System.getenv(name);
        if (value == null || value.isEmpty()) throw new IllegalArgumentException("Missing environment variable: " + name);
        return value;
    }

    /** Only read-only business operations, and only after an explicit command argument. */
    public static void main(String[] args) throws Exception {
        selfTest();
        if (args.length == 0) return;
        if (args.length != 1 || !(args[0].equals("send-user") || args[0].equals("send-dictionary")))
            throw new IllegalArgumentException("Use no arguments, send-user, or send-dictionary");
        String origin = env("OPEN_API_ORIGIN"); // Scheme + host + optional port, no /api or /prod-api.
        URI base = URI.create(origin);
        if (!("http".equals(base.getScheme()) || "https".equals(base.getScheme()))
                || base.getHost() == null || base.getUserInfo() != null
                || base.getRawQuery() != null || base.getRawFragment() != null
                || !(base.getRawPath().isEmpty() || base.getRawPath().equals("/")))
            throw new IllegalArgumentException("OPEN_API_ORIGIN must contain only scheme, host and optional port");
        boolean user = args[0].equals("send-user");
        String method = user ? "POST" : "GET";
        String path = user ? "/open-api/v1/users/verify" : "/open-api/v1/dictionaries/departments";
        // This example restricts account characters to avoid hand-built JSON escaping.
        // Production code: use your JSON library once and keep the resulting byte[] unchanged.
        String account = user ? env("OPEN_API_LOGIN_ACCOUNT") : "";
        if (user && !account.matches("[A-Za-z0-9._@-]{1,30}"))
            throw new IllegalArgumentException("Example account must use ASCII letters/digits/._@-; use a JSON library for other accounts");
        byte[] body = user ? ("{\"loginAccount\":\"" + account + "\"}").getBytes(StandardCharsets.UTF_8) : new byte[0];
        String query = "";
        Map<String, String> h = headers(method, path, query, body, env("OPEN_API_CLIENT_ID"),
            env("OPEN_API_KEY_ID"), env("OPEN_API_HMAC_SECRET"), String.valueOf(Instant.now().getEpochSecond()),
            UUID.randomUUID().toString(), UUID.randomUUID().toString(), "");
        URI target = URI.create(base.getScheme() + "://" + base.getRawAuthority() + path + (query.isEmpty() ? "" : "?" + query));
        HttpRequest.Builder request = HttpRequest.newBuilder(target).timeout(Duration.ofSeconds(15));
        h.forEach(request::header);
        request.method(method, user ? HttpRequest.BodyPublishers.ofByteArray(body) : HttpRequest.BodyPublishers.noBody());
        HttpResponse<byte[]> response = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER).build().send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
        // Do not log user response bodies, phone numbers, secrets or signing material.
        System.out.println("HTTP status: " + response.statusCode() + "; requestId: " + h.get("X-Request-Id"));
        // Application code must parse response.body(), HTTP status, numeric code and errorCode.
        // Do not automatically repeat purchase creation after a timeout.
    }
}
