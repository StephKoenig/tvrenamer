package org.tvrenamer.controller;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.tvrenamer.controller.tvdb.TvdbV4Client;
import org.tvrenamer.controller.tvdb.TvdbV4Transport;
import org.tvrenamer.controller.tvdb.TvdbV4Transport.TvdbHttpResponse;
import org.tvrenamer.model.ShowName;
import org.tvrenamer.model.ShowOption;

public class TheTVDBv4ProviderTest {

    private static TvdbV4Client clientReturning(String searchBody) {
        TvdbV4Transport t = new TvdbV4Transport() {
            public TvdbHttpResponse post(String u, String b, java.util.Map<String,String> h) {
                return new TvdbHttpResponse(200, "{\"data\":{\"token\":\"tok\"}}");
            }
            public TvdbHttpResponse get(String u, java.util.Map<String,String> h) {
                return new TvdbHttpResponse(200, searchBody);
            }
        };
        return new TvdbV4Client(t, () -> "key");
    }

    @Test
    public void getShowOptionsPopulatesShowName() throws Exception {
        String body = "{\"data\":[{\"tvdb_id\":\"1001\",\"name\":\"Solar Drift\","
                    + "\"year\":\"2019\",\"aliases\":[\"SD\"]}]}";
        TheTVDBv4Provider provider = new TheTVDBv4Provider(clientReturning(body));
        ShowName sn = ShowName.mapShowName("solar drift");
        provider.getShowOptions(sn);
        List<ShowOption> opts = sn.getShowOptions();
        assertEquals(1, opts.size());
        assertEquals("1001", opts.get(0).getIdString());
    }
}
