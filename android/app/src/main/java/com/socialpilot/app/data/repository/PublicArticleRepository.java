package com.socialpilot.app.data.repository;

import com.socialpilot.app.domain.editor.SharedUrlParser;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import okhttp3.Dns;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/** Reads bounded public HTML metadata only; never bypasses access controls. */
public final class PublicArticleRepository {
    private static final int MAX_BYTES = 131_072;
    private final OkHttpClient client;

    public enum Failure {
        INVALID_URL, PRIVATE_HOST, LOGIN_REQUIRED, PAYWALL, RATE_LIMIT,
        HTTP_ERROR, NON_HTML, TOO_LARGE, EMPTY_METADATA, NETWORK_ERROR, REDIRECT_LIMIT
    }

    public static final class Result {
        public final String requestedUrl;
        public final String url;
        public final String title;
        public final String description;
        public final Failure failure;

        private Result(String requestedUrl, String url, String title, String description,
                Failure failure) {
            this.requestedUrl = requestedUrl;
            this.url = url;
            this.title = title;
            this.description = description;
            this.failure = failure;
        }

        public boolean isSuccess() { return failure == null; }
    }

    public PublicArticleRepository() {
        this(new OkHttpClient.Builder().followRedirects(false)
                .dns(new PublicDns()).callTimeout(10, TimeUnit.SECONDS).build());
    }

    // Allows MockWebServer to exercise the same response parser on loopback.
    PublicArticleRepository(OkHttpClient client) { this.client = client; }

    public Result inspect(String input) {
        if (input == null || !input.equals(SharedUrlParser.firstPublicWebUrl(input))) {
            return failed(input, Failure.INVALID_URL);
        }
        HttpUrl url = HttpUrl.parse(input);
        if (url == null) return failed(input, Failure.INVALID_URL);
        for (int redirects = 0; redirects <= 3; redirects++) {
            try (Response response = client.newCall(new Request.Builder().url(url)
                    .header("User-Agent", "SocialPilot/0.1 public-link-preview")
                    .header("Accept", "text/html").build()).execute()) {
                int status = response.code();
                if (status >= 300 && status < 400) {
                    if (redirects == 3) return failed(input, Failure.REDIRECT_LIMIT);
                    String location = response.header("Location");
                    HttpUrl next = location == null ? null : url.resolve(location);
                    if (next == null || !next.toString().equals(
                            SharedUrlParser.firstPublicWebUrl(next.toString()))) {
                        return failed(input, Failure.INVALID_URL);
                    }
                    url = next;
                    continue;
                }
                if (status == 401 || status == 403) return failed(input, Failure.LOGIN_REQUIRED);
                if (status == 402) return failed(input, Failure.PAYWALL);
                if (status == 429) return failed(input, Failure.RATE_LIMIT);
                if (!response.isSuccessful()) return failed(input, Failure.HTTP_ERROR);
                ResponseBody body = response.body();
                if (body == null || body.contentType() == null
                        || !"text".equals(body.contentType().type())
                        || !"html".equals(body.contentType().subtype())) {
                    return failed(input, Failure.NON_HTML);
                }
                if (body.contentLength() > MAX_BYTES) return failed(input, Failure.TOO_LARGE);
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                try (InputStream stream = body.byteStream()) {
                    byte[] buffer = new byte[4096];
                    int read;
                    while ((read = stream.read(buffer)) != -1) {
                        if (bytes.size() + read > MAX_BYTES) return failed(input, Failure.TOO_LARGE);
                        bytes.write(buffer, 0, read);
                    }
                }
                Charset charset = body.contentType().charset(StandardCharsets.UTF_8);
                Document document = Jsoup.parse(bytes.toString(charset.name()), url.toString());
                if (document.selectFirst("form input[type=password]") != null) {
                    return failed(input, Failure.LOGIN_REQUIRED);
                }
                String title = document.title().trim();
                Element descriptionMeta = document.selectFirst(
                        "meta[property=og:description], meta[name=description]");
                String description = descriptionMeta == null ? ""
                        : descriptionMeta.attr("content").trim();
                if (title.isEmpty() && description.isEmpty()) {
                    return failed(input, Failure.EMPTY_METADATA);
                }
                return new Result(input, url.toString(), limit(title, 300),
                        limit(description, 1000), null);
            } catch (UnknownHostException error) {
                return failed(input, error.getMessage() != null
                        && error.getMessage().startsWith("private host")
                        ? Failure.PRIVATE_HOST : Failure.NETWORK_ERROR);
            } catch (IOException error) {
                return failed(input, Failure.NETWORK_ERROR);
            }
        }
        return failed(input, Failure.REDIRECT_LIMIT);
    }

    private static String limit(String text, int max) {
        return text.length() > max ? text.substring(0, max) : text;
    }

    private static Result failed(String url, Failure reason) {
        return new Result(url, url, "", "", reason);
    }

    private static final class PublicDns implements Dns {
        @Override public List<InetAddress> lookup(String hostname) throws UnknownHostException {
            List<InetAddress> addresses = Dns.SYSTEM.lookup(hostname);
            for (InetAddress address : addresses) {
                byte[] raw = address.getAddress();
                boolean uniqueLocalV6 = raw.length == 16 && (raw[0] & 0xfe) == 0xfc;
                boolean sharedV4 = raw.length == 4 && (raw[0] & 0xff) == 100
                        && ((raw[1] & 0xff) & 0xc0) == 0x40;
                if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                        || address.isSiteLocalAddress() || address.isLinkLocalAddress()
                        || address.isMulticastAddress() || uniqueLocalV6 || sharedV4) {
                    throw new UnknownHostException("private host");
                }
            }
            return addresses;
        }
    }
}
