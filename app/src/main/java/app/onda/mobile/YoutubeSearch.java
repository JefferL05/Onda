package app.onda.mobile;

import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.ListExtractor;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.channel.ChannelInfoItem;
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler;
import org.schabi.newpipe.extractor.playlist.PlaylistInfo;
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem;
import org.schabi.newpipe.extractor.search.SearchExtractor;
import org.schabi.newpipe.extractor.search.SearchInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Native YouTube search and collection loading, without a WebView or API key.
 *
 * Filters map to YouTube's own music surfaces where available, so "Músicas"
 * returns songs rather than random uploads. Results are ranked locally by a
 * small relevance model (title match, duration sanity, channel match) instead
 * of trusting raw server order.
 */
public final class YoutubeSearch {
    public static final String FILTER_SONGS = "music_songs";
    public static final String FILTER_VIDEOS = "videos";
    public static final String FILTER_ALL = "";
    public static final String FILTER_ARTISTS = "music_artists";
    public static final String FILTER_ALBUMS = "music_albums";
    public static final String FILTER_PLAYLISTS = "music_playlists";

    public static final class Result {
        public final String id;
        public final String title;
        public final String uploader;
        public final String url;
        public final String thumbnail;
        public final long durationSec;
        public final String kind;
        public final String album;
        public final int views;

        Result(String id, String title, String uploader, String url, String thumbnail, long durationSec) {
            this(id, title, uploader, url, thumbnail, durationSec, "track", "", 0);
        }

        Result(String id, String title, String uploader, String url, String thumbnail, long durationSec,
               String kind, String album, int views) {
            this.id = id;
            this.title = title;
            this.uploader = uploader;
            this.url = url;
            this.thumbnail = thumbnail;
            this.durationSec = durationSec;
            this.kind = kind;
            this.album = album == null ? "" : album;
            this.views = views;
        }

        LibraryStore.Track track() {
            return new LibraryStore.Track(title, url, uploader, thumbnail, durationSec, 0, album, "");
        }
    }

    /** One page of results plus the token needed to fetch the next one. */
    public static final class PageOfResults {
        public final List<Result> items;
        public final Object next;   // internal Page; opaque to callers
        public final boolean corrected;
        public final String suggestion;

        PageOfResults(List<Result> items, Object next, boolean corrected, String suggestion) {
            this.items = items;
            this.next = next;
            this.corrected = corrected;
            this.suggestion = suggestion;
        }
    }

    private YoutubeSearch() {}

    public static List<Result> search(String query, String filter, int limit) throws Exception {
        return page(query, filter, null, limit).items;
    }

    /** Native "did you mean" from the search page itself; empty when absent. */
    public static String suggestion(String query) throws Exception {
        String trimmed = query == null ? "" : query.trim();
        if (trimmed.isEmpty()) return "";
        ExtractorInit.ensure();
        SearchExtractor extractor = ServiceList.YouTube.getSearchExtractor(
                ServiceList.YouTube.getSearchQHFactory().fromQuery(trimmed, Collections.emptyList(), ""));
        extractor.fetchPage();
        String value = extractor.getSearchSuggestion();
        return value == null || value.equalsIgnoreCase(trimmed) ? "" : value.trim();
    }

    public static PageOfResults page(String query, String filter, Object after, int limit) throws Exception {
        String trimmed = query == null ? "" : query.trim();
        if (trimmed.isEmpty()) throw new IllegalArgumentException("Digite sua busca");
        if (limit <= 0) limit = 20;
        ExtractorInit.ensure();
        List<String> filters = filter == null || filter.isEmpty()
                ? Collections.emptyList() : Collections.singletonList(filter);
        SearchQueryHandler handler = ServiceList.YouTube.getSearchQHFactory().fromQuery(trimmed, filters, "");
        if (!(after instanceof Page)) {
            SearchInfo info = SearchInfo.getInfo(ServiceList.YouTube, handler);
            List<Result> results = map(info.getRelatedItems(), filter, limit);
            return new PageOfResults(results, info.getNextPage(), info.isCorrectedSearch(), "");
        }
        ListExtractor.InfoItemsPage<InfoItem> more = SearchInfo.getMoreItems(ServiceList.YouTube, handler, (Page) after);
        List<Result> results = map(more.getItems(), filter, limit);
        return new PageOfResults(results, more.getNextPage(), false, "");
    }

    private static List<Result> map(List<InfoItem> items, String filter, int limit) {
        List<Result> results = new ArrayList<>();
        if (items == null) return results;
        for (InfoItem item : items) {
            Result result = from(item);
            if (result == null && item instanceof ChannelInfoItem) {
                result = new Result(item.getUrl(), item.getName(), "Artista / canal", item.getUrl(),
                        bestThumbnail(item.getThumbnails()), -1, "artist", "", 0);
            }
            if (result == null && item instanceof PlaylistInfoItem) {
                result = new Result(item.getUrl(), item.getName(),
                        FILTER_ALBUMS.equals(filter) ? "Álbum" : "Playlist", item.getUrl(),
                        bestThumbnail(item.getThumbnails()), -1,
                        FILTER_ALBUMS.equals(filter) ? "album" : "playlist", "", 0);
            }
            if (result != null) {
                results.add(result);
                if (results.size() >= limit) break;
            }
        }
        if (FILTER_SONGS.equals(filter) || FILTER_VIDEOS.equals(filter)) {
            results.sort((a, b) -> Integer.compare(rank(b, ""), rank(a, "")));
        }
        return results;
    }

