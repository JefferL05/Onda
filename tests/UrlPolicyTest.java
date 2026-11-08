import app.onda.mobile.UrlPolicy;

public final class UrlPolicyTest {
    private static int assertions;
    private static void check(boolean ok, String message) {
        assertions++;
        if (!ok) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        String expected = "https://m.youtube.com/watch?v=dQw4w9WgXcQ";
        check(expected.equals(UrlPolicy.canonicalVideo("https://youtu.be/dQw4w9WgXcQ?t=42")), "short URL canonicalization");
        check(expected.equals(UrlPolicy.canonicalVideo("https://www.youtube.com/watch?si=tracking&v=dQw4w9WgXcQ&list=abc")), "drop tracking");
        check(expected.equals(UrlPolicy.canonicalVideo("https://m.youtube.com/shorts/dQw4w9WgXcQ")), "shorts");
        check(UrlPolicy.canonicalVideo("https://youtube.com/results?search_query=music") == null, "search is not a track");
        check(UrlPolicy.canonicalVideo("https://youtube.com/watch?v=abc") == null, "reject invalid video ID");
        check(!UrlPolicy.youtube("https://youtube.com.attacker.example/watch?v=dQw4w9WgXcQ"), "lookalike domain");
        check(!UrlPolicy.youtube("https://attacker@youtube.com/watch?v=dQw4w9WgXcQ"), "userinfo");
        check(!UrlPolicy.youtube("javascript:alert(1)"), "javascript scheme");
        check(!UrlPolicy.youtube("http://youtube.com"), "cleartext");
        check(!UrlPolicy.youtube("https://youtube.com:8443"), "unexpected port");
        check(UrlPolicy.blocked("https://ad.doubleclick.net/pagead/ad"), "ad subdomain");
        check(!UrlPolicy.blocked("https://notdoubleclick.net/"), "filter domain boundary");
        check(!UrlPolicy.blocked("https://rr1.googlevideo.com/videoplayback"), "do not block media CDN");
        check(UrlPolicy.search("música & jazz").endsWith("m%C3%BAsica+%26+jazz"), "Unicode query encoding");
        check(UrlPolicy.canonicalVideo("https://youtu.be/") == null, "empty video ID");
        check(!UrlPolicy.youtube("not a url"), "malformed URL");
        // 1.0 hardening cases
        check(UrlPolicy.canonicalVideo("  https://youtu.be/dQw4w9WgXcQ  ") != null,
                "surrounding whitespace is trimmed before parsing");
        check(UrlPolicy.canonicalVideo("https://youtube.com/watch?v=dQw4w9WgXcQ&v=evil0000000") != null,
                "duplicate v picks the first canonical id");
        check(!UrlPolicy.youtube("https://youtube.com@attacker.example/"), "at-sign host spoof");
        check(!UrlPolicy.youtube("https://youtube.com%2eattacker.example/"), "encoded dot host spoof");
        check(!UrlPolicy.youtube("https://youtube.com./watch?v=dQw4w9WgXcQ"), "trailing-dot host");
        check(UrlPolicy.blocked("https://sub.doubleclick.net/x"), "nested ad host");
        check(!UrlPolicy.blocked("https://doubleclick.net.attacker.example/"), "ad lookalike");
        check(!UrlPolicy.youtube("data:text/html,<script>"), "data URI rejected");
        System.out.println("PASS: " + assertions + " URL/filter checks");
    }
}
