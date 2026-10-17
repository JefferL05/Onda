package app.onda.mobile;

import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/** Metadata transport, deliberately independent of Android for integration testing. */
public final class ExtractorHttp extends Downloader {
    static final String USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36";

    @Override public Response execute(Request request) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(request.url()).openConnection();
        connection.setConnectTimeout(20000);
        connection.setReadTimeout(30000);
        connection.setRequestMethod(request.httpMethod());
        connection.setRequestProperty("User-Agent", USER_AGENT);
        for (Map.Entry<String, List<String>> header : request.headers().entrySet()) {
            connection.setRequestProperty(header.getKey(), String.join(", ", header.getValue()));
        }
        try {
            if (request.dataToSend() != null) {
                connection.setDoOutput(true);
                try (java.io.OutputStream out = connection.getOutputStream()) { out.write(request.dataToSend()); }
            }
            int code = connection.getResponseCode();
            InputStream raw = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
            String body = "";
            if (raw != null) {
                try (InputStream input = "gzip".equalsIgnoreCase(connection.getContentEncoding()) ? new GZIPInputStream(raw) : raw;
                     ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[16384];
                    int size;
                    while ((size = input.read(buffer)) != -1) {
                        if (Thread.currentThread().isInterrupted()) throw new IOException("Cancelado");
                        if (bytes.size() + size > 32 * 1024 * 1024) throw new IOException("Resposta de metadados muito grande");
                        bytes.write(buffer, 0, size);
                    }
                    body = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
                }
            }
            return new Response(code, connection.getResponseMessage(), connection.getHeaderFields(), body, connection.getURL().toString());
        } finally { connection.disconnect(); }
    }
}
