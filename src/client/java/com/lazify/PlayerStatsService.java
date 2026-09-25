package com.lazify;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

final class PlayerStatsService {
    private static final Logger LOGGER = LoggerFactory.getLogger("lazify");
    private static final long SUCCESS_TTL_MS = TimeUnit.MINUTES.toMillis(10);
    private static final long FAILURE_TTL_MS = TimeUnit.SECONDS.toMillis(45);
    private static final long SESSION_TTL_MS = TimeUnit.MINUTES.toMillis(5);
    private static final long PENDING_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(60);
    private static final int REQUEST_TIMEOUT_MS = 10_000;
    private static final int SESSION_TIMEOUT_MS = 12_000;
    private static final int MAX_BULK = 10;
    private static final int MAX_PENDING_SESSIONS = 512;

    private static final String ABYSS_URL = "http://api.abyssoverlay.com/player?uuid=";
    private static final String PRISM_URL = "https://flashlight.prismoverlay.com/v1/playerdata?uuid=";
    private static final String HYPIXEL_URL = "https://api.hypixel.net/v2/player?uuid=";
    private static final String BORDIC_CACHE_URL = "https://bordic.xyz/api/v2/resources/cache/hypixel?uuid=";
    private static final String BORDIC_CACHE_KEY_URL = "https://bordic.xyz/api/v2/resources/cache/hypixel?key=";
    private static final String BORDIC_WINSTREAK_URL = "https://bordic.xyz/api/v2/resources/winstreak?uuid=";
    private static final String BORDIC_SESSIONS_URL = "https://api.bordic.xyz/v4/sessions/";
    private static final String CORAL_URL = "https://api.urchin.gg/v3/player/tags?player=";
    private static final String LEGACY_URCHIN_URL = "https://urchin.ws/player/";
    private static final String SERAPH_URL = "https://api.seraph.si/";

    private static final Map<String, String> ABYSS_HEADERS = Map.of("User-Agent", "node-ao/2.0.3");

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(
            3, 3, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(512),
            daemonThreadFactory("Lazify API worker"), new ThreadPoolExecutor.AbortPolicy()
    );
    private final ScheduledThreadPoolExecutor sessionScheduler = new ScheduledThreadPoolExecutor(
            1, daemonThreadFactory("Lazify Bordic sessions")
    );
    private final ConcurrentMap<UUID, CachedStats> statsCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Pending> statsPending = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, CachedTag> tagCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Pending> tagsPending = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, SessionCache> sessionCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, SessionRequest> sessionsPending = new ConcurrentHashMap<>();
    private final ArrayBlockingQueue<SessionRequest> sessionQueue = new ArrayBlockingQueue<>(MAX_PENDING_SESSIONS);
    private final ConcurrentMap<UUID, String> usernames = new ConcurrentHashMap<>();
    private final AtomicBoolean sessionFlushScheduled = new AtomicBoolean();
    private final AtomicLong generation = new AtomicLong();
    private final String prismUserId = UUID.randomUUID().toString().replace("-", "");

    void request(UUID uuid, String username, String hypixelKey, String bordicKey, String urchinKey, String seraphKey) {
        if (uuid == null) return;
        String name = username == null ? "" : username.trim();
        if (!name.isEmpty()) usernames.put(uuid, name);
        updateCachedUsername(uuid, name);
        long requestGeneration = generation.get();
        requestStats(uuid, name, cleanKey(hypixelKey), cleanKey(bordicKey), requestGeneration);
        requestTags(uuid, name, cleanKey(urchinKey), cleanKey(seraphKey), requestGeneration);
        requestSessions(uuid, name, cleanKey(bordicKey), requestGeneration);
    }

    LegacyPlayerStats stats(UUID uuid) {
        if (uuid == null) return LegacyPlayerStats.unavailable("", "unavailable", false, "");
        CachedStats cached = statsCache.get(uuid);
        if (cached != null && cached.generation == generation.get()) return cached.value;
        return LegacyPlayerStats.unavailable(usernames.get(uuid), "pending", false, "");
    }

    String tag(UUID uuid) {
        if (uuid == null) return "";
        CachedTag cached = tagCache.get(uuid);
        return cached == null || cached.generation != generation.get() || !cached.info.available ? "" : cached.info.display;
    }

    void clear() {
        generation.incrementAndGet();
        statsCache.clear();
        statsPending.clear();
        tagCache.clear();
        tagsPending.clear();
        sessionCache.clear();
        sessionsPending.clear();
        sessionQueue.clear();
        usernames.clear();
    }

