package com.github.rashnain.savemod;

import com.github.rashnain.savemod.config.SaveModConfig;
import com.github.rashnain.savemod.gui.NameSaveScreen;
import com.github.rashnain.savemod.gui.SelectSaveScreen;
import com.github.rashnain.savemod.util.ZipUtil;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class SaveMod implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("AutoSave");
    public static final Path DIR = Path.of("savemod");
    public static String worldDir;

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private int ticks;

    @Override
    public void onInitializeClient() {
        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath("autosave", "main")
        );

        KeyMapping openList = KeyBindingHelper.registerKeyMapping(
                new KeyMapping(
                        "key.savemod.open_list",
                        InputConstants.Type.KEYSYM,
                        GLFW.GLFW_KEY_UNKNOWN,
                        category
                )
        );

        KeyMapping save = KeyBindingHelper.registerKeyMapping(
                new KeyMapping(
                        "key.savemod.save",
                        InputConstants.Type.KEYSYM,
                        GLFW.GLFW_KEY_UNKNOWN,
                        category
                )
        );

        SaveModConfig.load();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openList.consumeClick()) {
                if (isSingleplayer(client))
                    client.gui.setScreen(new SelectSaveScreen(null));
            }

            while (save.consumeClick()) {
                if (isSingleplayer(client))
                    client.gui.setScreen(
                            new NameSaveScreen(
                                    null,
                                    "",
                                    worldDir == null ? "" : worldDir,
                                    name -> {
                                        SelectSaveScreen screen = new SelectSaveScreen(null);
                                        client.gui.setScreen(screen);
                                        screen.save(name);
                                    }
                            )
                    );
            }

            if (!SaveModConfig.autoSave || !isSingleplayer(client) || worldDir == null) {
                ticks = 0;
                return;
            }

            if (++ticks >= SaveModConfig.intervalTicks()) {
                ticks = 0;
                automaticSave(client);
            }
        });
    }

    private static boolean isSingleplayer(Minecraft client) {
        return client.hasSingleplayerServer()
                && client.getSingleplayerServer() != null
                && !client.getSingleplayerServer().isPublished();
    }

    private static void automaticSave(Minecraft client) {
        try {
            var server = client.getSingleplayerServer();
            if (server == null || worldDir == null || worldDir.isBlank()) return;

            server.saveEverything(false, true, false);

            Path saveDir = DIR.resolve(worldDir);
            Files.createDirectories(saveDir);

            Path target = saveDir.resolve(
                    STAMP.format(LocalDateTime.now()) + "_AutoSave.zip"
            );

            ZipUtil.createBackup(
                    "saves/" + worldDir,
                    target.toString()
            );

            LOGGER.info("Sauvegarde automatique créée : {}", target);
        } catch (Exception e) {
            LOGGER.error("Échec de la sauvegarde automatique", e);
        }
    }
}
