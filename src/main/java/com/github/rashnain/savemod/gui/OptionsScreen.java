package com.github.rashnain.savemod.gui;

import com.github.rashnain.savemod.config.SaveModConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public final class OptionsScreen extends Screen {
    private static final int[] INTERVALS = {1, 5, 10, 20, 30, 40, 60};

    public OptionsScreen(Screen parent) {
        super(Component.translatable("savemod.options"));
    }

    @Override
    protected void init() {
        int cx = width / 2;
        int y = Math.max(40, height / 2 - 40);

        addRenderableWidget(Button.builder(
                Component.literal(toggleText()),
                button -> {
                    SaveModConfig.autoSave = !SaveModConfig.autoSave;
                    SaveModConfig.save();
                    button.setMessage(Component.literal(toggleText()));
                }
        ).bounds(cx - 150, y, 300, 22).build());

        addRenderableWidget(Button.builder(
                Component.literal(intervalText()),
                button -> {
                    SaveModConfig.intervalMinutes = nextInterval(
                            SaveModConfig.intervalMinutes
                    );
                    SaveModConfig.save();
                    button.setMessage(Component.literal(intervalText()));
                }
        ).bounds(cx - 150, y + 30, 300, 22).build());

        addRenderableWidget(Button.builder(
                CommonComponents.GUI_DONE,
                button -> onClose()
        ).bounds(cx - 100, height - 30, 200, 22).build());
    }

    private static String toggleText() {
        return SaveModConfig.autoSave
                ? "Sauvegarde automatique : ACTIVÉE"
                : "Sauvegarde automatique : DÉSACTIVÉE";
    }

    private static String intervalText() {
        return "Intervalle : " +
                (SaveModConfig.intervalMinutes == 60
                        ? "1 h"
                        : SaveModConfig.intervalMinutes + " min");
    }

    private static int nextInterval(int current) {
        for (int value : INTERVALS)
            if (value > current) return value;
        return INTERVALS[0];
    }

    @Override
    public void extractRenderState(
            GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            float delta
    ) {
        graphics.fill(0, 0, width, height, 0xFF080808);
        graphics.centeredText(
                font,
                title,
                width / 2,
                18,
                0xFFFFFFFF
        );
        graphics.centeredText(
                font,
                Component.literal(
                        "Réglages de la sauvegarde automatique."
                ),
                width / 2,
                34,
                0xFF888888
        );
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        SaveModConfig.save();
        if (minecraft != null)
            minecraft.gui.setScreen(null);
    }
}
