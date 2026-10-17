package app.onda.mobile;

import java.util.*;

/** Stable permutation: shuffle never selects the same item twice before the queue wraps. */
public final class PlaybackQueue {
    public enum Repeat { OFF, ALL, ONE }
    private final Random random;
    private List<String> base = new ArrayList<>();
    private List<String> order = new ArrayList<>();
    private String current;
    private String contextCursor;
    private final Set<String> manual = new HashSet<>();
    private final List<String> priority = new ArrayList<>();
    private boolean shuffle;
    private Repeat repeat = Repeat.OFF;

    public PlaybackQueue() { this(new Random()); }
    public PlaybackQueue(Random random) { this.random = random; }
    public void sync(List<String> urls) {
        List<String> unique = new ArrayList<>(new LinkedHashSet<>(urls));
        unique.removeIf(Objects::isNull);
        if (unique.equals(base)) return;
        base = unique;
        if (!shuffle) order = new ArrayList<>(base);
        else {
            order.removeIf(url -> !base.contains(url));
            List<String> added = new ArrayList<>(base);
            added.removeAll(order);
            Collections.shuffle(added, random);
            order.addAll(added);
        }
    }
    public void restore(List<String> savedOrder, boolean shuffled, Repeat mode, String selected) {
        shuffle = shuffled; repeat = mode; current = selected; contextCursor = selected;
        order = new ArrayList<>(new LinkedHashSet<>(savedOrder));
        order.removeIf(url -> !base.contains(url));
        for (String url : base) if (!order.contains(url)) order.add(url);
        if (!shuffle) order = new ArrayList<>(base);
    }
    public void prioritize(Collection<String> manualUrls, List<String> pending) {
        manual.clear(); manual.addAll(manualUrls);
        priority.clear();
        for (String url : pending) if (base.contains(url) && !priority.contains(url)) priority.add(url);
    }
    public String cursor() { return contextCursor; }
    public void restoreCursor(String value) { contextCursor = value; }
    private List<String> contextOrder() {
        List<String> result = new ArrayList<>(order); result.removeAll(manual); return result;
    }
    public List<String> order() {
        if (manual.isEmpty()) return new ArrayList<>(order);
        List<String> result = contextOrder();
        int after = Math.max(0, result.indexOf(manual.contains(current) ? contextCursor : current) + 1);
        List<String> insert = new ArrayList<>();
        if (manual.contains(current)) insert.add(current);
        for (String url : priority) if (!url.equals(current)) insert.add(url);
        result.removeAll(insert); result.addAll(Math.min(after, result.size()), insert);
        return result;
    }
    public String current() { return current; }
    public void select(String url) {
        current = url;
        if (!manual.contains(url)) contextCursor = url;
    }
    public boolean shuffled() { return shuffle; }
    public Repeat repeat() { return repeat; }
    public void repeat(Repeat mode) { repeat = mode; }
    public void shuffle(boolean enabled) {
        if (shuffle == enabled) return;
        shuffle = enabled;
        order = new ArrayList<>(base);
        if (enabled) {
            order.remove(current);
            Collections.shuffle(order, random);
            if (base.contains(current)) order.add(0, current);
        }
    }
    public String next(boolean naturalEnd) {
        if (order.isEmpty()) return null;
        if (naturalEnd && repeat == Repeat.ONE && base.contains(current)) return current;
        for (String pending : priority) if (!pending.equals(current) && base.contains(pending)) return pending;
        List<String> sequence = contextOrder();
        if (sequence.isEmpty()) return null;
        int index = sequence.indexOf(manual.contains(current) ? contextCursor : current);
        if (index + 1 < sequence.size()) return sequence.get(index + 1);
        return repeat == Repeat.ALL ? sequence.get(0) : null;
    }
    public String previous() {
        if (manual.contains(current) && base.contains(contextCursor)) return contextCursor;
        List<String> sequence = contextOrder();
        int index = sequence.indexOf(current);
        if (index > 0) return sequence.get(index - 1);
        return index == 0 && repeat == Repeat.ALL && !sequence.isEmpty() ? sequence.get(sequence.size() - 1) : null;
    }
}