    private void requestStats(UUID uuid, String username, String hypixelKey, String bordicKey, long requestGeneration) {
        long now = System.currentTimeMillis();
        CachedStats cached = statsCache.get(uuid);
        if (cached != null && cached.generation == requestGeneration && cached.expiresAt > now) return;
        Pending pending = new Pending(requestGeneration, now);
        Pending existing = statsPending.get(uuid);
        if (existing != null && (now - existing.startedAt > PENDING_TIMEOUT_MS || existing.generation != requestGeneration)) {
            statsPending.remove(uuid, existing);
        }
        if (statsPending.putIfAbsent(uuid, pending) != null) return;
        try {
            executor.execute(() -> fetchStats(uuid, username, hypixelKey, bordicKey, pending));
        } catch (RejectedExecutionException rejected) {
            statsPending.remove(uuid, pending);
            LOGGER.debug("Player stats request skipped because the bounded worker queue is full");
        }
    }

    private void fetchStats(UUID uuid, String username, String hypixelKey, String bordicKey, Pending pending) {
        try {
            StatsResult result = fetchStats(uuid, username, hypixelKey, bordicKey);
            if (pending.generation != generation.get()) return;
            long ttl = result.profile != null ? SUCCESS_TTL_MS : FAILURE_TTL_MS;
            cacheStats(uuid, username, result.profile, result.nicked, result.nickReason, ttl, pending.generation);
        } catch (RuntimeException failure) {
            if (pending.generation == generation.get()) {
                cacheStats(uuid, username, null, false, "stats providers unavailable", FAILURE_TTL_MS, pending.generation);
            }
            LOGGER.debug("Player stats parsing failed; the cached result remains unavailable");
        } finally {
            statsPending.remove(uuid, pending);
        }
    }

    private StatsResult fetchStats(UUID uuid, String username, String hypixelKey, String bordicKey) {
        String dashless = uuid.toString().replace("-", "").toLowerCase(Locale.ROOT);
        ApiResponse abyssResponse = get(ABYSS_URL + dashless, ABYSS_HEADERS, REQUEST_TIMEOUT_MS);
        ApiResponse prismResponse = get(PRISM_URL + dashless, prismHeaders(), REQUEST_TIMEOUT_MS);
        BedwarsStats merged = profile(abyssResponse, "abyss", username);
        BedwarsStats prism = profile(prismResponse, "prism", username);
        if (prism != null) merged = merged == null ? prism : merged.merge(prism);

        ApiResponse bordicResponse = null;
        ApiResponse hypixelResponse = null;
        if (merged != null && needsRank(merged)) {
            bordicResponse = fetchBordicCache(dashless, bordicKey);
            BedwarsStats bordic = profile(bordicResponse, "bordic", username);
            if (bordic != null) merged = merged.merge(bordic);
        }
        if (merged != null && needsRank(merged) && !hypixelKey.isBlank()) {
            hypixelResponse = fetchHypixel(dashless, hypixelKey);
            BedwarsStats hypixel = profile(hypixelResponse, "hypixel", username);
            if (hypixel != null) merged = merged.merge(hypixel);
        }
        if (merged == null && !hypixelKey.isBlank()) {
            if (hypixelResponse == null) hypixelResponse = fetchHypixel(dashless, hypixelKey);
            merged = profile(hypixelResponse, "hypixel", username);
        }
        if (merged != null) {
            merged = merged.withUsername(username);
            if (!merged.hasWinstreak() || merged.winstreak() == 0L) {
                BedwarsWinstreaks fallback = fetchBordicWinstreaks(dashless);
                if (fallback != null) merged = merged.withWinstreakFallback(fallback.overall, fallback.modes);
            }
            return new StatsResult(merged, false, "");
        }

        boolean offlineUuid = isOfflineUuid(dashless);
        boolean abyssNick = isNullPlayerNick(abyssResponse, offlineUuid);
        boolean prismNick = isPrismNick(prismResponse);
        boolean bordicChecked = !bordicKey.isBlank();
        if (bordicChecked && bordicResponse == null) bordicResponse = fetchBordicCache(dashless, bordicKey);
        boolean bordicNick = !bordicChecked || isBordicNick(bordicResponse, offlineUuid);
        boolean hypixelChecked = !hypixelKey.isBlank();
        boolean hypixelNick = !hypixelChecked || isNullPlayerNick(hypixelResponse, offlineUuid);
        String reason = "abyss=" + yn(abyssNick) + " prism=" + yn(prismNick)
                + " bordic=" + (bordicChecked ? yn(bordicNick) : "skip")
                + " hypixel=" + (hypixelChecked ? yn(hypixelNick) : "skip");
        if (offlineUuid) return new StatsResult(null, true, reason + " offline=Y");
        boolean confirmedNick = abyssNick && prismNick && bordicNick && hypixelNick;
        return new StatsResult(null, confirmedNick, reason);
    }

