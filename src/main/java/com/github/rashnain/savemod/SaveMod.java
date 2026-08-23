package com.github.rashnain.savemod;

import com.github.rashnain.savemod.config.SaveModConfig;
import com.github.rashnain.savemod.gui.NameSaveScreen;
import com.github.rashnain.savemod.gui.SelectSaveScreen;
import com.github.rashnain.savemod.util.ZipUtil;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.storage.LevelResource;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@Environment(EnvType.CLIENT)
public final class SaveMod implements ClientModInitializer {

    public static final Logger LOGGER =
            LoggerFactory.getLogger("AutoSave");

    /*
     * IMPORTANT :
     * On utilise le vrai dossier de l'instance Minecraft.
     * Cela fonctionne avec Modrinth App.
     */
    public static final Path DIR =
            Minecraft.getInstance()
                    .gameDirectory
                    .toPath()
                    .resolve("savemod");

    public static String worldDir;

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern(
                    "yyyy-MM-dd_HH-mm-ss"
            );

    /*
     * Un seul thread pour les compressions.
     * Cela évite plusieurs ZIP simultanés.
     */
    private static final ExecutorService BACKUP_EXECUTOR =
            Executors.newSingleThreadExecutor(r -> {
                Thread thread =
                        new Thread(
                                r,
                                "AutoSave-Backup"
                        );

                thread.setDaemon(true);

                return thread;
            });

    /*
     * Empêche une deuxième sauvegarde automatique
     * de démarrer alors que la précédente est encore
     * en cours de compression.
     */
    private static final AtomicBoolean AUTO_SAVE_RUNNING =
            new AtomicBoolean(false);

    private int ticks;

    @Override
    public void onInitializeClient() {

        KeyMapping.Category category =
                KeyMapping.Category.register(
                        Identifier.fromNamespaceAndPath(
                                "autosave",
                                "main"
                        )
                );

        KeyMapping openList =
                KeyMappingHelper.registerKeyMapping(
                        new KeyMapping(
                                "key.savemod.open_list",
                                InputConstants.Type.KEYSYM,
                                GLFW.GLFW_KEY_UNKNOWN,
                                category
                        )
                );

        KeyMapping save =
                KeyMappingHelper.registerKeyMapping(
                        new KeyMapping(
                                "key.savemod.save",
                                InputConstants.Type.KEYSYM,
                                GLFW.GLFW_KEY_UNKNOWN,
                                category
                        )
                );

        SaveModConfig.load();

        ClientTickEvents.END_CLIENT_TICK.register(
                client -> {

                    while (openList.consumeClick()) {

                        if (isSingleplayer(client)) {
                            client.gui.setScreen(
                                    new SelectSaveScreen(null)
                            );
                        }
                    }

                    while (save.consumeClick()) {

                        if (isSingleplayer(client)) {

                            client.gui.setScreen(
                                    new NameSaveScreen(
                                            null,
                                            "",
                                            worldDir == null
                                                    ? ""
                                                    : worldDir,
                                            name -> {

                                                SelectSaveScreen screen =
                                                        new SelectSaveScreen(
                                                                null
                                                        );

                                                client.gui.setScreen(
                                                        screen
                                                );

                                                screen.save(name);
                                            }
                                    )
                            );
                        }
                    }

                    if (!SaveModConfig.autoSave
                            || !isSingleplayer(client)
                            || worldDir == null
                            || worldDir.isBlank()) {

                        ticks = 0;
                        return;
                    }

                    if (++ticks
                            >= SaveModConfig.intervalTicks()) {

                        ticks = 0;

                        automaticSave(client);
                    }
                }
        );
    }

    private static boolean isSingleplayer(
            Minecraft client
    ) {
        return client.hasSingleplayerServer()
                && client.getSingleplayerServer() != null
                && !client
                .getSingleplayerServer()
                .isPublished();
    }

    private static void automaticSave(
            Minecraft client
    ) {

        if (!AUTO_SAVE_RUNNING.compareAndSet(
                false,
                true
        )) {
            LOGGER.info(
                    "Sauvegarde automatique précédente encore en cours."
            );
            return;
        }

        var server =
                client.getSingleplayerServer();

        if (server == null
                || worldDir == null
                || worldDir.isBlank()) {

            AUTO_SAVE_RUNNING.set(false);
            return;
        }

        /*
         * Minecraft sauvegarde d'abord le monde
         * sur son thread serveur.
         */
        CompletableFuture
                .runAsync(
                        () -> {

                            boolean success =
                                    server.saveEverything(
                                            false,
                                            true,
                                            false
                                    );

                            if (!success) {
                                throw new IllegalStateException(
                                        "Minecraft n'a pas réussi à sauvegarder le monde."
                                );
                            }
                        },
                        server
                )

                /*
                 * Une fois la sauvegarde Minecraft terminée,
                 * la compression ZIP se fait sur notre thread
                 * d'arrière-plan.
                 */
                .thenRunAsync(
                        () -> {

                            try {

                                Path worldPath =
                                        server.getWorldPath(
                                                LevelResource.ROOT
                                        )
                                                .toAbsolutePath()
                                                .normalize();

                                if (!Files.isDirectory(
                                        worldPath
                                )) {

                                    throw new IllegalStateException(
                                            "Dossier du monde introuvable : "
                                                    + worldPath
                                    );
                                }

                                Path saveDir =
                                        DIR.resolve(
                                                worldDir
                                        );

                                Files.createDirectories(
                                        saveDir
                                );

                                Path target =
                                        saveDir.resolve(
                                                STAMP.format(
                                                        LocalDateTime.now()
                                                )
                                                        + "_AutoSave.zip"
                                        );

                                ZipUtil.createBackup(
                                        worldPath.toString(),
                                        target.toString()
                                );

                                LOGGER.info(
                                        "Sauvegarde automatique créée : {}",
                                        target
                                );

                            } catch (Exception e) {

                                LOGGER.error(
                                        "Échec de la sauvegarde automatique",
                                        e
                                );

                            } finally {
                                AUTO_SAVE_RUNNING.set(
                                        false
                                );
                            }
                        },
                        BACKUP_EXECUTOR
                )

                .exceptionally(
                        error -> {

                            LOGGER.error(
                                    "Échec de la sauvegarde Minecraft",
                                    error
                            );

                            AUTO_SAVE_RUNNING.set(
                                    false
                            );

                            return null;
                        }
                );
    }

    public static ExecutorService backupExecutor() {
        return BACKUP_EXECUTOR;
    }

    public static DateTimeFormatter timestampFormatter() {
        return STAMP;
    }
}