    static final class CollectionResult {
        final String title, cover;
        final List<LibraryStore.Track> tracks;
        final boolean truncated;
        final String description;

        CollectionResult(String title, String cover, List<LibraryStore.Track> tracks, boolean truncated, String description) {
            this.title = title;
            this.cover = cover;
            this.tracks = tracks;
            this.truncated = truncated;
            this.description = description == null ? "" : description;
        }
    }

    static CollectionResult collection(String url, boolean album) throws Exception {
        if (!UrlPolicy.youtube(url)) throw new IllegalArgumentException("Coleção inválida");
        ExtractorInit.ensure();
        PlaylistInfo info = PlaylistInfo.getInfo(ServiceList.YouTube, url);
        List<StreamInfoItem> items = new ArrayList<>(info.getRelatedItems());
        Page next = info.getNextPage();
        int pages = 0;
        while (Page.isValid(next) && items.size() < 150 && pages++ < 6) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            ListExtractor.InfoItemsPage<StreamInfoItem> more = PlaylistInfo.getMoreItems(ServiceList.YouTube, url, next);
            items.addAll(more.getItems());
            next = more.getNextPage();
        }
        List<LibraryStore.Track> tracks = new ArrayList<>();
        for (StreamInfoItem item : items) {
            Result result = from(item);
            if (result != null) {
                tracks.add(new LibraryStore.Track(result.title, result.url, result.uploader, result.thumbnail,
                        result.durationSec, 0, album ? info.getName() : result.album, ""));
            }
            if (tracks.size() >= 150) break;
        }
        String description = "";
        try { description = info.getDescription().getContent(); } catch (Exception ignored) { }
        return new CollectionResult(info.getName(), bestThumbnail(info.getThumbnails()), tracks,
                Page.isValid(next) || items.size() > 150 || info.getStreamCount() > tracks.size(),
                description);
    }

    static Result from(InfoItem item) {
        if (!(item instanceof StreamInfoItem)) return null;
        StreamInfoItem stream = (StreamInfoItem) item;
        String canonical = UrlPolicy.canonicalVideo(stream.getUrl());
        if (canonical == null) return null;
        String title = stream.getName() == null || stream.getName().trim().isEmpty()
                ? "Áudio do YouTube" : stream.getName().trim();
        String uploader = stream.getUploaderName() == null ? "" : stream.getUploaderName().trim();
        int views = (int) Math.max(0, Math.min(Integer.MAX_VALUE, stream.getViewCount()));
        return new Result(OfflineStore.id(canonical), title, uploader, canonical,
                bestThumbnail(stream.getThumbnails()), stream.getDuration(), "track", "", views);
    }

    /**
     * Relevance: coverage of the query words in the title, a sane song length,
     * exact artist mentions and view momentum. Cheap, deterministic, testable.
     */
    static int rank(Result result, String query) {
        int score = 0;
        String title = LibraryQuery.normalized(result.title);
        String channel = LibraryQuery.normalized(result.uploader);
        List<String> words = new ArrayList<>();
        for (String word : LibraryQuery.normalized(query).split("\\s+")) if (word.length() > 1) words.add(word);
        int hit = 0;
        for (String word : words) if (title.contains(word)) hit++;
        if (!words.isEmpty()) score += 40 * hit / words.size();
        if (words.isEmpty()) score += 20;
        for (String word : words) if (channel.contains(word)) { score += 8; break; }
        long duration = result.durationSec;
        if (duration >= 45 && duration <= 480) score += 22;         // a song, not a podcast
        else if (duration > 480 && duration <= 1200) score += 6;
        else if (duration > 0) score -= 10;
        if (title.contains("official") || title.contains("oficial")) score += 4;
        if (title.contains("live") || title.contains("ao vivo")) score -= 2;
        if (title.contains("remix") || title.contains("cover")) score -= 3;
        if (result.views > 5_000_000) score += 6;
        else if (result.views > 200_000) score += 3;
        return score;
    }

    static String bestThumbnail(List<Image> images) {
        if (images == null || images.isEmpty()) return "";
        Image best = null;
        for (Image image : images) {
            if (image == null || image.getUrl() == null || image.getUrl().isEmpty()) continue;
            if (!image.getUrl().startsWith("https://")) continue;
            if (best == null) { best = image; continue; }
            if (image.getHeight() > best.getHeight()) best = image;
        }
        return best == null ? "" : best.getUrl();
    }
}