    private void cacheStats(
            UUID uuid,
            String username,
            BedwarsStats profile,
            boolean nicked,
            String nickReason,
            long ttl,
            long requestGeneration
    ) {
        if (requestGeneration != generation.get()) return;
        statsCache.compute(uuid, (ignored, previous) -> {
            if (requestGeneration != generation.get()) return previous;
            BedwarsStats source = profile;
            SessionCache sessions = freshSessions(uuid);
            if (sessions != null) source = withSessionLifetime(source, username, sessions.snapshots);
            LegacyPlayerStats value = source == null
                    ? LegacyPlayerStats.unavailable(username, nicked ? "nicked" : "unavailable", nicked, nickReason)
                    : LegacyPlayerStats.from(source.withUsername(username));
            if (source != null && nicked) {
                value = new LegacyPlayerStats(value.columns(), true, nickReason, value.rankPrefix(), value.provider());
            }
            if (sessions != null) value = value.withColumns(periodColumns(sessions.snapshots));
            CachedTag cachedTag = tagCache.get(uuid);
            if (cachedTag != null && cachedTag.generation == requestGeneration) {
                value = value.withColumns(tagColumns(cachedTag.info));
            }
            return new CachedStats(source, value, System.currentTimeMillis() + ttl, requestGeneration);
        });
    }

    private void updateCachedUsername(UUID uuid, String username) {
        if (username.isEmpty()) return;
        long currentGeneration = generation.get();
        statsCache.computeIfPresent(uuid, (ignored, cached) -> {
            if (cached.generation != currentGeneration) return cached;
            BedwarsStats source = cached.source == null ? null : cached.source.withUsername(username);
            return new CachedStats(source, cached.value.withColumns(Map.of("username", username)),
                    cached.expiresAt, cached.generation);
        });
    }

    private void requestTags(UUID uuid, String username, String urchinKey, String seraphKey, long requestGeneration) {
        if (urchinKey.isBlank() && seraphKey.isBlank()) return;
        long now = System.currentTimeMillis();
        CachedTag cached = tagCache.get(uuid);
        if (cached != null && cached.generation == requestGeneration && cached.expiresAt > now) {
            applyTagToStats(uuid, cached.info, requestGeneration);
            return;
        }
        Pending pending = new Pending(requestGeneration, now);
        Pending existing = tagsPending.get(uuid);
        if (existing != null && (now - existing.startedAt > PENDING_TIMEOUT_MS || existing.generation != requestGeneration)) {
            tagsPending.remove(uuid, existing);
        }
        if (tagsPending.putIfAbsent(uuid, pending) != null) return;
        try {
            executor.execute(() -> fetchTags(uuid, username, urchinKey, seraphKey, pending));
        } catch (RejectedExecutionException rejected) {
            tagsPending.remove(uuid, pending);
            LOGGER.debug("Player tag request skipped because the bounded worker queue is full");
        }
    }

    private void fetchTags(UUID uuid, String username, String urchinKey, String seraphKey, Pending pending) {
        try {
            TagPart urchin = urchinKey.isBlank() ? TagPart.unavailable() : fetchUrchinTag(uuid, username, urchinKey);
            TagPart seraph = seraphKey.isBlank() ? TagPart.unavailable() : fetchSeraphTag(uuid, seraphKey);
            boolean available = urchin.available || seraph.available;
            TagInfo info = new TagInfo(
                    available,
                    urchin.type,
                    urchin.reason,
                    seraph.type,
                    seraph.reason,
                    tagDisplay(urchin.type, seraph.type)
            );
            if (pending.generation == generation.get()) {
                long ttl = available ? SUCCESS_TTL_MS : FAILURE_TTL_MS;
                tagCache.put(uuid, new CachedTag(info, System.currentTimeMillis() + ttl, pending.generation));
                applyTagToStats(uuid, info, pending.generation);
            }
        } catch (RuntimeException failure) {
            LOGGER.debug("Player tag parsing failed; tags remain unavailable");
        } finally {
            tagsPending.remove(uuid, pending);
        }
    }

