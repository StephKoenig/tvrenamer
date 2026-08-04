package org.tvrenamer.controller.tvdb;

import java.io.IOException;
import java.util.Map;

/** Minimal HTTP seam for the v4 client, so auth/retry/parse are testable offline. */
public interface TvdbV4Transport {

    /** HTTP result: status code + raw response body (may be empty). */
    record TvdbHttpResponse(int status, String body) {}

    TvdbHttpResponse post(String url, String jsonBody, Map<String, String> headers)
        throws IOException;

    TvdbHttpResponse get(String url, Map<String, String> headers) throws IOException;
}
