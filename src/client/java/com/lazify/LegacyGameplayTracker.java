package com.lazify;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Collection;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
    
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class LegacyGameplayTracker {
    private static final Pattern FORMATTING = Pattern.compile("(?i)\\u00a7[0-9A-FK-ORX]");
    private static final Pattern TEAM_ELIMINATED = Pattern.compile("(?i)^TEAM ELIMINATED\\s*>\\s*(\\w+)\\s+Team\\s+has\\s+been\\s+eliminated!?$");
    private static final Pattern KILL = Pattern.compile("(?i)^([\\w+]{1,16}) .*(?:by|to|for) ([\\w+]{1,16})(?:'s Golem)?\\.?$");
    private static final Pattern BED = Pattern.compile("(?i)^(.+?) Bed (?:was [\\w #']+|has [\\w #']+) by ([\\w+]{1,16})!*$");
    private static final Pattern PLAYER_LINE = Pattern.compile("(?i)^([\\w+]{1,16}) has (?:quit|disconnected|left)(?: the game)?!?$");
    private static final Pattern LOBBY_JOIN = Pattern.compile("^(\\w+) has joined \\((\\d+)/(\\d+)\\)!$");
    private static final Pattern PREGAME_JOIN = Pattern.compile("^\\+ \\((\\d+)/(\\d+)\\) (\\w+)$");
    private static final long COMMAND_DELAY_TICKS = 30;

    private volatile List<BedwarsMonitor.PlayerRow> players = List.of();
    private volatile boolean inBedwars;
    private volatile boolean lobby;
    private volatile Set<String> hidden = Set.of();
    private volatile Set<String> party = Set.of();
    private final Map<String, List<Long>> encounterTimes = new HashMap<>();
    private final Map<String, TeamInfo> teams = new HashMap<>();
    private final Map<String, CombatCounts> combat = new HashMap<>();
    private final Map<String, String> combatNames = new HashMap<>();
    private final Set<String> partyNames = new LinkedHashSet<>();
    private String lobbyId = "";
    private int previousStatus = -1;
    private int ticks;
    private int whoDelay;
    private int partyDelay;
    private boolean whoQueued;
    private boolean partyQueued;
    private boolean parseParty;
    private boolean selfEliminated;
    private boolean teamWiped;
    private long gameStartedAt;
    private final Map<UUID, String> manualPlayers = new HashMap<>();
    private final Map<String, Long> joinedAt = new HashMap<>();
    private final Set<String> finalKilled = new HashSet<>();
    private final Set<String> filteredAnnouncements = new HashSet<>();
    private final Map<String, String> encounterLobby = new HashMap<>();
    private boolean suppressPartyOutput;
    private boolean suppressWhoOutput;
    private final Set<String> whoNames = new LinkedHashSet<>();
    private boolean clearOnWho;
    private boolean teamFkdrSent;
    private boolean teamThreatSent;
    private boolean dodgeWarned;
    private boolean removeFinalKills;
    private boolean resultSent;
    private List<String> gameResult = List.of();
    private boolean gameResultEnabled;
    private boolean overlayCleared;
    private boolean partyDetectorEnabled;
    private boolean partyDetectorPregame;
    private boolean partyDetectorGameStarting;
    private int partyDetectorExpected;
    private int partyDetectorCounter;
    private long partyDetectorLastJoinAt;
    private boolean partyDetectorPing;
    private boolean partyDetectorShowMissed;
    private boolean partyDetectorMissedDone;
    private int partyDetectorMissedTicks;
    private boolean denickEnabled;
    private long denickGeneration;
    private final Set<UUID> activeNickUuids = new HashSet<>();
    private final Map<String, Set<UUID>> nickUuidsByName = new HashMap<>();
    private final Map<UUID, NickEvidence> nickEvidence = new HashMap<>();
    private final Map<UUID, Map<UUID, ResolvedIdentity>> denickCandidates = new HashMap<>();
    private final Map<UUID, ResolvedIdentity> denickedPlayers = new HashMap<>();
    private final Set<String> profileLookupsStarted = new HashSet<>();
    private final Map<UUID, String> statDenickAttempts = new HashMap<>();

    void tick(Minecraft client, LazifyConfig config, PlayerStatsService service) {
        if (client == null || client.player == null || client.level == null || client.getConnection() == null) {
            clearWorldState();
            return;
        }
        denickEnabled = config.getBoolean("denick");
        if (!denickEnabled && (!activeNickUuids.isEmpty() || !nickEvidence.isEmpty()
                || !denickCandidates.isEmpty() || !denickedPlayers.isEmpty())) clearDenickState();
        clearOnWho = config.getBoolean("clearOnWho");
        suppressWhoOutput = config.getBoolean("hideWho");
        tickPartyDetector(client);
        if (++ticks < 5) {
            tickCommands(client);
            return;
        }
        ticks = 0;
        List<String> sidebar = sidebarLines(client);
        int status = status(client, sidebar);
        boolean oldInBedwars = inBedwars;
        inBedwars = status >= 1;
        lobby = status == 1 || status == 2;
        if (!inBedwars) {
            if (oldInBedwars || previousStatus != -1) onWorldChange();
            previousStatus = status;
            tickCommands(client);
            return;
        }
        String detectedLobbyId = extractLobbyId(sidebar);
        boolean lobbyChanged = !lobbyId.equals(detectedLobbyId);
        if (lobbyChanged || previousStatus == 3 && status != 3) clearDenickState();
        if (lobbyChanged) {
            lobbyId = detectedLobbyId;
            joinedAt.clear();
            partyNames.clear();
            party = Set.of();
            partyQueued = false;
            partyDetectorPregame = false;
            partyDetectorGameStarting = false;
            partyDetectorCounter = 0;
            partyDetectorLastJoinAt = 0;
            combat.clear();
            combatNames.clear();
            teams.clear();
            selfEliminated = false;
            teamWiped = false;
            whoNames.clear();
            teamFkdrSent = false;
            teamThreatSent = false;
            dodgeWarned = false;
            gameStartedAt = 0;
            partyDetectorMissedDone = false;
            partyDetectorMissedTicks = 0;
            finalKilled.clear();
        }
        if (status == 3 && previousStatus != 3) {
            combat.clear();
            combatNames.clear();
            selfEliminated = false;
            teamWiped = false;
            teamFkdrSent = false;
            teamThreatSent = false;
            gameStartedAt = System.currentTimeMillis();
            if (config.getBoolean("autoWho") && !whoQueued) {
                whoQueued = true;
                suppressWhoOutput = config.getBoolean("hideWho");
                whoDelay = Math.max(1, (int) (config.getDouble("whoDelay") * 20));
            }
        }
        previousStatus = status;
        removeFinalKills = config.getBoolean("removeFinalKill");
        if (lobby && config.getBoolean("autoPl") && !partyQueued) {
            partyQueued = true;
            suppressPartyOutput = config.getBoolean("hidePl");
            partyDelay = (int) COMMAND_DELAY_TICKS;
        }
        gameResultEnabled = config.getBoolean("gameResultChat");
        partyDetectorPing = config.getBoolean("partyDetectorPing");
        partyDetectorShowMissed = config.getBoolean("partyDetectorShowMissed");
        partyDetectorEnabled = config.getBoolean("partyDetector");
        int mode = detectMode(sidebar);
        partyDetectorExpected = switch (mode) {
            case 2 -> config.getBoolean("partyDetectorBw2s") ? 2 : 0;
            case 3 -> config.getBoolean("partyDetectorBw3s") ? 3 : 0;
            case 4 -> config.getBoolean("partyDetectorBw4s") ? 4 : 0;
            case 5 -> config.getBoolean("partyDetectorBw4v4") ? 4 : 0;
            default -> 0;
        };
        if (status == 2) partyDetectorPregame = true;
        if (status == 3 || partyDetectorGameStarting) {
            partyDetectorCounter = 0;
            partyDetectorMissedDone = true;
            partyDetectorLastJoinAt = 0;
        }
        if (status == 3) {
            partyDetectorPregame = false;
            partyDetectorGameStarting = false;
        }
        if (overlayCleared) {
            players = List.of();
            tickCommands(client);
            return;
        }
        discoverPlayers(client, config, service, status);
        if (denickEnabled) tryStatDenickForActiveNicks(config);
        tickTeamAlerts(client, config, status);
        tickCommands(client);
    }

    List<BedwarsMonitor.PlayerRow> players() { return players; }
    boolean inBedwars() { return inBedwars; }
    boolean isLobby() { return lobby; }
    Set<String> hiddenPlayers() { return hidden; }
    Set<String> partyMembers() { return party; }
    void addManualPlayer(UUID uuid, String name) {
        if (uuid != null && name != null && !name.isBlank()) {
            manualPlayers.put(uuid, name);
            refreshNow();
        }
    }

    void clearOverlay() {
        manualPlayers.clear();
        players = List.of();
        overlayCleared = true;
    }

    void refreshNow() { overlayCleared = false; ticks = 4; }

    void hidePlayer(String name) {
        if (name == null || name.isBlank()) return;
        Set<String> next = new HashSet<>(hidden);
        next.add(name.toLowerCase(Locale.ROOT));
        hidden = Set.copyOf(next);
        refreshNow();
    }

    void unhidePlayer(String name) {
        if (name == null) return;
        Set<String> next = new HashSet<>(hidden);
        next.remove(name.toLowerCase(Locale.ROOT));
        hidden = Set.copyOf(next);
        refreshNow();
    }

    void clearHiddenPlayers() { hidden = Set.of(); refreshNow(); }

    void onWorldChange() {
        players = List.of();
        inBedwars = false;
        lobby = false;
        lobbyId = "";
        clearDenickState();
        previousStatus = -1;
        partyDetectorPregame = false;
        partyDetectorGameStarting = false;
        partyDetectorCounter = 0;
        partyDetectorLastJoinAt = 0;
        partyDetectorMissedDone = false;
        whoNames.clear();
        teamFkdrSent = false;
        teamThreatSent = false;
        dodgeWarned = false;
        partyDetectorMissedTicks = 0;
        teams.clear();
        combat.clear();
        combatNames.clear();
        partyNames.clear();
        party = Set.of();
        whoQueued = false;
        partyQueued = false;
        parseParty = false;
        selfEliminated = false;
        teamWiped = false;
        gameStartedAt = 0;
        joinedAt.clear();
        finalKilled.clear();
        encounterLobby.clear();
        manualPlayers.clear();
        filteredAnnouncements.clear();
        whoDelay = 0;
        partyDelay = 0;
        suppressPartyOutput = false;
        suppressWhoOutput = false;
        resultSent = false;
        gameResult = List.of();
        overlayCleared = false;
    }

    void onMessage(String plainText) {
        if (plainText == null || plainText.isBlank()) return;
        String msg = strip(plainText).trim();
        if (msg.startsWith("ONLINE:")) {
            whoNames.clear();
            if (clearOnWho) {
                String names = msg.substring("ONLINE:".length()).trim();
                for (String name : names.split(",")) {
                    String cleaned = strip(name).trim();
                    if (!cleaned.isEmpty()) whoNames.add(cleaned.toLowerCase(Locale.ROOT));
                }
                players = List.of();
                overlayCleared = false;
                ticks = 4;
            }
        }
        if (msg.startsWith("Party Leader:")) partyNames.clear();
        if (msg.startsWith("Party Leader:") || msg.startsWith("Party Members") || msg.startsWith("Party Moderators")) {
            parseParty = true;
            parsePartyLine(msg);
        } else if (msg.startsWith("-----") && parseParty) {
            parseParty = false;
        } else if (msg.equalsIgnoreCase("You are not currently in a party.")) {
            partyNames.clear();
            party = Set.of();
            parseParty = false;
        } else if (parseParty) {
            parsePartyLine(msg);
        }
        Matcher elimination = TEAM_ELIMINATED.matcher(msg);
        if (inBedwars && msg.equalsIgnoreCase("You have been eliminated!")) selfEliminated = true;
        if (inBedwars && msg.endsWith("FINAL KILL!") && Minecraft.getInstance().player != null) {
            String victim = msg.substring(0, msg.indexOf(' '));
            if (victim.equalsIgnoreCase(Minecraft.getInstance().player.getName().getString())) selfEliminated = true;
            if (removeFinalKills) {
                finalKilled.add(victim.toLowerCase(Locale.ROOT));
                removePlayer(victim);
            }
        }
        if (elimination.matches() && selfTeamMatches(elimination.group(1))) teamWiped = true;
        Matcher join = LOBBY_JOIN.matcher(msg);
        if (join.matches()) markJoin(join.group(1));
        Matcher pregameJoin = PREGAME_JOIN.matcher(msg);
        if (pregameJoin.matches()) markJoin(pregameJoin.group(3));
        if (msg.endsWith(" reconnected.")) markJoin(msg.substring(0, msg.length() - " reconnected.".length()));
        Matcher playerEvent = PLAYER_LINE.matcher(msg);
        if (playerEvent.matches()) removePlayer(playerEvent.group(1));
        if (denickEnabled && inBedwars) recordDenickEvidence(msg);
        recordCombat(msg);
        maybePublishGameResult();
        if (msg.contains("Protect your bed and destroy the enemy beds")) partyDetectorPregame = true;
        if (msg.equals("The game starts in 1 second!")) {
            partyDetectorGameStarting = true;
            partyDetectorCounter = 0;
            partyDetectorLastJoinAt = 0;
        }
    }

    boolean shouldHideMessage(String plainText) {
        if (plainText == null) return false;
        String msg = strip(plainText).trim();
        if (suppressWhoOutput && msg.startsWith("ONLINE: ")) return true;
        boolean hide = suppressPartyOutput && (msg.startsWith("Party Leader:") || msg.startsWith("Party Members")
                || msg.startsWith("Party Moderators") || msg.startsWith("-----")
                || msg.equalsIgnoreCase("You are not currently in a party."));
        if (hide && (msg.startsWith("-----") || msg.equalsIgnoreCase("You are not currently in a party."))) {
            suppressPartyOutput = false;
        }
        return hide;
    }

    private void discoverPlayers(Minecraft client, LazifyConfig config, PlayerStatsService service, int status) {
        List<BedwarsMonitor.PlayerRow> next = new ArrayList<>();
        UUID self = client.player.getUUID();
        long now = System.currentTimeMillis();
        boolean scanTablist = config.getBoolean("autoTablist")
                && !(status == 1 && config.getBoolean("disableInLobby"));
        Collection<PlayerInfo> tabPlayers = client.getConnection().getOnlinePlayers();
        Map<String, List<PlayerInfo>> tabNames = new HashMap<>();
        activeNickUuids.clear();
        nickUuidsByName.clear();
        for (PlayerInfo info : tabPlayers) {
            if (info.getProfile() == null || info.getProfile().id() == null) continue;
            String profileName = info.getProfile().name();
            if (profileName != null && !profileName.isBlank()) {
                tabNames.computeIfAbsent(profileName.toLowerCase(Locale.ROOT), ignored -> new ArrayList<>()).add(info);
            }
        }
        if (scanTablist || !whoNames.isEmpty()) {
            for (PlayerInfo info : tabPlayers) {
                if (info.getProfile() == null || info.getProfile().id() == null) continue;
                UUID uuid = info.getProfile().id();
                String name = info.getProfile().name();
                if (name == null || name.isBlank()) continue;
                String normalized = name.toLowerCase(Locale.ROOT);
                if (!scanTablist && !whoNames.contains(normalized)) continue;
                if (finalKilled.contains(normalized) || hidden.contains(normalized)) continue;
                boolean isSelf = uuid.equals(self);
                if (isSelf) {
                    TeamInfo selfTeam = config.getBoolean("teams")
                            ? teamFor(client, name, info, config.getBoolean("teamPrefix")) : null;
                    if (selfTeam != null) teams.put(uuid.toString(), selfTeam);
                    if (!config.getBoolean("showYourself")) continue;
                }
                if (denickEnabled && !isV4Uuid(uuid)) observeNick(info, name, tabNames);
                next.add(makeRow(client, config, service, uuid, name, info.getLatency(), isSelf, now));
            }
        }
        pruneInactiveDenicks();
        if (denickEnabled && !config.bordicApiKey().isBlank() && status >= 2) scanNickWood(client);
        for (Map.Entry<UUID, String> entry : manualPlayers.entrySet()) {
            UUID uuid = entry.getKey();
            if (next.stream().anyMatch(row -> row.uuid().equals(uuid))
                    || hidden.contains(entry.getValue().toLowerCase(Locale.ROOT))) continue;
            next.add(makeRow(client, config, service, uuid, entry.getValue(), 0, uuid.equals(self), now));
        }
        
        int sortBy = config.getInt("sortByIndex");
        boolean descending = config.getInt("sortMode") == 0;
        Comparator<BedwarsMonitor.PlayerRow> sort = switch (sortBy) {
            case 0 -> Comparator.comparingInt(BedwarsMonitor.PlayerRow::encounters);
            case 1 -> Comparator.comparingDouble(row -> statNumber(row, "star"));
            case 3, 5 -> Comparator.comparingLong(BedwarsMonitor.PlayerRow::joinedAtMillis);
            case 4 -> Comparator.comparingDouble(row -> statNumber(row, "winstreaks"));
            default -> Comparator.comparingDouble(row -> statNumber(row, "fkdr"));
        };
        if (sortBy != 3 && sortBy != 5 && descending) sort = sort.reversed();
        next.sort(Comparator.comparing(BedwarsMonitor.PlayerRow::self).reversed().thenComparing(sort)
                .thenComparing(BedwarsMonitor.PlayerRow::name, String.CASE_INSENSITIVE_ORDER));
        if (config.getBoolean("statFilter")) {
            double minFkdr = config.getDouble("statFilterMinFkdr");
            int minStars = config.getInt("statFilterMinStars");
            if (minFkdr > 0 || minStars > 0) {
                List<BedwarsMonitor.PlayerRow> retained = new ArrayList<>(next.size());
                for (BedwarsMonitor.PlayerRow row : next) {
                    if (row.self() || row.stats() == null) {
                        retained.add(row);
                        continue;
                    }
                    String fkdrText = row.stats().column("fkdr");
                    String starsText = row.stats().column("star");
                    boolean fkdrKnown = minFkdr <= 0 || isNumeric(fkdrText);
                    boolean starsKnown = minStars <= 0 || isNumeric(starsText);
                    if (!fkdrKnown || !starsKnown) {
                        retained.add(row);
                        continue;
                    }
                    double fkdr = statNumber(row, "fkdr");
                    double stars = statNumber(row, "star");
                    boolean matches = minFkdr > 0 && fkdr >= minFkdr || minStars > 0 && stars >= minStars;
                    if (matches) {
                        retained.add(row);
                        if (config.getBoolean("statFilterChat") && filteredAnnouncements.add(row.uuid().toString())) {
                            client.player.sendSystemMessage(Component.literal("Stat filter: " + row.name()
                                    + " matches (FKDR " + fkdrText + ", stars " + starsText + ")."));
                        }
                    }
                }
                next = retained;
            }
        }
        int maxRows = config.getInt("maxRows");
        if (next.size() > maxRows) {
            BedwarsMonitor.PlayerRow selfRow = next.stream().filter(BedwarsMonitor.PlayerRow::self).findFirst().orElse(null);
            next = new ArrayList<>(next.subList(0, maxRows));
            if (selfRow != null && !next.contains(selfRow)) next.set(next.size() - 1, selfRow);
        }
        
        players = List.copyOf(next);
    }

    private BedwarsMonitor.PlayerRow makeRow(Minecraft client, LazifyConfig config, PlayerStatsService service,
                                               UUID uuid, String name, int ping, boolean self, long now) {
        String key = uuid.toString();
        PlayerInfo info = client.getConnection().getPlayerInfo(uuid);
        TeamInfo teamInfo = config.getBoolean("teams") || config.getBoolean("teamFkdrChat")
                || config.getBoolean("teamThreatChat")
                ? teamFor(client, name, info, config.getBoolean("teamPrefix")) : null;
        if (teamInfo != null) teams.put(key, teamInfo);
        List<Long> seen = encounterTimes.computeIfAbsent(key, ignored -> new ArrayList<>());
        long ttl = Math.max(0, config.getInt("encountersTimeoutMins")) * 60_000L;
        seen.removeIf(time -> now - time > ttl);
        if (seen.isEmpty() || !lobbyId.equals(encounterLobby.get(key))) {
            seen.add(now);
            encounterLobby.put(key, lobbyId);
        }
        long joined = joinedAt.computeIfAbsent(key, ignored -> now);
        ResolvedIdentity identity = denickEnabled ? denickedPlayers.get(uuid) : null;
        UUID statsUuid = identity == null ? uuid : identity.uuid;
        String statsName = identity == null ? name : identity.name;
        service.request(statsUuid, statsName, config.hypixelApiKey(), config.bordicApiKey(),
                config.urchinApiKey(), config.seraphApiKey());
        LegacyPlayerStats stats = service.stats(statsUuid);
        if (identity != null && stats != null) {
            stats = new LegacyPlayerStats(stats.columns(), true, "denicked", stats.rankPrefix(), stats.provider());
        }
        return new BedwarsMonitor.PlayerRow(uuid, name, ping, self, teamInfo == null ? "" : teamInfo.color,
                seen.size(), stats, service.tag(statsUuid), joined);
    }
    private void observeNick(PlayerInfo info, String name, Map<String, List<PlayerInfo>> tabNames) {
        UUID nickUuid = info.getProfile().id();
        activeNickUuids.add(nickUuid);
        String normalized = name.toLowerCase(Locale.ROOT);
        nickUuidsByName.computeIfAbsent(normalized, ignored -> new HashSet<>()).add(nickUuid);
        nickEvidence.computeIfAbsent(nickUuid, ignored -> new NickEvidence());

        String skinName = LegacyDenickSupport.skinProfileName(info);
        if (skinName != null) {
            queueProfileLookup(nickUuid, "skin|" + skinName.toLowerCase(Locale.ROOT),
                    LegacyDenickSupport.profileByName(skinName));
        }

        Set<UUID> realUuids = new HashSet<>();
        for (PlayerInfo sameName : tabNames.getOrDefault(normalized, List.of())) {
            UUID candidate = sameName.getProfile().id();
            if (!candidate.equals(nickUuid) && isV4Uuid(candidate)) realUuids.add(candidate);
        }
        if (realUuids.size() == 1) {
            UUID realUuid = realUuids.iterator().next();
            queueProfileLookup(nickUuid, "tab|" + realUuid,
                    LegacyDenickSupport.profileByUuid(realUuid));
        }
    }

    private void queueProfileLookup(UUID nickUuid, String evidenceKey,
                                    java.util.concurrent.CompletableFuture<LegacyDenickSupport.ResolvedProfile> lookup) {
        String requestKey = nickUuid + "|" + evidenceKey;
        if (!profileLookupsStarted.add(requestKey)) return;
        long generation = denickGeneration;
        String scopeLobby = lobbyId;
        Minecraft client = Minecraft.getInstance();
        lookup.whenComplete((profile, failure) -> client.execute(() -> {
            if (failure != null || profile == null || !denickEnabled || generation != denickGeneration
                    || !scopeLobby.equals(lobbyId) || !activeNickUuids.contains(nickUuid)) return;
            addDenickCandidate(nickUuid, new ResolvedIdentity(profile.uuid(), profile.name()));
        }));
    }

    private void recordDenickEvidence(String msg) {
        for (Map.Entry<String, Set<UUID>> entry : nickUuidsByName.entrySet()) {
            if (entry.getValue().size() != 1) continue;
            UUID nickUuid = entry.getValue().iterator().next();
            if (!activeNickUuids.contains(nickUuid)) continue;
            Integer star = LegacyDenickSupport.extractStar(msg, entry.getKey());
            if (star != null && star > 0) {
                NickEvidence evidence = nickEvidence.computeIfAbsent(nickUuid, ignored -> new NickEvidence());
                evidence.star = star;
            }
        }

        if (previousStatus < 2) return;
        LegacyDenickSupport.KillMatch match = LegacyDenickSupport.detectKillMessage(msg);
        if (match == null || match.killer() == null || match.killer().isBlank()) return;
        Set<UUID> candidates = nickUuidsByName.get(match.killer().toLowerCase(Locale.ROOT));
        if (candidates == null || candidates.size() != 1) return;
        UUID nickUuid = candidates.iterator().next();
        if (!activeNickUuids.contains(nickUuid)) return;
        NickEvidence evidence = nickEvidence.computeIfAbsent(nickUuid, ignored -> new NickEvidence());
        if (match.packageId() != null && !match.packageId().isEmpty()
                && (!"stat".equals(match.packageId()) || evidence.packageId == null || "stat".equals(evidence.packageId))) {
            evidence.packageId = match.packageId();
        }
        if (match.observedFinals() != null) evidence.finals = match.observedFinals();
        if (match.observedBeds() != null) evidence.beds = match.observedBeds();
    }

    private void scanNickWood(Minecraft client) {
        for (Player player : client.level.players()) {
            UUID uuid = player.getUUID();
            if (!activeNickUuids.contains(uuid)) continue;
            String wood = woodType(player.getMainHandItem());
            if (wood == null) continue;
            NickEvidence evidence = nickEvidence.computeIfAbsent(uuid, ignored -> new NickEvidence());
            if (evidence.wood == null) evidence.wood = wood;
        }
    }

    private static String woodType(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        Item item = stack.getItem();
        if (item == Items.OAK_PLANKS) return "woodSkin_oak";
        if (item == Items.SPRUCE_PLANKS) return "woodSkin_spruce";
        if (item == Items.BIRCH_PLANKS) return "woodSkin_birch";
        if (item == Items.JUNGLE_PLANKS) return "woodSkin_jungle";
        if (item == Items.ACACIA_PLANKS) return "woodSkin_acacia";
        if (item == Items.DARK_OAK_PLANKS) return "woodSkin_dark_oak";
        if (item == Items.OAK_LOG) return "woodSkin_oak_log";
        if (item == Items.SPRUCE_LOG) return "woodSkin_spruce_log";
        if (item == Items.BIRCH_LOG) return "woodSkin_birch_log";
        if (item == Items.JUNGLE_LOG) return "woodSkin_jungle_log";
        if (item == Items.ACACIA_LOG) return "woodSkin_acacia_log";
        if (item == Items.DARK_OAK_LOG) return "woodSkin_dark_oak_log";
        return null;
    }

    private void tryStatDenickForActiveNicks(LazifyConfig config) {
        String apiKey = config.bordicApiKey();
        if (apiKey == null || apiKey.isBlank()) return;
        String keyFingerprint = LegacyDenickSupport.keyFingerprint(apiKey);
        for (UUID nickUuid : activeNickUuids) {
            NickEvidence evidence = nickEvidence.get(nickUuid);
            if (evidence == null || evidence.packageId == null || evidence.finals == null || evidence.beds == null) continue;
            String attempt = keyFingerprint + '|' + evidence.packageId + '|' + evidence.finals + '|'
                    + evidence.beds + '|' + evidence.star + '|' + evidence.wood;
            if (attempt.equals(statDenickAttempts.put(nickUuid, attempt))) continue;
            String scopeLobby = lobbyId;
            long generation = denickGeneration;
            LegacyDenickSupport.findBordicMatch(apiKey, evidence.packageId, evidence.finals,
                    evidence.beds, evidence.wood, evidence.star).whenComplete((result, failure) -> {
                if (failure != null || result == null || result.ambiguous()) return;
                Minecraft client = Minecraft.getInstance();
                client.execute(() -> {
                    if (!denickEnabled || generation != denickGeneration || !scopeLobby.equals(lobbyId)
                            || !activeNickUuids.contains(nickUuid) || !nickEvidence.containsKey(nickUuid)
                            || !attempt.equals(statDenickAttempts.get(nickUuid))) return;
                    LegacyDenickSupport.BordicPlayer player = result.player();
                    UUID realUuid = LegacyDenickSupport.parseUuid(player.uuid());
                    if (realUuid == null || realUuid.version() != 4) return;
                    addDenickCandidate(nickUuid, new ResolvedIdentity(realUuid, player.name()));
                });
            });
        }
    }

    private void addDenickCandidate(UUID nickUuid, ResolvedIdentity identity) {
        if (identity.uuid == null || identity.uuid.version() != 4 || identity.name == null || identity.name.isBlank()) return;
        Map<UUID, ResolvedIdentity> candidates = denickCandidates.computeIfAbsent(nickUuid, ignored -> new HashMap<>());
        candidates.put(identity.uuid, identity);
        if (candidates.size() == 1) {
            ResolvedIdentity resolved = candidates.values().iterator().next();
            if (!resolved.equals(denickedPlayers.put(nickUuid, resolved))) refreshNow();
        } else if (denickedPlayers.remove(nickUuid) != null) {
            statDenickAttempts.remove(nickUuid);
            refreshNow();
        }
    }

    private void pruneInactiveDenicks() {
        nickEvidence.keySet().removeIf(uuid -> !activeNickUuids.contains(uuid));
        denickCandidates.keySet().removeIf(uuid -> !activeNickUuids.contains(uuid));
        denickedPlayers.keySet().removeIf(uuid -> !activeNickUuids.contains(uuid));
        statDenickAttempts.keySet().removeIf(uuid -> !activeNickUuids.contains(uuid));
        profileLookupsStarted.removeIf(key -> {
            int separator = key.indexOf('|');
            if (separator <= 0) return true;
            try { return !activeNickUuids.contains(UUID.fromString(key.substring(0, separator))); }
            catch (IllegalArgumentException malformed) { return true; }
        });
    }

    private void clearDenickState() {
        denickGeneration++;
        activeNickUuids.clear();
        nickUuidsByName.clear();
        nickEvidence.clear();
        denickCandidates.clear();
        denickedPlayers.clear();
        profileLookupsStarted.clear();
        statDenickAttempts.clear();
    }

    private static boolean isV4Uuid(UUID uuid) {
        return uuid != null && uuid.version() == 4;
    }

    private static TeamInfo teamFor(Minecraft client, String name, PlayerInfo info, boolean showPrefix) {
        if (client.level == null) return null;
        PlayerTeam team = client.level.getScoreboard().getPlayersTeam(name);
        if (team == null) return null;
        Component prefix = team.getPlayerPrefix();
        Component suffix = team.getPlayerSuffix();
        String formatted = showPrefix && prefix != null ? prefix.getString() + name
                + (suffix == null ? "" : suffix.getString()) : name;
        String display = info == null || info.getTabListDisplayName() == null ? name : info.getTabListDisplayName().getString();
        if (!strip(display).equalsIgnoreCase(name)) formatted = display;
        return new TeamInfo(team.getName(), formatted,
                team.getColor().map(color -> color.getSerializedName()).orElse("white"));

    }
    private static double statNumber(BedwarsMonitor.PlayerRow row, String column) {
        if (row.stats() == null) return 0;
        try {
            return Double.parseDouble(row.stats().column(column)
                    .replaceAll("(?i)\\u00a7[0-9A-FK-ORX]", "").replace(",", "").trim());
        } catch (RuntimeException ignored) {
            return 0;
        }
    }
    private static boolean isNumeric(String value) {
        if (value == null || value.isBlank() || value.equals("-")) return false;
        try {
            return Double.isFinite(Double.parseDouble(value
                    .replaceAll("(?i)\\u00a7[0-9A-FK-ORX]", "").replace(",", "").trim()));
        } catch (NumberFormatException ignored) {
            return false;
        }
    }
    private void tickTeamAlerts(Minecraft client, LazifyConfig config, int status) {
        if (lobby && config.getBoolean("dodgeWarning") && !dodgeWarned) {
            Set<String> excluded = new HashSet<>(partyNames);
            excluded.add(client.player.getUUID().toString().replace("-", ""));
            double total = 0;
            int count = 0;
            for (BedwarsMonitor.PlayerRow row : players) {
                if (excluded.contains(row.uuid().toString().replace("-", "")) || row.stats() == null) continue;
                String fkdr = row.stats().column("fkdr");
                if (!isNumeric(fkdr)) continue;
                total += statNumber(row, "fkdr");
                count++;
            }
            double threshold = config.getDouble("dodgeThreshold");
            if (count >= 2 && total / count >= threshold) {
                dodgeWarned = true;
                client.player.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                        "Lobby dodge warning! Avg FKDR: %.2f (threshold: %.2f)", total / count, threshold)));
            }
        }
        if (status != 3 || gameStartedAt == 0
                || System.currentTimeMillis() - gameStartedAt < 500) return;
        if (!teamFkdrSent && config.getBoolean("teamFkdrChat")) {
            Map<String, List<Double>> averages = new LinkedHashMap<>();
            for (BedwarsMonitor.PlayerRow row : players) {
                if (row.stats() == null || !isNumeric(row.stats().column("fkdr"))) continue;
                averages.computeIfAbsent(row.team().isBlank() ? "?" : row.team(), ignored -> new ArrayList<>())
                        .add(statNumber(row, "fkdr"));
            }
            if (!averages.isEmpty()) {
                List<String> teams = averages.entrySet().stream()
                        .sorted(Map.Entry.<String, List<Double>>comparingByValue(
                                Comparator.comparingDouble(values -> values.stream().mapToDouble(Double::doubleValue)
                                        .average().orElse(0))).reversed())
                        .map(entry -> formatTeam(entry.getKey()) + ": "
                                + String.format(Locale.ROOT, "%.2f",
                                entry.getValue().stream().mapToDouble(Double::doubleValue).average().orElse(0)))
                        .toList();
                sendCommand(client, "pc Team FKDRs: " + String.join(" | ", teams));
                teamFkdrSent = true;
            }
        }
        if (!teamThreatSent && config.getBoolean("teamThreatChat")) {
            sendTeamThreat(client, config);
        }
    }

    private void sendTeamThreat(Minecraft client, LazifyConfig config) {
        String selfTeam = players.stream().filter(BedwarsMonitor.PlayerRow::self)
                .map(BedwarsMonitor.PlayerRow::team).filter(team -> !team.isBlank()).findFirst().orElse("");
        Map<String, ThreatAccumulator> threats = new LinkedHashMap<>();
        for (BedwarsMonitor.PlayerRow row : players) {
            String team = row.team();
            if (team.isBlank() || team.equals(selfTeam) || row.stats() == null) continue;
            LegacyPlayerStats stats = row.stats();
            double score = Math.min(15, statNumber(row, "fkdr")) * config.getDouble("threatFkdrWeight")
                    + Math.min(10, statNumber(row, "star") / 100) * config.getDouble("threatStarWeight")
                    + Math.min(8, statNumber(row, "winstreaks") / 25) * config.getDouble("threatWinstreakWeight")
                    + Math.min(5, row.encounters()) * config.getDouble("threatEncounterWeight");
            boolean tagged = row.tag() != null && !row.tag().isBlank();
            if (tagged) score += config.getDouble("threatUrchinWeight");
            boolean nicked = stats.nicked();
            if (nicked) score += config.getDouble("threatNickWeight");
            threats.computeIfAbsent(team, ignored -> new ThreatAccumulator())
                    .add(score, tagged, nicked);
        }
        if (threats.size() < 2) return;
        List<Map.Entry<String, ThreatAccumulator>> ranked = threats.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue().score(config.getDouble("threatTeamSizeWeight")),
                        a.getValue().score(config.getDouble("threatTeamSizeWeight")))).toList();
        double threshold = config.getDouble("teamThreatThreshold");
        double top = ranked.getFirst().getValue().score(config.getDouble("threatTeamSizeWeight"));
        if (top < threshold) return;
        String message = ranked.stream().limit(4).map(entry -> {
            ThreatAccumulator threat = entry.getValue();
            double score = threat.score(config.getDouble("threatTeamSizeWeight"));
            String level = score >= threshold * 1.75 ? "EXTREME"
                    : score >= threshold * 1.3 ? "HIGH"
                    : score >= threshold ? "ELEVATED" : score >= threshold * .65 ? "MEDIUM" : "LOW";
            return formatTeam(entry.getKey()) + " " + level + " "
                    + String.format(Locale.ROOT, "%.2f", score) + " [" + threat.players + "p"
                    + (threat.tagged > 0 ? ", U" + threat.tagged : "")
                    + (threat.nicked > 0 ? ", N" + threat.nicked : "") + "]";
        }).reduce((left, right) -> left + " | " + right).orElse("");
        if (!message.isEmpty()) {
            sendCommand(client, "pc Threat: " + message);
            teamThreatSent = true;
        }
    }

    private static String formatTeam(String team) {
        return team.isEmpty() ? "?" : Character.toUpperCase(team.charAt(0))
                + team.substring(1).toLowerCase(Locale.ROOT);
    }


    private void tickPartyDetector(Minecraft client) {
        if (!partyDetectorEnabled || !partyDetectorPregame || partyDetectorGameStarting
                || !partyDetectorShowMissed || partyDetectorExpected <= 0 || partyDetectorMissedDone) return;
        if (++partyDetectorMissedTicks < 20) return;
        partyDetectorMissedDone = true;
        int missed = Math.max(0, partyDetectorExpected - partyDetectorCounter);
        if (missed > 0 && client.player != null) {
            client.player.sendSystemMessage(Component.literal(
                    "PartyDetector: Missed " + missed + " player" + (missed == 1 ? "" : "s") + "."));
        }
    }

    private void tickCommands(Minecraft client) {
        if (whoDelay > 0 && --whoDelay == 0) sendCommand(client, "who");
        if (partyDelay > 0 && --partyDelay == 0) sendCommand(client, "pl");
    }

    private static void sendCommand(Minecraft client, String command) {
        client.execute(() -> {
            if (client.player != null && client.player.connection != null) client.player.connection.sendCommand(command);
        });
    }

    private int status(Minecraft client, List<String> sidebar) {
        Objective objective = client.level.getScoreboard().getDisplayObjective(DisplaySlot.SIDEBAR);
        String title = objective == null ? "" : strip(objective.getDisplayName().getString()).trim();
        if (!title.toUpperCase(Locale.ROOT).startsWith("BED WARS")) return -1;
        String id = extractLobbyId(sidebar);
        if (id.startsWith("L")) return 1;
        for (String raw : sidebar) {
            String line = strip(raw).trim();
            if (line.equals("Waiting...") || line.startsWith("Starting in")) return 2;
            if (isTeamScore(line)) return 3;
        }
        return -1;
    }

    private static List<String> sidebarLines(Minecraft client) {
        Objective objective = client.level.getScoreboard().getDisplayObjective(DisplaySlot.SIDEBAR);
        if (objective == null) return List.of();
        List<PlayerScoreEntry> scores = client.level.getScoreboard().listPlayerScores(objective).stream()
                .sorted(Comparator.comparingInt(PlayerScoreEntry::value).reversed()).toList();
        List<String> lines = new ArrayList<>(scores.size());
        for (PlayerScoreEntry score : scores) {
            PlayerTeam team = client.level.getScoreboard().getPlayersTeam(score.owner());
            String prefix = team == null || team.getPlayerPrefix() == null ? "" : team.getPlayerPrefix().getString();
            String suffix = team == null || team.getPlayerSuffix() == null ? "" : team.getPlayerSuffix().getString();
            lines.add(prefix + score.owner() + suffix);
        }
        return lines;
    }

    private static int detectMode(List<String> sidebar) {
        for (String raw : sidebar) {
            String line = strip(raw).toLowerCase(Locale.ROOT).trim();
            if (line.equals("4v4") || line.contains("2 x 4") || line.contains("2x4")) return 5;
            if (line.equals("solo") || line.equals("solos") || line.endsWith(" solo")) return 1;
            if (line.equals("doubles") || line.endsWith(" doubles")) return 2;
            if (line.equals("threes") || line.contains("3v3v3v3")) return 3;
            if (line.equals("fours") || line.contains("4v4v4v4")) return 4;
        }
        return 0;
    }

    private static String extractLobbyId(List<String> lines) {
        if (lines == null) return "";
        for (String raw : lines) {
            Matcher matcher = Pattern.compile("\\b(L\\d+)\\b").matcher(strip(raw).trim());
            if (matcher.find()) return matcher.group(1);
        }
        return "";
    }

    private static boolean isTeamScore(String line) {
        return line.matches("(?i)^.{1,3}\\s+(?:Red|Blue|Green|Yellow|Aqua|White|Pink|Gray):.*$");
    }

    private static String strip(String text) {
        return FORMATTING.matcher(text).replaceAll("");
    }

    private void addPartyNames(String text) {
        Minecraft client = Minecraft.getInstance();
        if (text == null || client.getConnection() == null) return;
        String roster = strip(text);
        for (PlayerInfo info : client.getConnection().getOnlinePlayers()) {
            if (info.getProfile() == null || info.getProfile().id() == null) continue;
            String name = info.getProfile().name();
            if (name != null && Pattern.compile("(?i)(?<![A-Za-z0-9_])" + Pattern.quote(name)
                    + "(?![A-Za-z0-9_])").matcher(roster).find()) {
                partyNames.add(info.getProfile().id().toString().replace("-", ""));
            }
        }
        party = Set.copyOf(partyNames);
    }

    private void parsePartyLine(String line) {
        int colon = line.indexOf(':');
        if (colon < 0) return;
        addPartyNames(line.substring(colon + 1).replace("●", " "));
    }
    private void removePlayer(String name) {
        players = players.stream().filter(row -> !row.name().equalsIgnoreCase(name)).toList();
        String normalized = name.toLowerCase(Locale.ROOT);
        Set<UUID> removedNicks = nickUuidsByName.remove(normalized);
        if (removedNicks != null) for (UUID uuid : removedNicks) removeNickState(uuid);
    }

    private void removeNickState(UUID uuid) {
        activeNickUuids.remove(uuid);
        nickEvidence.remove(uuid);
        denickCandidates.remove(uuid);
        denickedPlayers.remove(uuid);
        statDenickAttempts.remove(uuid);
        profileLookupsStarted.removeIf(key -> key.startsWith(uuid + "|"));
    }
    private void markJoin(String name) {
        Minecraft client = Minecraft.getInstance();
        if (client.getConnection() == null) return;
        for (PlayerInfo info : client.getConnection().getOnlinePlayers()) {
            if (info.getProfile() == null || !info.getProfile().name().equalsIgnoreCase(name)) continue;
            String key = info.getProfile().id().toString();
            joinedAt.put(key, System.currentTimeMillis());
            finalKilled.remove(name.toLowerCase(Locale.ROOT));
            partyDetectorPregame = true;
            break;
        }
    }
    void onPartyDetectorPlayerJoin(String playerName) {
        Minecraft client = Minecraft.getInstance();
        if (!partyDetectorEnabled || !partyDetectorPregame || partyDetectorGameStarting || partyDetectorExpected <= 0
                || playerName == null || playerName.isBlank() || client.player == null
                || playerName.equalsIgnoreCase(client.player.getName().getString())) return;
        long now = System.currentTimeMillis();
        if (partyDetectorCounter > 0 && now - partyDetectorLastJoinAt > 1000) {
            partyDetectorCounter = 0;
            partyDetectorLastJoinAt = 0;
            return;
        }
        if (partyDetectorCounter == 0) partyDetectorLastJoinAt = now;
        partyDetectorCounter++;
        if (partyDetectorCounter >= partyDetectorExpected) {
            client.player.sendSystemMessage(Component.literal(
                    "PartyDetector: A party of " + partyDetectorCounter + " joined."));
            partyDetectorCounter = 0;
            if (partyDetectorPing) client.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f, 1.2f);
            partyDetectorLastJoinAt = 0;
        }
    }

    private void recordCombat(String msg) {
        if (!inBedwars) return;
        boolean isFinal = msg.endsWith("FINAL KILL!");
        String body = isFinal ? msg.substring(0, msg.length() - "FINAL KILL!".length()).trim() : msg;
        Matcher bed = BED.matcher(body);
        String killer = null;
        Combat type = null;
        if (bed.matches()) { killer = bed.group(2); type = Combat.BED; }
        else {
            Matcher kill = KILL.matcher(body);
            if (kill.matches()) { killer = kill.group(2); type = isFinal ? Combat.FINAL : Combat.KILL; }
        }
        if (killer == null || type == null) return;
        String key = findUuid(killer);
        combatNames.put(key, killer);
        int[] counts = combat.computeIfAbsent(key, ignored -> new CombatCounts()).values;
        if (type == Combat.BED) counts[2]++;
        else { counts[0]++; if (isFinal) counts[1]++; }
    }

    private String findUuid(String name) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null && client.player.getName().getString().equalsIgnoreCase(name)) {
            return client.player.getUUID().toString();
        }
        for (BedwarsMonitor.PlayerRow row : players) {
            if (row.name().equalsIgnoreCase(name)) return row.uuid().toString();
        }
        return "name:" + name.toLowerCase(Locale.ROOT);
    }

    private boolean selfTeamMatches(String eliminated) {
        String selfTeam = selfTeamKey();
        return selfTeam != null && selfTeam.toLowerCase(Locale.ROOT).contains(eliminated.toLowerCase(Locale.ROOT));
    }

    private String selfTeamKey() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return null;
        TeamInfo tracked = teams.get(client.player.getUUID().toString());
        if (tracked != null) return tracked.name;
        if (client.level == null) return null;
        PlayerTeam team = client.level.getScoreboard().getPlayersTeam(client.player.getName().getString());
        return team == null ? null : team.getName();
    }
    private void maybePublishGameResult() {
        if (!gameResultEnabled || resultSent || !selfEliminated || !teamWiped || gameStartedAt == 0
                || System.currentTimeMillis() - gameStartedAt < 30_000L) return;
        resultSent = true;
        List<Map.Entry<String, CombatCounts>> result = new ArrayList<>(combat.entrySet());
        result.sort(Comparator.<Map.Entry<String, CombatCounts>>comparingInt(entry -> entry.getValue().values[1])
                .reversed().thenComparing(Comparator.comparingInt(
                        (Map.Entry<String, CombatCounts> entry) -> entry.getValue().values[0]).reversed()));
        List<String> summary = new ArrayList<>();
        String selfTeam = selfTeamKey();
        String selfUuid = Minecraft.getInstance().player == null ? "" : Minecraft.getInstance().player.getUUID().toString();
        for (Map.Entry<String, CombatCounts> entry : result) {
            TeamInfo playerTeam = teams.get(entry.getKey());
            if (selfTeam == null ? !entry.getKey().equals(selfUuid)
                    : playerTeam == null || !selfTeam.equals(playerTeam.name)) continue;
            int[] counts = entry.getValue().values;
            summary.add(combatNames.getOrDefault(entry.getKey(), entry.getKey()) + ": " + counts[0]
                    + " kills, " + counts[1] + " finals, " + counts[2] + " beds");
        }
        gameResult = List.copyOf(summary);
        Minecraft client = Minecraft.getInstance();
        if (gameResultEnabled && client.player != null) {
            client.execute(() -> gameResult.forEach(line ->
                    client.player.sendSystemMessage(Component.literal(line))));
        }
    }

    private void clearWorldState() {
        if (inBedwars || !players.isEmpty() || !activeNickUuids.isEmpty() || !nickEvidence.isEmpty()) onWorldChange();
    }

    private record ResolvedIdentity(UUID uuid, String name) {}
    private static final class NickEvidence {
        String packageId;
        Integer finals;
        Integer beds;
        Integer star;
        String wood;
    }
    private enum Combat { KILL, FINAL, BED }
    private record TeamInfo(String name, String formatted, String color) {}
    private static final class ThreatAccumulator {
        double total;
        int players;
        int tagged;
        int nicked;

        void add(double score, boolean hasTag, boolean isNicked) {
            total += score;
            players++;
            if (hasTag) tagged++;
            if (isNicked) nicked++;
        }

        double score(double teamSizeWeight) {
            return total / players + Math.max(0, players - 1) * teamSizeWeight;
        }
    }
    private static final class CombatCounts { final int[] values = new int[3]; }
}
