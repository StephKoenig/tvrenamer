package org.tvrenamer.controller.tvdb;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

public class JdkHttpTransport implements TvdbV4Transport {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient client = HttpClient.newBuilder()
        .connectTimeout(TIMEOUT)
        .build();

    @Override
    public TvdbHttpResponse post(String url, String jsonBody, Map<String, String> headers)
        throws IOException {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
            .timeout(TIMEOUT)
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody));
        headers.forEach(b::header);
        return send(b.build());
    }

    @Override
    public TvdbHttpResponse get(String url, Map<String, String> headers) throws IOException {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
            .timeout(TIMEOUT)
            .GET();
        headers.forEach(b::header);
        return send(b.build());
    }

    private TvdbHttpResponse send(HttpRequest req) throws IOException {
        try {
            HttpResponse<String> resp =
                client.send(req, HttpResponse.BodyHandlers.ofString());
            return new TvdbHttpResponse(resp.statusCode(), resp.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        }
    }
}
