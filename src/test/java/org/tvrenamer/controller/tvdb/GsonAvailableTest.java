package org.tvrenamer.controller.tvdb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

public class GsonAvailableTest {
    @Test
    public void gsonParsesObject() {
        JsonObject o = new Gson().fromJson("{\"status\":\"success\"}", JsonObject.class);
        assertEquals("success", o.get("status").getAsString());
    }
}
