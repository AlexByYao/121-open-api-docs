import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** JDK 11+只读联调示例，凭据从环境变量读取，不打印用户资料。 */
public final class OpenApiExample {
    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 1 || !("user".equals(arguments[0]) || "departments".equals(arguments[0]))) {
            System.out.println("Usage: java OpenApiExample.java user|departments");
            return;
        }
        String baseUrl = required("OPEN_API_BASE_URL").replaceAll("/+$", "");
        URI base = URI.create(baseUrl);
        if (!("https".equals(base.getScheme()) || "http".equals(base.getScheme()))
                || base.getHost() == null || base.getUserInfo() != null
                || base.getQuery() != null || base.getFragment() != null
                || !"/open-api/v1".equals(base.getPath())) {
            throw new IllegalArgumentException("OPEN_API_BASE_URL must end with /open-api/v1");
        }
        boolean user = "user".equals(arguments[0]);
        String path = user ? "/users/verify" : "/dictionaries/departments?pageNum=1&pageSize=100";
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(10))
                .header("App-Id", required("OPEN_API_APP_ID"))
                .header("App-Secret", required("OPEN_API_APP_SECRET"))
                .header("Accept", "application/json");
        if (user) {
            String account = required("OPEN_API_LOGIN_ACCOUNT");
            StringBuilder escaped = new StringBuilder();
            for (char character : account.toCharArray()) {
                if (character < 0x20) throw new IllegalArgumentException("Invalid login account");
                if (character == '"' || character == '\\') escaped.append('\\');
                escaped.append(character);
            }
            builder.header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"loginAccount\":\"" + escaped + "\"}"));
        } else {
            builder.GET();
        }
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        Matcher requestId = Pattern.compile("\"requestId\"\\s*:\\s*\"([A-Za-z0-9._:-]{1,128})\"")
                .matcher(response.body());
        System.out.println("HTTP " + response.statusCode()
                + (requestId.find() ? " requestId=" + requestId.group(1) : ""));
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isEmpty()) throw new IllegalArgumentException("Missing " + name);
        return value;
    }
}
