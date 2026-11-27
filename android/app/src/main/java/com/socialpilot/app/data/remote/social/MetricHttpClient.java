package com.socialpilot.app.data.remote.social;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/** Blocks OkHttp automatic 503 follow-up while preserving Retry-After headers and body tolerance. */
final class MetricHttpClient {
    private static final class RetryAfterHeaders {
        List<String> values;
    }

    private MetricHttpClient() {}

    static OkHttpClient forAdapter(OkHttpClient client) {
        return client.newBuilder()
                .followRedirects(false)
                .followSslRedirects(false)
                .retryOnConnectionFailure(false)
                .addInterceptor(chain -> {
                    RetryAfterHeaders hold = new RetryAfterHeaders();
                    Request tagged = chain.request().newBuilder()
                            .tag(RetryAfterHeaders.class, hold)
                            .build();
                    Response response = chain.proceed(tagged);
                    if (hold.values != null && !hold.values.isEmpty() && response.code() == 503) {
                        Response.Builder restored = response.newBuilder().removeHeader("Retry-After");
                        for (String value : hold.values) {
                            restored.addHeader("Retry-After", value);
                        }
                        return restored.build();
                    }
                    return response;
                })
                .addNetworkInterceptor(chain -> {
                    RetryAfterHeaders hold = chain.request().tag(RetryAfterHeaders.class);
                    if (hold != null) hold.values = null;
                    Response response = chain.proceed(chain.request());
                    if (hold != null && response.code() == 503) {
                        List<String> retryAfter = response.headers("Retry-After");
                        if (!retryAfter.isEmpty()) {
                            hold.values = retryAfter;
                            return response.newBuilder().removeHeader("Retry-After").build();
                        }
                    }
                    return response;
                })
                .build();
    }

    static String readBody(Response received, boolean tolerateBodyFailure) throws IOException {
        if (received.body() == null) return "";
        if (!tolerateBodyFailure) return received.body().string();
        try {
            return received.body().string();
        } catch (IOException ignored) {
            return "";
        }
    }

    static Long retryAt(String header, long receivedAt) {
        if (header == null) return null;
        try {
            long seconds = Long.parseLong(header);
            return seconds < 0 ? null : Math.addExact(receivedAt, Math.multiplyExact(seconds, 1000));
        } catch (NumberFormatException ignored) {
            SimpleDateFormat format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US);
            format.setLenient(false);
            format.setTimeZone(TimeZone.getTimeZone("GMT"));
            try { return format.parse(header).getTime(); }
            catch (ParseException ignoredDate) { return null; }
        } catch (ArithmeticException ignored) { return null; }
    }
}
