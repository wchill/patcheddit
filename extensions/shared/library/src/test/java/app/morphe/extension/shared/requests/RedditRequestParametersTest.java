/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.shared.requests;

import org.junit.Test;

import java.io.IOException;
import java.util.Map;

import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class RedditRequestParametersTest {
    @Test
    public void readsQueryParameters() throws Exception {
        Map<String, String> parameters = RedditRequestParameters.read(new Request.Builder()
                .url("https://oauth.reddit.com/api/info?id=t3_one%2Ct3_two&raw_json=1")
                .build());
        assertEquals("t3_one,t3_two", parameters.get("id"));
        assertEquals("1", parameters.get("raw_json"));
    }

    @Test
    public void formBodyOverridesTheQuery() throws Exception {
        Map<String, String> parameters = RedditRequestParameters.read(new Request.Builder()
                .url("https://oauth.reddit.com/api/info?id=t3_old&raw_json=1")
                .post(new FormBody.Builder().add("id", "t3_new").build()).build());
        assertEquals("t3_new", parameters.get("id"));
        assertEquals("1", parameters.get("raw_json"));
    }

    @Test
    public void decodesGenericFormBodiesWithoutAndroidUriStubs() throws Exception {
        Map<String, String> parameters = RedditRequestParameters.read(new Request.Builder()
                .url("https://oauth.reddit.com/api/info")
                .post(RequestBody.create("id=t3_one%2Ct3_two&note=hello+world%2B%26"
                                + "&utf8=%E2%98%83&empty=&flag",
                        MediaType.get("application/x-www-form-urlencoded"))).build());
        assertEquals("t3_one,t3_two", parameters.get("id"));
        assertEquals("hello world+&", parameters.get("note"));
        assertEquals("\u2603", parameters.get("utf8"));
        assertEquals("", parameters.get("empty"));
        assertEquals("", parameters.get("flag"));
    }

    @Test
    public void rejectsMalformedFormEncoding() {
        Request request = new Request.Builder().url("https://oauth.reddit.com/api/info")
                .post(RequestBody.create("id=%ZZ",
                        MediaType.get("application/x-www-form-urlencoded"))).build();
        assertThrows(IOException.class, () -> RedditRequestParameters.read(request));
    }
}
