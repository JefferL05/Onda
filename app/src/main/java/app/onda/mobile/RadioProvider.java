package app.onda.mobile;

import org.schabi.newpipe.extractor.*;
import org.schabi.newpipe.extractor.stream.StreamExtractor;
import java.util.*;

interface RadioProvider {
    List<LibraryStore.Track> related(LibraryStore.Track seed) throws Exception;

    static RadioProvider youtube() {
        return seed -> {
            ExtractorInit.ensure();
            StreamExtractor extractor = ServiceList.YouTube.getStreamExtractor(seed.url);
            extractor.fetchPage();
            List<LibraryStore.Track> result = new ArrayList<>();
            InfoItemsCollector<?,?> collector = extractor.getRelatedItems();
            if (collector != null) for (InfoItem item : collector.getItems()) {
                YoutubeSearch.Result track = YoutubeSearch.from(item);
                if (track != null) result.add(new LibraryStore.Track(track.title, track.url, track.uploader, track.thumbnail, track.durationSec));
                if (result.size() >= 30) break;
            }
            return result;
        };
    }
}
