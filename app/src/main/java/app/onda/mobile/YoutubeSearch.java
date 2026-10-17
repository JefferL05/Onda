package app.onda.mobile;

import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler;
import org.schabi.newpipe.extractor.search.SearchInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.schabi.newpipe.extractor.channel.ChannelInfoItem;
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem;
import org.schabi.newpipe.extractor.playlist.PlaylistInfo;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.ListExtractor;

/** Native YouTube search with covers, without WebView or API key. */
public final class YoutubeSearch {
    public static final String FILTER_SONGS = "music_songs";
    public static final String FILTER_VIDEOS = "videos";
    public static final String FILTER_ALL = "";
    public static final String FILTER_ARTISTS = "music_artists", FILTER_ALBUMS = "music_albums", FILTER_PLAYLISTS = "music_playlists";

    public static final class Result {
        public final String id;
        public final String title;
        public final String uploader;
        public final String url;
        public final String thumbnail;
        public final long durationSec;
        public final String kind;

        Result(String id, String title, String uploader, String url, String thumbnail, long durationSec) {
            this(id,title,uploader,url,thumbnail,durationSec,"track");
        }
        Result(String id, String title, String uploader, String url, String thumbnail, long durationSec, String kind) {
            this.id = id;
            this.title = title;
            this.uploader = uploader;
            this.url = url;
            this.thumbnail = thumbnail;
            this.durationSec = durationSec;
            this.kind = kind;
        }
    }

    private YoutubeSearch() {}

    public static List<Result> search(String query, String filter, int limit) throws Exception {
        String trimmed = query == null ? "" : query.trim();
        if (trimmed.isEmpty()) throw new IllegalArgumentException("Digite sua busca");
        if (limit <= 0) limit = 20;
        ExtractorInit.ensure();
        List<String> filters = filter == null || filter.isEmpty()
                ? Collections.emptyList() : Collections.singletonList(filter);
        SearchQueryHandler handler = ServiceList.YouTube.getSearchQHFactory()
                .fromQuery(trimmed, filters, "");
        SearchInfo info = SearchInfo.getInfo(ServiceList.YouTube, handler);
        List<Result> results = new ArrayList<>();
        if (info.getRelatedItems() != null) {
            for (InfoItem item : info.getRelatedItems()) {
                Result result = from(item);
                if (result == null && item instanceof ChannelInfoItem) result = new Result(item.getUrl(), item.getName(),
                        "Artista / canal", item.getUrl(), bestThumbnail(item.getThumbnails()), -1, "artist");
                if (result == null && item instanceof PlaylistInfoItem) result = new Result(item.getUrl(), item.getName(),
                        FILTER_ALBUMS.equals(filter) ? "Álbum" : "Playlist", item.getUrl(), bestThumbnail(item.getThumbnails()), -1,
                        FILTER_ALBUMS.equals(filter) ? "album" : "playlist");
                if (result != null) {
                    results.add(result);
                    if (results.size() >= limit) break;
                }
            }
        }
        return results;
    }

    static final class CollectionResult {
        final String title, cover; final List<LibraryStore.Track> tracks; final boolean truncated;
        CollectionResult(String title,String cover,List<LibraryStore.Track> tracks,boolean truncated) {
            this.title=title;this.cover=cover;this.tracks=tracks;this.truncated=truncated;
        }
    }
    static CollectionResult collection(String url,boolean album) throws Exception {
        if (!UrlPolicy.youtube(url)) throw new IllegalArgumentException("Coleção inválida");
        ExtractorInit.ensure();
        PlaylistInfo info=PlaylistInfo.getInfo(ServiceList.YouTube,url);
        List<StreamInfoItem> items=new ArrayList<>(info.getRelatedItems());
        Page next=info.getNextPage(); int pages=0;
        while(Page.isValid(next) && items.size()<100 && pages++<5) {
            if(Thread.currentThread().isInterrupted()) throw new InterruptedException();
            ListExtractor.InfoItemsPage<StreamInfoItem> more=PlaylistInfo.getMoreItems(ServiceList.YouTube,url,next);
            items.addAll(more.getItems()); next=more.getNextPage();
        }
        List<LibraryStore.Track> tracks=new ArrayList<>();
        for(StreamInfoItem item:items) {
            Result result=from(item);
            if(result!=null) tracks.add(new LibraryStore.Track(result.title,result.url,result.uploader,result.thumbnail,result.durationSec,0,album?info.getName():"",""));
            if(tracks.size()>=100) break;
        }
        return new CollectionResult(info.getName(),bestThumbnail(info.getThumbnails()),tracks,
                Page.isValid(next)||items.size()>100||info.getStreamCount()>tracks.size());
    }

    static Result from(InfoItem item) {
        if (!(item instanceof StreamInfoItem)) return null;
        StreamInfoItem stream = (StreamInfoItem) item;
        String canonical = UrlPolicy.canonicalVideo(stream.getUrl());
        if (canonical == null) return null;
        String title = stream.getName() == null || stream.getName().trim().isEmpty()
                ? "Áudio do YouTube" : stream.getName().trim();
        String uploader = stream.getUploaderName() == null ? "" : stream.getUploaderName().trim();
        return new Result(OfflineStore.id(canonical), title, uploader, canonical,
                bestThumbnail(stream.getThumbnails()), stream.getDuration());
    }

    static String bestThumbnail(List<Image> images) {
        if (images == null || images.isEmpty()) return "";
        Image best = null;
        for (Image image : images) {
            if (image == null || image.getUrl() == null || image.getUrl().isEmpty()) continue;
            if (!image.getUrl().startsWith("https://")) continue;
            if (best == null) { best = image; continue; }
            int height = image.getHeight();
            int bestHeight = best.getHeight();
            if (height > bestHeight) best = image;
        }
        return best == null ? "" : best.getUrl();
    }
}
