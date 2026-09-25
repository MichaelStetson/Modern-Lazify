package com.lazify;

import net.minecraft.client.Minecraft;

import java.util.List;
import java.util.Set;
import java.util.UUID;

final class BedwarsMonitor {
    private final LegacyGameplayTracker tracker = new LegacyGameplayTracker();

    void tick(Minecraft client, LazifyConfig config, PlayerStatsService statsService) {
        tracker.tick(client, config, statsService);
    }

    List<PlayerRow> players() {
        return tracker.players();
    }

    boolean inBedwars() {
        return tracker.inBedwars();
    }

    boolean isLobby() {
        return tracker.isLobby();
    }

    void onMessage(String plainText) {
        tracker.onMessage(plainText);
    }
    void onPartyDetectorPlayerJoin(String playerName) {
        tracker.onPartyDetectorPlayerJoin(playerName);
    }

    boolean shouldHideMessage(String plainText) {
        return tracker.shouldHideMessage(plainText);
    }

    void onWorldChange() {
        tracker.onWorldChange();
    }

    void addManualPlayer(UUID uuid, String name) {
        tracker.addManualPlayer(uuid, name);
    }

    void clearOverlay() {
        tracker.clearOverlay();
    }
    void refreshNow() {
        tracker.refreshNow();
    }

    Set<String> hiddenPlayers() {
        return tracker.hiddenPlayers();
    }

    void hidePlayer(String name) {
        tracker.hidePlayer(name);
    }

    void unhidePlayer(String name) {
        tracker.unhidePlayer(name);
    }

    void clearHiddenPlayers() {
        tracker.clearHiddenPlayers();
    }

    Set<String> partyMembers() {
        return tracker.partyMembers();
    }

    record PlayerRow(UUID uuid, String name, int ping, boolean self, String team, int encounters,
                     LegacyPlayerStats stats, String tag, long joinedAtMillis) {}
}
