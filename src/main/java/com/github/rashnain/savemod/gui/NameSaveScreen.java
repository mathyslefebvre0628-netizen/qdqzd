package com.github.rashnain.savemod.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

public class NameSaveScreen extends Screen {
    private final Screen parent;
    private final String previousName;
    private final String worldName;
    private final Consumer<String> consumer;
    private EditBox nameBox;

    public NameSaveScreen(Screen parent, String previousName, String worldName, Consumer<String> consumer) {
        super(Component.empty());
        this.parent = parent;
        this.previousName = previousName;
        this.worldName = worldName;
        this.consumer = consumer;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (super.keyPressed(event)) return true;
        if (getFocused() == nameBox && (event.input() == 257 || event.input() == 335)) {
            consumer.accept(nameBox.getValue());
            return true;
        }
        return false;
    }

    @Override
    protected void init() {
        nameBox = new EditBox(
                font,
                width / 2 - 100,
                height / 2 - 10,
                200,
                20,
                null,
                Component.empty()
        );
        addRenderableWidget(nameBox);

        if (previousName != null && !previousName.isEmpty())
            nameBox.setValue(previousName);

        addRenderableWidget(
                Button.builder(
                        Component.translatable(
                                previousName == null || previousName.isEmpty()
                                        ? "savemod.name.create"
                                        : "savemod.name.rename"
                        ),
                        button -> consumer.accept(nameBox.getValue())
                ).bounds(width / 2 - 155, height / 2 + 25, 150, 20).build()
        );

        addRenderableWidget(
                Button.builder(
                        CommonComponents.GUI_CANCEL,
                        button -> onClose()
                ).bounds(width / 2 + 5, height / 2 + 25, 150, 20).build()
        );

        setInitialFocus(nameBox);
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
                Component.literal(
                        previousName == null || previousName.isEmpty()
                                ? "Nommer la sauvegarde"
                                : "Renommer la sauvegarde"
                ),
                width / 2,
                height / 2 - 45,
                0xFFFFFFFF
        );
        graphics.centeredText(
                font,
                Component.literal("Monde : " + worldName),
                width / 2,
                height / 2 - 30,
                0xFF888888
        );
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        if (minecraft != null)
            minecraft.gui.setScreen(parent);
    }
}