    private TagPart fetchUrchinTag(UUID uuid, String username, String apiKey) {
        String id = uuid.toString().replace("-", "");
        boolean available = false;
        ArrayList<String> players = new ArrayList<>(2);
        players.add(id);
        if (username != null && !username.isBlank() && !username.equalsIgnoreCase(id)) players.add(username);
        for (String player : players) {
            String encodedPlayer = encode(player);
            ApiResponse headerResponse = get(CORAL_URL + encodedPlayer,
                    Map.of("X-API-Key", apiKey), 3_000);
            TagPart part = coralTag(headerResponse);
            if (part.available) available = true;
            if (!part.type.isEmpty()) return part;

            ApiResponse queryResponse = get(CORAL_URL + encodedPlayer + "&key=" + encode(apiKey), Map.of(), 3_000);
            part = coralTag(queryResponse);
            if (part.available) available = true;
            if (!part.type.isEmpty()) return part;
        }

        ApiResponse legacy = get(LEGACY_URCHIN_URL + encode(id) + "?key=" + encode(apiKey) + "&sources=GAME", Map.of(), 3_000);
        TagPart legacyPart = coralTag(legacy);
        if (legacyPart.available) available = true;
        if (!legacyPart.type.isEmpty()) return legacyPart;
        return new TagPart(available, "", "");
    }

    private TagPart fetchSeraphTag(UUID uuid, String apiKey) {
        ApiResponse response = get(SERAPH_URL + uuid.toString().replace("-", "") + "/blacklist",
                Map.of("seraph-api-key", apiKey), 3_000);
        if (response.code != 200 || response.body == null || !bool(response.body, "success", false)) {
            return TagPart.unavailable();
        }
        JsonObject data = object(response.body, "data");
        JsonObject blacklist = object(data, "blacklist");
        if (blacklist == null) return new TagPart(true, "", "");
        if (!bool(blacklist, "tagged", false)) return new TagPart(true, "", "");
        String type = text(blacklist, "report_type", "");
        String reason = text(blacklist, "reason", "");
        if (reason.isEmpty()) reason = text(blacklist, "tooltip", "");
        if (type.isEmpty() && reason.isEmpty()) return new TagPart(true, "", "");
        return new TagPart(true, type.isEmpty() ? "blacklisted" : type, reason);
    }

    private void applyTagToStats(UUID uuid, TagInfo info, long requestGeneration) {
        if (requestGeneration != generation.get()) return;
        Map<String, String> columns = tagColumns(info);
        statsCache.computeIfPresent(uuid, (ignored, cached) -> cached.generation != requestGeneration
                ? cached : new CachedStats(cached.source, cached.value.withColumns(columns), cached.expiresAt, cached.generation));
    }

    private void requestSessions(UUID uuid, String username, String apiKey, long requestGeneration) {
        if (apiKey.isBlank()) return;
        SessionCache cached = freshSessions(uuid);
        if (cached != null) return;
        long now = System.currentTimeMillis();
        SessionRequest existing = sessionsPending.get(uuid);
        if (existing != null && (now - existing.queuedAt > PENDING_TIMEOUT_MS || existing.generation != requestGeneration)) {
            sessionsPending.remove(uuid, existing);
        }
        SessionRequest pending = new SessionRequest(uuid, username, apiKey, requestGeneration, now);
        if (sessionsPending.putIfAbsent(uuid, pending) != null) return;
        if (!sessionQueue.offer(pending)) {
            sessionsPending.remove(uuid, pending);
            LOGGER.debug("Bordic session request skipped because the bounded request queue is full");
            return;
        }
        scheduleSessionFlush();
    }

    private void scheduleSessionFlush() {
        if (!sessionFlushScheduled.compareAndSet(false, true)) return;
        sessionScheduler.schedule(this::flushSessions, 200L, TimeUnit.MILLISECONDS);
    }

    private void flushSessions() {
        try {
            ArrayList<SessionRequest> batch = new ArrayList<>(MAX_BULK);
            for (int index = 0; index < MAX_BULK; index++) {
                SessionRequest next = sessionQueue.poll();
                if (next == null) break;
                batch.add(next);
            }
            LinkedHashMap<String, List<SessionRequest>> byKey = new LinkedHashMap<>();
            for (SessionRequest request : batch) {
                byKey.computeIfAbsent(request.apiKey, ignored -> new ArrayList<>()).add(request);
            }
            for (Map.Entry<String, List<SessionRequest>> group : byKey.entrySet()) {
                fetchSessionBatch(group.getKey(), group.getValue());
            }
        } catch (RuntimeException failure) {
            LOGGER.debug("Bordic session batch failed; snapshots remain unavailable");
        } finally {
            sessionFlushScheduled.set(false);
            if (!sessionQueue.isEmpty()) scheduleSessionFlush();
        }
    }

