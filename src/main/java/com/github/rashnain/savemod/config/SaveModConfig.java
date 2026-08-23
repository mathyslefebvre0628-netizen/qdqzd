package com.github.rashnain.savemod.config;

import com.github.rashnain.savemod.SaveMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class SaveModConfig {
    private static final Path PATH =
            FabricLoader.getInstance().getConfigDir().resolve("autosave.properties");
    private static final Properties P = new Properties();

    public static boolean autoSave = true;
    public static int intervalMinutes = 5;

    private SaveModConfig() {}

    public static void load() {
        try {
            if (Files.notExists(PATH)) {
                save();
                return;
            }
            try (InputStream in = Files.newInputStream(PATH)) {
                P.load(in);
            }
            autoSave = Boolean.parseBoolean(P.getProperty("auto-save", "true"));
            intervalMinutes = normalize(Integer.parseInt(P.getProperty("interval-minutes", "5")));
        } catch (Exception e) {
            SaveMod.LOGGER.error("Impossible de charger la configuration", e);
            autoSave = true;
            intervalMinutes = 5;
        }
    }

    public static void save() {
        try {
            Files.createDirectories(PATH.getParent());
            P.clear();
            P.setProperty("auto-save", Boolean.toString(autoSave));
            P.setProperty("interval-minutes", Integer.toString(normalize(intervalMinutes)));
            try (OutputStream out = Files.newOutputStream(PATH)) {
                P.store(out, "Auto Save configuration");
            }
        } catch (Exception e) {
            SaveMod.LOGGER.error("Impossible d'enregistrer la configuration", e);
        }
    }

    public static int normalize(int value) {
        return switch (value) {
            case 1, 5, 10, 20, 30, 40, 60 -> value;
            default -> 5;
        };
    }

    public static int intervalTicks() {
        return normalize(intervalMinutes) * 20 * 60;
    }
}
