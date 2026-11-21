package com.socialpilot.app.data.remote.social;

import com.socialpilot.app.domain.contract.social.ChannelInfo;
import com.socialpilot.app.domain.contract.social.Platform;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;
import java.util.TimeZone;

/** One provider query, one post, one collection. No cross-endpoint window alignment. */
public final class PostRequest {
    public enum Query {
        FB_REACTIONS, FB_LIKES, FB_COMMENTS_TOPLEVEL, FB_COMMENTS_STREAM, FB_SHARES,
        FB_MEDIA_VIEWS, FB_UNIQUE_VIEWERS, FB_IMPRESSIONS,
        MEMBER_IMPRESSION, MEMBER_REACTION, MEMBER_COMMENT, MEMBER_RESHARE, MEMBER_REACHED,
        ORGANIZATION_ORGANIC, IG_REACH, IG_LIKES, IG_COMMENTS, IG_SHARES, IG_IMPRESSIONS
    }

    /** Date-only provider bounds require an explicitly verified zone; never assume UTC.
     * The caller supplies evidence for the date-to-instant mapping for this account/version.
     * There is deliberately no default period, zone, or open-ended range.
     */
    public static final class Period {
        public final String startDate;
        public final String endDate;
        public final String verifiedZoneId;
        public final String evidenceRef;
        public final long start;
        public final long end;

        public Period(String startDate, String endDate, String verifiedZoneId, String evidenceRef) {
            this.startDate = FollowerRequest.text(startDate);
            this.endDate = FollowerRequest.text(endDate);
            this.verifiedZoneId = FollowerRequest.text(verifiedZoneId);
            this.evidenceRef = reference(evidenceRef);
            boolean known = false;
            for (String id : TimeZone.getAvailableIDs()) if (id.equals(verifiedZoneId)) known = true;
            if (!known) throw new IllegalArgumentException("verifiedZoneId");
            start = date(startDate, verifiedZoneId);
            end = date(endDate, verifiedZoneId);
            if (start >= end) throw new IllegalArgumentException("period order");
        }

        private static long date(String value, String zone) {
            if (!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw new IllegalArgumentException("date");
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            format.setLenient(false);
            format.setTimeZone(TimeZone.getTimeZone(zone));
            ParsePosition position = new ParsePosition(0);
            Date parsed = format.parse(value, position);
            if (parsed == null || position.getIndex() != value.length()) throw new IllegalArgumentException("date");
            return parsed.getTime();
        }

        String wire() { return "(start:" + wireDate(startDate) + ",end:" + wireDate(endDate) + ")"; }
        private static String wireDate(String date) {
            return "(year:" + Integer.parseInt(date.substring(0, 4)) + ",month:"
                    + Integer.parseInt(date.substring(5, 7)) + ",day:" + Integer.parseInt(date.substring(8)) + ")";
        }
    }

    public final FollowerRequest.Account account;
    public final Platform platform;
    public final String channelId;
    public final long connectionRevision;
    public final String externalAccountId;
    public final String apiVersion;
    public final String collectionId;
    public final String postId;
    public final Query query;
    public final Period period;
    private final FollowerRequest accountScope;

    public PostRequest(FollowerRequest.Account account, String channelId, long revision,
            String externalAccountId, String postId, String apiVersion, String collectionId,
            Query query, Period period) {
        accountScope = new FollowerRequest(account, channelId, revision, externalAccountId, apiVersion, collectionId);
        this.account = account;
        platform = accountScope.platform;
        this.channelId = channelId;
        connectionRevision = revision;
        this.externalAccountId = externalAccountId;
        this.apiVersion = apiVersion;
        this.collectionId = collectionId;
        this.postId = FollowerRequest.text(postId);
        this.query = Objects.requireNonNull(query, "query");
        this.period = period;
        boolean facebook = query.name().startsWith("FB_");
        boolean member = query.name().startsWith("MEMBER_");
        if (facebook != (account == FollowerRequest.Account.FACEBOOK_PAGE)
                || query.name().startsWith("IG_") != (platform == Platform.INSTAGRAM)
                || member != (account == FollowerRequest.Account.LINKEDIN_MEMBER)) {
            throw new IllegalArgumentException("query/account");
        }
        if (platform == Platform.LINKEDIN && !postId.matches("urn:li:(share|ugcPost):[0-9]+")) {
            throw new IllegalArgumentException("post URN");
        }
        if (period != null && !member) throw new IllegalArgumentException("single org post has no period API");
    }

    public boolean matches(ChannelInfo channel) { return accountScope.matches(channel); }

    boolean sameTarget(PostRequest other) {
        return other != null && accountScope.sameTarget(other.accountScope) && postId.equals(other.postId)
                && query == other.query && (period == null ? other.period == null : other.period != null
                && period.startDate.equals(other.period.startDate) && period.endDate.equals(other.period.endDate)
                && period.verifiedZoneId.equals(other.period.verifiedZoneId)
                && period.evidenceRef.equals(other.period.evidenceRef));
    }

    static String reference(String value) {
        FollowerRequest.text(value);
        if (!value.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,199}")) throw new IllegalArgumentException("redacted reference");
        return value;
    }
}