    private void fetchSessionBatch(String apiKey, List<SessionRequest> requests) {
        EnumMap<SessionPeriod, Map<String, SessionSnapshot>> fetched = new EnumMap<>(SessionPeriod.class);
        for (SessionPeriod period : SessionPeriod.values()) {
            fetched.put(period, fetchSessionPeriod(period, apiKey, requests));
        }
        long now = System.currentTimeMillis();
        for (SessionRequest request : requests) {
            if (request.generation == generation.get()) {
                EnumMap<SessionPeriod, SessionSnapshot> snapshots = new EnumMap<>(SessionPeriod.class);
                String key = normalizedUuid(request.uuid);
                for (SessionPeriod period : SessionPeriod.values()) {
                    snapshots.put(period, fetched.get(period).getOrDefault(key, SessionSnapshot.missing("no response")));
                }
                SessionCache cache = new SessionCache(Collections.unmodifiableMap(snapshots), now + SESSION_TTL_MS, request.generation);
                sessionCache.put(request.uuid, cache);
                applySessionsToStats(request, cache);
            }
            sessionsPending.remove(request.uuid, request);
        }
    }

    private Map<String, SessionSnapshot> fetchSessionPeriod(SessionPeriod period, String apiKey, List<SessionRequest> requests) {
        StringBuilder body = new StringBuilder(64 + requests.size() * 36).append("{\"uuids\":[");
        for (int index = 0; index < requests.size(); index++) {
            if (index > 0) body.append(',');
            body.append('\"').append(normalizedUuid(requests.get(index).uuid)).append('\"');
        }
        body.append("]}");
        ApiResponse response = post(BORDIC_SESSIONS_URL + period.path + "?key=" + encode(apiKey), body.toString(), SESSION_TIMEOUT_MS);
        if (response.code != 200 || response.body == null || !bool(response.body, "success", false)) {
            return Map.of();
        }
        JsonArray sessions = array(response.body, "sessions");
        if (sessions == null) return Map.of();
        LinkedHashMap<String, SessionSnapshot> result = new LinkedHashMap<>();
        for (JsonElement element : sessions) {
            if (!element.isJsonObject()) continue;
            JsonObject session = element.getAsJsonObject();
            String uuid = text(session, "uuid", "").replace("-", "").toLowerCase(Locale.ROOT);
            if (uuid.isEmpty()) continue;
            String name = usernameFor(requests, uuid);
            result.put(uuid, parseSession(session, name));
        }
        return result;
    }

    private void applySessionsToStats(SessionRequest request, SessionCache cache) {
        if (request.generation != generation.get()) return;
        statsCache.compute(request.uuid, (uuid, cached) -> {
            if (request.generation != generation.get()) return cached;
            BedwarsStats source = cached == null ? null : cached.source;
            if (source == null || !source.hasBedwars()) source = withSessionLifetime(source, request.username, cache.snapshots);
            LegacyPlayerStats value;
            if (source != null) {
                value = LegacyPlayerStats.from(source.withUsername(request.username));
                if (cached != null && cached.value.nicked() && !source.hasBedwars()) {
                    value = new LegacyPlayerStats(value.columns(), true, cached.value.nickReason(),
                            cached.value.rankPrefix(), cached.value.provider());
                }
            } else if (cached != null) {
                value = cached.value;
            } else {
                value = LegacyPlayerStats.unavailable(request.username, "unavailable", false, "");
            }
            value = value.withColumns(periodColumns(cache.snapshots));
            CachedTag tag = tagCache.get(uuid);
            if (tag != null && tag.generation == request.generation) value = value.withColumns(tagColumns(tag.info));
            long expiresAt = cached == null ? System.currentTimeMillis() + FAILURE_TTL_MS : cached.expiresAt;
            return new CachedStats(source, value, expiresAt, request.generation);
        });
    }

    private static BedwarsStats withSessionLifetime(
            BedwarsStats profile,
            String username,
            Map<SessionPeriod, SessionSnapshot> snapshots
    ) {
        BedwarsStats lifetime = null;
        for (SessionPeriod period : SessionPeriod.values()) {
            SessionSnapshot snapshot = snapshots.get(period);
            if (snapshot != null && snapshot.ok && snapshot.lifetime != null && snapshot.lifetime.hasBedwars()) {
                lifetime = snapshot.lifetime;
                break;
            }
        }
        if (lifetime == null) return profile;
        lifetime = lifetime.withUsername(username);
        return profile == null ? lifetime : profile.merge(lifetime);
    }

