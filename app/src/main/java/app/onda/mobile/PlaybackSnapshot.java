package app.onda.mobile;

import org.json.*;
import java.util.*;

/** Durable user intent, never durable autoplay or expiring media URLs. */
final class PlaybackSnapshot {
    String url, title = "";
    String contextCursor;
    int position;
    boolean shuffle;
    PlaybackQueue.Repeat repeat = PlaybackQueue.Repeat.OFF;
    List<String> order = new ArrayList<>();

    String encode() {
        try {
            return new JSONObject().put("version", 1).put("url", url == null ? "" : url)
                    .put("contextCursor", contextCursor == null ? "" : contextCursor)
                    .put("title", title).put("position", Math.max(0, position))
                    .put("shuffle", shuffle).put("repeat", repeat.name())
                    .put("order", new JSONArray(order)).toString();
        } catch (JSONException e) { throw new IllegalStateException(e); }
    }
    static PlaybackSnapshot decode(String text) {
        PlaybackSnapshot result = new PlaybackSnapshot();
        try {
            JSONObject object = new JSONObject(text);
            if (object.optInt("version") != 1) return result;
            result.url = UrlPolicy.canonicalVideo(object.optString("url"));
            result.contextCursor = UrlPolicy.canonicalVideo(object.optString("contextCursor", object.optString("url")));
            result.title = object.optString("title", "");
            result.position = result.url == null ? 0 : Math.max(0, object.optInt("position", 0));
            result.shuffle = object.optBoolean("shuffle", false);
            try { result.repeat = PlaybackQueue.Repeat.valueOf(object.optString("repeat")); }
            catch (IllegalArgumentException ignored) { }
            JSONArray array = object.optJSONArray("order");
            if (array != null) for (int i = 0; i < array.length(); i++) {
                String url = UrlPolicy.canonicalVideo(array.optString(i));
                if (url != null && !result.order.contains(url)) result.order.add(url);
            }
        } catch (JSONException ignored) { return new PlaybackSnapshot(); }
        return result;
    }
}
