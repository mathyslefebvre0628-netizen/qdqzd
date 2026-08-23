package com.github.rashnain.savemod;

import com.github.rashnain.savemod.config.SaveModConfig;
import com.github.rashnain.savemod.gui.NameSaveScreen;
import com.github.rashnain.savemod.gui.SelectSaveScreen;
import com.github.rashnain.savemod.util.ZipUtil;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.api.EnvironmentInterface;
import net.fabricmc.api.EnvType;
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
     * Dossier des sauvegardes de l'instance Minecraft active.
     *
     * Avec Modrinth, par exemple :
     *
     * C:\Users\Mathy\AppData\Roaming\ModrinthApp\
     * profiles\test\savemod
     */
    public static final Path DIR =
            Minecraft.getInstance()
                    .gameDirectory
                    .toPath()
                    .resolve("savemod")
                    .toAbsolutePath()
                    .normalize();

    /*
     * Nom du monde actuellement chargé.
     */
    public static String worldDir;

    /*
     * Format utilisé dans les noms de fichiers.
     */
    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern(
                    "yyyy-MM-dd_HH-mm-ss"
            );

    /*
     * Thread dédié à la compression ZIP.
     *
     * Cela évite de bloquer le rendu de Minecraft
     * pendant la création de la sauvegarde.
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
     * Empêche deux sauvegardes automatiques
     * de se compresser en même temps.
     */
    private static final AtomicBoolean AUTO_SAVE_RUNNING =
            new AtomicBoolean(false);

    private int ticks = 0;

    @Override
    public void onInitializeClient() {

        /*
         * Catégorie des touches.
         */
        KeyMapping.Category category =
                KeyMapping.Category.register(
                        Identifier.fromNamespaceAndPath(
                                "autosave",
                                "main"
                        )
                );

        /*
         * Ouvrir la liste des sauvegardes.
         */
        KeyMapping openList =
                KeyMappingHelper.registerKeyMapping(
                        new KeyMapping(
                                "key.savemod.open_list",
                                InputConstants.Type.KEYSYM,
                                GLFW.GLFW_KEY_UNKNOWN,
                                category
                        )
                );

        /*
         * Créer une sauvegarde manuelle.
         */
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

                    /*
                     * Touche : ouvrir les sauvegardes.
                     */
                    while (openList.consumeClick()) {

                        if (isSingleplayer(client)) {

                            client.gui.setScreen(
                                    new SelectSaveScreen(null)
                            );
                        }
                    }

                    /*
                     * Touche : sauvegarde manuelle.
                     */
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

                    /*
                     * Auto Save désactivé
                     * ou aucun monde.
                     */
                    if (!SaveModConfig.autoSave
                            || !isSingleplayer(client)
                            || worldDir == null
                            || worldDir.isBlank()) {

                        ticks = 0;
                        return;
                    }

                    /*
                     * Intervalle atteint.
                     */
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

    /*
     * =========================================================
     * SAUVEGARDE AUTOMATIQUE
     * =========================================================
     */
    private static void automaticSave(
            Minecraft client
    ) {

        /*
         * Une seule compression à la fois.
         */
        if (!AUTO_SAVE_RUNNING.compareAndSet(
                false,
                true
        )) {

            LOGGER.info(
                    "Une sauvegarde automatique est déjà en cours."
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
         * 1. Minecraft sauvegarde d'abord
         *    le monde sur son thread serveur.
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
                 * 2. Compression ZIP sur le thread
                 *    AutoSave-Backup.
                 */
                .thenRunAsync(
                        () -> {

                            try {

                                /*
                                 * Récupère le vrai dossier
                                 * du monde actuellement chargé.
                                 *
                                 * Compatible Modrinth.
                                 */
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

                                /*
                                 * Dossier des sauvegardes
                                 * dans l'instance active.
                                 */
                                Path saveDir =
                                        DIR.resolve(
                                                worldDir
                                        )
                                                .toAbsolutePath()
                                                .normalize();

                                Files.createDirectories(
                                        saveDir
                                );

                                /*
                                 * Exemple :
                                 *
                                 * 2026-08-24_01-05-00_AutoSave.zip
                                 */
                                Path target =
                                        saveDir.resolve(
                                                STAMP.format(
                                                        LocalDateTime.now()
                                                )
                                                        + "_AutoSave.zip"
                                        );

                                /*
                                 * Compression en arrière-plan.
                                 */
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

                /*
                 * Gestion des erreurs du futur.
                 */
                .exceptionally(
                        error -> {

                            LOGGER.error(
                                    "Échec de la sauvegarde automatique",
                                    error
                            );

                            AUTO_SAVE_RUNNING.set(
                                    false
                            );

                            return null;
                        }
                );
    }

    /*
     * Exécuteur utilisé par les sauvegardes manuelles
     * pour éviter le freeze pendant la compression.
     */
    public static ExecutorService backupExecutor() {
        return BACKUP_EXECUTOR;
    }

    /*
     * Formatter utilisé par les autres classes.
     */
    public static DateTimeFormatter timestampFormatter() {
        return STAMP;
    }

    /*
     * Retourne le dossier des sauvegardes d'un monde.
     */
    public static Path getSaveDirectory(
            Minecraft client,
            String worldDir
    ) {
        return client.gameDirectory
                .toPath()
                .resolve("savemod")
                .resolve(worldDir)
                .toAbsolutePath()
                .normalize();
    }
}