    private static Map<String, String> periodColumns(Map<SessionPeriod, SessionSnapshot> snapshots) {
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        for (SessionPeriod period : SessionPeriod.values()) {
            SessionSnapshot snapshot = snapshots.get(period);
            String prefix = period.path;
            if (snapshot == null || !snapshot.ok) {
                values.put(prefix + "fkdr", "-");
                values.put(prefix + "wlr", "-");
                values.put(prefix + "bblr", "-");
                values.put(prefix + "kdr", "-");
                values.put(prefix + "stars", "-");
            } else {
                values.put(prefix + "fkdr", ratio(snapshot.fkdr));
                values.put(prefix + "wlr", ratio(snapshot.wlr));
                values.put(prefix + "bblr", ratio(snapshot.bblr));
                values.put(prefix + "kdr", ratio(snapshot.kdr));
                values.put(prefix + "stars", snapshot.stars > 0L ? "+" + snapshot.stars : Long.toString(snapshot.stars));
            }
        }
        return values;
    }

    private ApiResponse fetchHypixel(String uuid, String apiKey) {
        return get(HYPIXEL_URL + uuid + "&key=" + encode(apiKey), Map.of(), REQUEST_TIMEOUT_MS);
    }

    private ApiResponse fetchBordicCache(String uuid, String apiKey) {
        String url = apiKey.isBlank() ? BORDIC_CACHE_URL + uuid
                : BORDIC_CACHE_KEY_URL + encode(apiKey) + "&uuid=" + uuid;
        return get(url, Map.of(), REQUEST_TIMEOUT_MS);
    }

    private BedwarsWinstreaks fetchBordicWinstreaks(String uuid) {
        ApiResponse response = get(BORDIC_WINSTREAK_URL + uuid, Map.of(), 3_000);
        if (response.code != 200 || response.body == null || !bool(response.body, "success", false)) return null;
        JsonObject data = object(response.body, "data");
        if (data == null) return null;
        LinkedHashMap<String, Long> modes = new LinkedHashMap<>();
        modes.put("solo", number(data, "eight_one_winstreak", 0L));
        modes.put("doubles", number(data, "eight_two_winstreak", 0L));
        modes.put("threes", number(data, "four_three_winstreak", 0L));
        modes.put("fours", number(data, "four_four_winstreak", 0L));
        modes.put("4v4", number(data, "two_four_winstreak", 0L));
        return new BedwarsWinstreaks(number(data, "winstreak", 0L), modes);
    }

