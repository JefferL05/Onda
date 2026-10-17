package app.onda.mobile;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.atomic.AtomicBoolean;

/** Never publishes partial downloads as playable files. */
public final class AudioDownload {
    public interface Progress { void update(long bytes, long total); }
    private static final long MAX_BYTES = 512L * 1024 * 1024;

    public static long transfer(String url, File destination, AtomicBoolean cancelled, Progress progress) throws IOException {
        File partial = new File(destination.getPath() + ".part");
        if (destination.exists()) throw new IOException("O arquivo já existe.");
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(20000);
        connection.setReadTimeout(30000);
        connection.setRequestProperty("User-Agent", ExtractorHttp.USER_AGENT);
        connection.setRequestProperty("Range", "bytes=0-");
        long copied = 0;
        boolean published = false;
        try {
            checkCancelled(cancelled);
            int code = connection.getResponseCode();
            if (code != 200 && code != 206) throw new IOException("O servidor recusou o download (HTTP " + code + "). Tente novamente.");
            long expected = connection.getContentLengthLong();
            if (code == 206) {
                String range = connection.getHeaderField("Content-Range");
                if (range == null || !range.matches("bytes 0-[0-9]+/[0-9]+")) throw new IOException("Resposta parcial inválida.");
                long total = Long.parseLong(range.substring(range.indexOf('/') + 1));
                if (expected != total) throw new IOException("O servidor não enviou o áudio inteiro.");
            }
            String mime = connection.getContentType();
            if (mime != null && (mime.contains("text/") || mime.contains("json"))) throw new IOException("O servidor retornou uma página em vez de áudio.");
            if (expected > MAX_BYTES) throw new IOException("Áudio acima do limite de 512 MB por arquivo.");
            if (expected > 0 && destination.getParentFile().getUsableSpace() < expected + 10 * 1024 * 1024L) throw new IOException("Espaço insuficiente no aparelho.");
            try (InputStream in = connection.getInputStream(); FileOutputStream out = new FileOutputStream(partial)) {
                byte[] buffer = new byte[65536];
                int count;
                long lastProgress = 0;
                while ((count = in.read(buffer)) != -1) {
                    checkCancelled(cancelled);
                    copied += count;
                    if (copied > MAX_BYTES) throw new IOException("Áudio acima do limite de 512 MB por arquivo.");
                    out.write(buffer, 0, count);
                    if (System.currentTimeMillis() - lastProgress > 300) {
                        progress.update(copied, expected);
                        lastProgress = System.currentTimeMillis();
                    }
                }
                out.getFD().sync();
            }
            checkCancelled(cancelled);
            if (copied < 16 || (expected >= 0 && copied != expected)) throw new IOException("Download incompleto. Tente novamente.");
            try (FileInputStream in = new FileInputStream(partial)) {
                byte[] header = new byte[8];
                if (in.read(header) != 8 || !isAudioContainer(header)) throw new IOException("Formato recebido não reconhecido.");
            }
            if (!partial.renameTo(destination)) throw new IOException("Não foi possível concluir o arquivo.");
            published = true;
            progress.update(copied, copied);
            return copied;
        } finally {
            connection.disconnect();
            if (!published && partial.exists() && !partial.delete()) partial.deleteOnExit();
        }
    }

    private static void checkCancelled(AtomicBoolean cancelled) throws IOException {
        if (cancelled.get() || Thread.currentThread().isInterrupted()) throw new IOException("Download cancelado.");
    }

    static boolean isAudioContainer(byte[] bytes) {
        return bytes.length >= 8 && ((bytes[4] == 'f' && bytes[5] == 't' && bytes[6] == 'y' && bytes[7] == 'p')
                || ((bytes[0] & 255) == 0x1a && (bytes[1] & 255) == 0x45 && (bytes[2] & 255) == 0xdf && (bytes[3] & 255) == 0xa3));
    }
}
