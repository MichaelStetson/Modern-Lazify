package com.lazify;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public final class LazifyClient implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("lazify");
    private static final Identifier CATEGORY_ID = Identifier.fromNamespaceAndPath("lazify", "main");
    private static LazifyClient instance;

    private final LazifyConfig config = new LazifyConfig();
    private final PlayerStatsService statsService = new PlayerStatsService();
    private final BedwarsMonitor monitor = new BedwarsMonitor();
    private KeyMapping toggleOverlay;
    private KeyMapping openSettings;
    private boolean overlayVisible;

    @Override
    public void onInitializeClient() {
        instance = this;
        config.load();
        KeyMapping.Category category = KeyMapping.Category.register(CATEGORY_ID);
        toggleOverlay = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.lazify.toggle_overlay", InputConstants.Type.KEYSYM, config.getInt("keybind"), category));
        openSettings = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.lazify.open_settings", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_L, category));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleOverlay.consumeClick()) {
                if (!config.getBoolean("keybindHold")) {
                    overlayVisible = !overlayVisible;
                    notifyPlayer(client, "Overlay " + (overlayVisible ? "enabled" : "disabled"));
                }
            }
            while (openSettings.consumeClick()) {
                openSettings();
            }
            monitor.tick(client, config, statsService);
            if (client.player != null && config.getBoolean("antiDebuff")) {
                client.player.removeEffect(MobEffects.NAUSEA);
                client.player.removeEffect(MobEffects.BLINDNESS);
            }
        });
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            String text = message.getString();
            monitor.onMessage(text);
            return !monitor.shouldHideMessage(text);
        });
        ClientReceiveMessageEvents.ALLOW_CHAT.register((message, playerChatMessage, sender, boundChatType, timeStamp) -> {
            String text = message.getString();
            monitor.onMessage(text);
            return !monitor.shouldHideMessage(text);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> monitor.onWorldChange());

        ClientEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof net.minecraft.client.player.AbstractClientPlayer remotePlayer
                    && remotePlayer != Minecraft.getInstance().player) {
                monitor.onPartyDetectorPlayerJoin(remotePlayer.getName().getString());
            }
        });

        LazifyHud hud = new LazifyHud(config, monitor);
        HudElementRegistry.attachElementBefore(VanillaHudElements.PLAYER_LIST,
                Identifier.fromNamespaceAndPath("lazify", "player_stats_under_tab"),
                (graphics, delta) -> {
                    if (!config.getBoolean("overlayOverTab")) hud.render(graphics, delta);
                });
        HudElementRegistry.attachElementAfter(VanillaHudElements.PLAYER_LIST,
                Identifier.fromNamespaceAndPath("lazify", "player_stats_over_tab"),
                (graphics, delta) -> {
                    if (config.getBoolean("overlayOverTab")) hud.render(graphics, delta);
                });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                LazifyCommands.register(dispatcher, config, monitor, statsService, this::openSettings));
        LOGGER.info("Lazify client initialized for Minecraft 26.2");
    }

    private void openSettings() {
        Minecraft client = Minecraft.getInstance();
        client.setScreenAndShow(new LazifySettingsScreen(client.gui.screen(), config));
    }

    static BedwarsMonitor monitor() {
        return instance == null ? null : instance.monitor;
    }
    static void rebindOverlayKey(int key) {
        if (instance == null) return;
        instance.config.setInt("keybind", key);
        instance.config.save();
        instance.toggleOverlay.setKey(InputConstants.Type.KEYSYM.getOrCreate(key));
        KeyMapping.resetMapping();
    }

    static String overlayKeyLabel() {
        return instance == null ? "Unknown" : instance.toggleOverlay.getTranslatedKeyMessage().getString();
    }

    static boolean overlayVisible() {
        if (instance == null || !instance.config.overlayEnabled()) return false;
        boolean tabHeld = Minecraft.getInstance().options.keyPlayerList.isDown();
        boolean toggled = instance.config.getBoolean("keybindHold")
                ? instance.toggleOverlay.isDown()
                : instance.overlayVisible;
        return toggled || (instance.config.getBoolean("showOnTab") && tabHeld);
    }

    static boolean tabHeld() {
        return Minecraft.getInstance().options.keyPlayerList.isDown();
    }

    public static boolean noHurtCamEnabled() {
        return instance != null && instance.config.getBoolean("noHurtCam");
    }

    public static boolean middleClickShopEnabled() {
        return instance != null && instance.config.getBoolean("middleClickShop");
    }
    public static boolean antiDebuffEnabled() {
        return instance != null && instance.config.getBoolean("antiDebuff");
    }

    public static boolean inBedwars() {
        return instance != null && instance.monitor.inBedwars();
    }

    private static void notifyPlayer(Minecraft client, String message) {
        if (client.player != null) client.player.sendOverlayMessage(Component.literal(message));
    }
}