    private ApiResponse get(String url, Map<String, String> headers, int timeoutMs) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .GET();
            headers.forEach(builder::header);
            HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            return new ApiResponse(response.statusCode(), parseObject(response.body()));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return new ApiResponse(0, null);
        } catch (IOException | RuntimeException failure) {
            return new ApiResponse(0, null);
        }
    }

    private ApiResponse post(String url, String jsonBody, int timeoutMs) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "Lazify/1.0.0")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            return new ApiResponse(response.statusCode(), parseObject(response.body()));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return new ApiResponse(0, null);
        } catch (IOException | RuntimeException failure) {
            return new ApiResponse(0, null);
        }
    }

    private static BedwarsStats profile(ApiResponse response, String provider, String username) {
        if (response == null || response.code != 200 || response.body == null || !bool(response.body, "success", false)) {
            return null;
        }
        BedwarsStats stats = BedwarsStats.parse(response.body, provider);
        return stats == null ? null : stats.withUsername(username);
    }

    private Map<String, String> prismHeaders() {
        return Map.of("X-User-Id", prismUserId, "X-Prism-Version", "v1.11.0");
    }

    private SessionCache freshSessions(UUID uuid) {
        SessionCache cached = sessionCache.get(uuid);
        if (cached == null || cached.generation != generation.get()
                || cached.expiresAt <= System.currentTimeMillis()) return null;
        return cached;
    }

    private static SessionSnapshot parseSession(JsonObject session, String username) {
        if (!bool(session, "success", false)) return SessionSnapshot.missing(text(session, "cause", "failed"));
        JsonObject delta = object(session, "delta");
        long finals = number(delta, "final_kills_bedwars", 0L);
        long finalDeaths = number(delta, "final_deaths_bedwars", 0L);
        long wins = number(delta, "wins_bedwars", 0L);
        long losses = number(delta, "losses_bedwars", 0L);
        long bedsBroken = number(delta, "beds_broken_bedwars", 0L);
        long bedsLost = number(delta, "beds_lost_bedwars", 0L);
        long kills = number(delta, "kills_bedwars", 0L);
        long deaths = number(delta, "deaths_bedwars", 0L);
        JsonObject historicalValue = nestedObject(session, "historical", "value");
        JsonObject currentValue = nestedObject(session, "current", "value");
        long historicExperience = number(historicalValue, "Experience", number(historicalValue, "experience", 0L));
        long currentExperience = number(currentValue, "Experience", number(currentValue, "experience", 0L));
        long stars = BedwarsStats.levelFromExperience(currentExperience)
                - BedwarsStats.levelFromExperience(historicExperience);
        BedwarsStats lifetime = currentValue == null ? null : BedwarsStats.fromSession(username, currentValue);
        return new SessionSnapshot(
                true, "", ratioValue(finals, finalDeaths), ratioValue(wins, losses),
                ratioValue(bedsBroken, bedsLost), ratioValue(kills, deaths), stars, lifetime
        );
    }

    private static TagPart coralTag(ApiResponse response) {
        if (response == null || response.code != 200 || response.body == null) return TagPart.unavailable();
        JsonArray tags = array(response.body, "tags");
        if (tags == null) return new TagPart(true, "", "");
        String bestType = "";
        String bestReason = "";
        double bestThreat = 0.0;
        for (JsonElement element : tags) {
            if (!element.isJsonObject()) continue;
            JsonObject tag = element.getAsJsonObject();
            String type = text(tag, "tag_type", text(tag, "type", "")).trim();
            String reason = text(tag, "reason", "");
            if (type.isEmpty() || isCoralNotice(type, reason)) continue;
            double threat = tagThreat(type);
            if (bestType.isEmpty() || threat > bestThreat) {
                bestType = type;
                bestReason = reason;
                bestThreat = threat;
            }
        }
        return new TagPart(true, bestType, bestReason);
    }

    private static Map<String, String> tagColumns(TagInfo info) {
        String value = info.available ? info.display : "-";
        return Map.of("tags", value, "urchin", value);
    }

    private static String tagDisplay(String urchinType, String seraphType) {
        boolean hasUrchin = urchinType != null && !urchinType.isEmpty();
        boolean hasSeraph = seraphType != null && !seraphType.isEmpty();
        if (!hasUrchin && !hasSeraph) return "";
        String urchin = hasUrchin ? urchinTagColor(urchinType) : "";
        String seraph = hasSeraph ? seraphTagColor(seraphType) : "";
        if (hasUrchin && hasSeraph) {
            return (seraph.isEmpty() ? "\u00a7cBL" : seraph) + "\u00a77+" + (urchin.isEmpty() ? "\u00a7cT" : urchin);
        }
        if (!urchin.isEmpty()) return urchin;
        if (!seraph.isEmpty()) return seraph;
        return "\u00a7cT";
    }

    private static String urchinTagColor(String type) {
        String normalized = type.toLowerCase(Locale.ROOT).replace(' ', '_');
        if (normalized.contains("blatant")) return "\u00a7cBC";
        if (normalized.contains("confirmed")) return "\u00a7cC";
        if (normalized.contains("closet")) return "\u00a7eCC";
        if (normalized.contains("sniper")) return "\u00a74S";
        return "";
    }

    private static String seraphTagColor(String type) {
        String mapped = urchinTagColor(type);
        return mapped.isEmpty() ? "\u00a7cBL" : mapped;
    }

    private static double tagThreat(String type) {
        String normalized = type.toLowerCase(Locale.ROOT).replace(' ', '_');
        if (normalized.contains("blatant")) return 4.0;
        if (normalized.contains("confirmed")) return 3.5;
        if (normalized.contains("closet")) return 2.25;
        if (normalized.contains("sniper")) return 1.5;
        return 2.0;
    }

    private static boolean isCoralNotice(String type, String reason) {
        String lowerReason = reason.toLowerCase(Locale.ROOT);
        String normalizedType = type.toLowerCase(Locale.ROOT).replace(' ', '_');
        return lowerReason.contains("urchin api is deprecated")
                || lowerReason.contains("notice for the developer")
                || normalizedType.equals("caution") && lowerReason.contains("migrate to the new api");
    }

    private static boolean isNullPlayerNick(ApiResponse response, boolean offlineUuid) {
        if (response == null || response.body == null) return false;
        if (response.code == 200 && bool(response.body, "success", false)) return object(response.body, "player") == null;
        return offlineUuid && invalidUuidError(response.body);
    }

    private static boolean isPrismNick(ApiResponse response) {
        if (response == null || response.body == null) return false;
        if (bool(response.body, "success", false)) return response.code == 200 && object(response.body, "player") == null;
        String cause = text(response.body, "cause", "").toLowerCase(Locale.ROOT);
        return cause.contains("well-known") || cause.contains("player not stored");
    }

    private static boolean isBordicNick(ApiResponse response, boolean offlineUuid) {
        if (response == null || response.body == null) return false;
        if (bool(response.body, "success", true)) return false;
        String cause = text(response.body, "cause", "");
        if (cause.contains("No data available for that request")) return true;
        return offlineUuid && invalidUuidError(response.body);
    }

    private static boolean invalidUuidError(JsonObject body) {
        String error = (text(body, "error", "") + " " + text(body, "cause", "")).toLowerCase(Locale.ROOT);
        return error.contains("invalid") || error.contains("malformed");
    }

    private static boolean isOfflineUuid(String dashlessUuid) {
        return dashlessUuid != null && dashlessUuid.length() == 32 && dashlessUuid.charAt(12) != '4';
    }

    private static boolean needsRank(BedwarsStats stats) {
        return stats.rank().isBlank() || stats.rank().equalsIgnoreCase("NORMAL")
                || stats.rank().equalsIgnoreCase("NONE");
    }

    private static String usernameFor(List<SessionRequest> requests, String dashlessUuid) {
        for (SessionRequest request : requests) {
            if (normalizedUuid(request.uuid).equals(dashlessUuid)) return request.username;
        }
        return "";
    }

    private static String normalizedUuid(UUID uuid) {
        return uuid.toString().replace("-", "").toLowerCase(Locale.ROOT);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String cleanKey(String key) {
        return key == null ? "" : key.trim();
    }

    private static String yn(boolean value) {
        return value ? "Y" : "N";
    }

    private static String ratio(double value) {
        String result = String.format(Locale.ROOT, "%.2f", value);
        while (result.endsWith("0")) result = result.substring(0, result.length() - 1);
        if (result.endsWith(".")) result = result.substring(0, result.length() - 1);
        return result;
    }

    private static double ratioValue(long numerator, long denominator) {
        double value = denominator > 0L ? (double) numerator / (double) denominator : numerator;
        return Math.round(value * 100.0) / 100.0;
    }

    private static ThreadFactory daemonThreadFactory(String prefix) {
        AtomicInteger nextId = new AtomicInteger();
        return task -> {
            Thread thread = new Thread(task, prefix + "-" + nextId.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    private static JsonObject parseObject(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            JsonElement element = JsonParser.parseString(json);
            return element.isJsonObject() ? element.getAsJsonObject() : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static JsonObject object(JsonObject parent, String key) {
        if (parent == null || !parent.has(key) || !parent.get(key).isJsonObject()) return null;
        return parent.getAsJsonObject(key);
    }

    private static JsonObject nestedObject(JsonObject parent, String first, String second) {
        return object(object(parent, first), second);
    }

    private static JsonArray array(JsonObject parent, String key) {
        if (parent == null || !parent.has(key) || !parent.get(key).isJsonArray()) return null;
        return parent.getAsJsonArray(key);
    }

    private static String text(JsonObject object, String key, String fallback) {
        if (object == null || !object.has(key) || !object.get(key).isJsonPrimitive()) return fallback;
        try {
            return object.get(key).getAsString();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static long number(JsonObject object, String key, long fallback) {
        if (object == null || !object.has(key) || !object.get(key).isJsonPrimitive()) return fallback;
        try {
            return Math.max(0L, object.get(key).getAsLong());
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static boolean bool(JsonObject object, String key, boolean fallback) {
        if (object == null || !object.has(key) || !object.get(key).isJsonPrimitive()) return fallback;
        try {
            return object.get(key).getAsBoolean();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }


    private enum SessionPeriod {
        DAILY("daily"),
        WEEKLY("weekly"),
        MONTHLY("monthly");

        private final String path;
        SessionPeriod(String path) { this.path = path; }
    }

    private record ApiResponse(int code, JsonObject body) {}
    private record Pending(long generation, long startedAt) {}
    private record StatsResult(BedwarsStats profile, boolean nicked, String nickReason) {}
    private record CachedStats(BedwarsStats source, LegacyPlayerStats value, long expiresAt, long generation) {}
    private record TagPart(boolean available, String type, String reason) {
        static TagPart unavailable() { return new TagPart(false, "", ""); }
    }
    private record TagInfo(boolean available, String urchinType, String urchinReason,
                           String seraphType, String seraphReason, String display) {}
    private record CachedTag(TagInfo info, long expiresAt, long generation) {}
    private record SessionRequest(UUID uuid, String username, String apiKey, long generation, long queuedAt) {}
    private record SessionCache(Map<SessionPeriod, SessionSnapshot> snapshots, long expiresAt, long generation) {}
    private record SessionSnapshot(boolean ok, String cause, double fkdr, double wlr, double bblr,
                                   double kdr, long stars, BedwarsStats lifetime) {
        static SessionSnapshot missing(String cause) {
            return new SessionSnapshot(false, cause == null ? "" : cause, 0.0, 0.0, 0.0, 0.0, 0L, null);
        }
    }
    private record BedwarsWinstreaks(long overall, Map<String, Long> modes) {}
}
