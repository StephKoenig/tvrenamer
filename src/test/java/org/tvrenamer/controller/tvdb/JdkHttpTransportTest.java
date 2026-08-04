package org.tvrenamer.controller.tvdb;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.Test;
import org.tvrenamer.controller.tvdb.TvdbV4Transport.TvdbHttpResponse;

public class JdkHttpTransportTest {
    @Test
    public void constructs() {
        assertNotNull(new JdkHttpTransport());
    }

    @Test
    public void responseRecordHoldsValues() {
        TvdbHttpResponse r = new TvdbHttpResponse(200, "body");
        org.junit.jupiter.api.Assertions.assertEquals(200, r.status());
        org.junit.jupiter.api.Assertions.assertEquals("body", r.body());
    }
}
