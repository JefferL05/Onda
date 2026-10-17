package app.onda.mobile;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.io.UnsupportedEncodingException;

/** Pure Java policy: no credentials, arbitrary schemes or lookalike domains. */
public final class UrlPolicy {
    private UrlPolicy() {}

    public static String host(String url) {
        try {
            URI uri = URI.create(url);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null
                    || (uri.getPort() != -1 && uri.getPort() != 443)) return "";
            return uri.getHost() == null ? "" : uri.getHost().toLowerCase(java.util.Locale.ROOT);
        } catch (IllegalArgumentException e) { return ""; }
    }

    private static boolean within(String host, String domain) {
        return host.equals(domain) || host.endsWith("." + domain);
    }

    public static boolean youtube(String url) {
        String host = host(url);
        return within(host, "youtube.com") || host.equals("youtu.be");
    }

    public static boolean blocked(String url) {
        String host = host(url);
        for (String domain : new String[]{"doubleclick.net", "googlesyndication.com",
                "googleadservices.com", "google-analytics.com", "googletagmanager.com"}) {
            if (within(host, domain)) return true;
        }
        return false;
    }

    public static String search(String text) {
        try {
            return "https://m.youtube.com/results?search_query="
                    + URLEncoder.encode(text.trim(), "UTF-8");
        } catch (UnsupportedEncodingException impossible) {
            throw new AssertionError(impossible);
        }
    }

    /** Store video identities only, dropping tracking and ephemeral parameters. */
    public static String canonicalVideo(String url) {
        if (!youtube(url)) return null;
        try {
            URI uri = URI.create(url);
            String id = null;
            String path = uri.getPath();
            if (host(url).equals("youtu.be")) id = path.substring(1);
            else if (path.startsWith("/shorts/") || path.startsWith("/live/")) {
                id = path.substring(path.lastIndexOf('/') + 1);
            } else if (path.equals("/watch") && uri.getRawQuery() != null) {
                for (String pair : uri.getRawQuery().split("&")) {
                    String[] bits = pair.split("=", 2);
                    if (bits.length == 2 && bits[0].equals("v")) {
                        id = URLDecoder.decode(bits[1], "UTF-8");
                        break;
                    }
                }
            }
            return id != null && id.matches("[A-Za-z0-9_-]{11}")
                    ? "https://m.youtube.com/watch?v=" + id : null;
        } catch (IllegalArgumentException | IndexOutOfBoundsException | UnsupportedEncodingException e) { return null; }
    }
}
