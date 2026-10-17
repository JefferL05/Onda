package app.onda.mobile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Pure download-queue rules: ordering, dedup, retry budget and Wi-Fi gating. */
public final class DownloadQueue {
    public enum State { WAITING, ACTIVE, DONE, FAILED }

    public static final class Job {
        public final String id;
        public final String url;
        public final String title;
        public State state;
        public int progress;
        public String error;
        public int attempts;
        public long addedAt;

        Job(String id, String url, String title) {
            this.id = id;
            this.url = url;
            this.title = title;
            this.state = State.WAITING;
            this.progress = -1;
            this.error = "";
            this.addedAt = System.currentTimeMillis();
        }
    }

    private final Map<String, Job> jobs = new LinkedHashMap<>();
    private final int maxJobs;

    public DownloadQueue() { this(50); }
    public DownloadQueue(int maxJobs) { this.maxJobs = maxJobs; }

    public synchronized Job enqueue(String url, String title) {
        String canonical = UrlPolicy.canonicalVideo(url == null ? "" : url);
        if (canonical == null) throw new IllegalArgumentException("Vídeo inválido");
        String id = OfflineStore.id(canonical);
        Job existing = jobs.get(id);
        if (existing != null && existing.state != State.DONE) return existing;
        if (jobs.size() >= maxJobs) throw new IllegalStateException("Fila cheia");
        String name = title == null || title.trim().isEmpty() ? "Áudio do YouTube" : title.trim();
        Job job = new Job(id, canonical, name);
        jobs.put(id, job);
        return job;
    }

    public synchronized Job active() {
        for (Job job : jobs.values()) if (job.state == State.ACTIVE) return job;
        return null;
    }

    public synchronized Job nextPending(boolean wifiOnly, boolean onWifi) {
        if (wifiOnly && !onWifi) return null;
        for (Job job : jobs.values()) if (job.state == State.WAITING) return job;
        return null;
    }

    public synchronized void markActive(String id) {
        Job job = jobs.get(id);
        if (job == null) return;
        job.state = State.ACTIVE;
        job.progress = -1;
        job.error = "";
    }

    public synchronized void markProgress(String id, int progress) {
        Job job = jobs.get(id);
        if (job != null && job.state == State.ACTIVE) job.progress = progress;
    }

    public synchronized void markDone(String id) {
        Job job = jobs.get(id);
        if (job != null) { job.state = State.DONE; job.progress = 100; }
    }

    public synchronized void markFailed(String id, String error) {
        Job job = jobs.get(id);
        if (job == null) return;
        job.attempts++;
        job.state = State.FAILED;
        job.error = error == null ? "" : error;
    }

    public synchronized boolean retry(String id) {
        Job job = jobs.get(id);
        if (job == null || job.state != State.FAILED) return false;
        if (job.attempts >= 3) return false;
        job.state = State.WAITING;
        job.progress = -1;
        job.error = "";
        return true;
    }

    public synchronized boolean remove(String id) { return jobs.remove(id) != null; }

    public synchronized void pruneFinished() {
        jobs.values().removeIf(job -> job.state == State.DONE);
    }

    public synchronized List<Job> list() { return new ArrayList<>(jobs.values()); }

    public synchronized int pendingCount() {
        int count = 0;
        for (Job job : jobs.values()) if (job.state == State.WAITING || job.state == State.ACTIVE) count++;
        return count;
    }
}
