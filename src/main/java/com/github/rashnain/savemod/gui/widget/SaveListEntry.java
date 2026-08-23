package com.github.rashnain.savemod.gui.widget;

import com.github.rashnain.savemod.SaveMod;
import com.github.rashnain.savemod.SaveSummary;
import com.github.rashnain.savemod.gui.NameSaveScreen;
import com.github.rashnain.savemod.util.ZipUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.apache.commons.io.FileUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class SaveListEntry extends ObjectSelectionList.Entry<SaveListEntry> {
    private static final DateFormat DATE_FORMAT = new SimpleDateFormat();
    private static final Identifier UNKNOWN =
            Identifier.withDefaultNamespace("textures/misc/unknown_server.png");
    public static final Identifier JOIN_HIGHLIGHTED =
            Identifier.withDefaultNamespace("world_list/join_highlighted");
    public static final Identifier JOIN =
            Identifier.withDefaultNamespace("world_list/join");

    private final Minecraft client;
    private final SaveListWidget list;
    private final SaveSummary save;
    private final Path dir;
    private final Path file;
    private long lastClick;

    public SaveListEntry(SaveSummary save, SaveListWidget list) {
        this.save = save;
        this.list = list;
        this.client = Minecraft.getInstance();
        this.dir = SaveMod.DIR.resolve(save.getWorldDir());
        this.file = dir.resolve(save.getSaveFileName());
    }

    @Override
    public Component getNarration() {
        return Component.nullToEmpty(save.getSaveName());
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.x() - list.getRowLeft() <= 32 || Util.getMillis() - lastClick < 250L) {
            load();
            return true;
        }
        lastClick = Util.getMillis();
        return true;
    }

    @Override
    public void extractContent(
            GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            boolean hovered,
            float delta
    ) {
        int x = getContentX();
        int y = getContentY();

        graphics.text(client.font, save.getSaveName(), x + 35, y + 1, -1);
        graphics.text(
                client.font,
                save.getWorldDir() + " (" +
                        DATE_FORMAT.format(new Date(save.getLastPlayed())) +
                        ")",
                x + 35,
                y + client.font.lineHeight + 3,
                -0x808080
        );
        graphics.text(
                client.font,
                save.getSizeInMB() + " MB",
                x + 35,
                y + client.font.lineHeight * 2 + 4,
                -0x808080
        );

        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                UNKNOWN,
                x, y, 0, 0,
                32, 32, 32, 32
        );

        if (hovered) {
            graphics.fill(x, y, x + 32, y + 32, -0x5F6F6F70);
            graphics.blitSprite(
                    RenderPipelines.GUI_TEXTURED,
                    mouseX - x <= 32 ? JOIN_HIGHLIGHTED : JOIN,
                    x, y, 32, 32
            );
        }
    }

    public void load() {
        if (client.hasSingleplayerServer()) {
            client.disconnectFromWorld(
                    Component.translatable("savemod.message.closing")
            );
        }

        client.setScreenAndShow(
                new GenericMessageScreen(
                        Component.translatable("savemod.message.deleting")
                )
        );

        try {
            FileUtils.deleteDirectory(
                    Path.of("saves").resolve(save.getWorldDir()).toFile()
            );

            client.setScreenAndShow(
                    new GenericMessageScreen(
                            Component.translatable(
                                    "savemod.message.uncompressing"
                            )
                    )
            );

            ZipUtil.unzipFile(file.toString(), "saves/");
            client.createWorldOpenFlows().openWorld(save.getWorldDir(), () -> {});
        } catch (IOException e) {
            SaveMod.LOGGER.error("Could not load save", e);
            client.gui.setScreen(list.getParent());
        }
    }

    public void rename() {
        client.gui.setScreen(
                new NameSaveScreen(
                        list.getParent(),
                        save.getSaveName(),
                        save.getWorldDir(),
                        newName -> {
                            if (newName == null || newName.isEmpty())
                                newName = save.getWorldDir();

                            String filename =
                                    file.getFileName().toString();

                            String renamed =
                                    filename.substring(0, 20)
                                            + newName
                                            + ".zip";

                            try {
                                Files.move(
                                        file,
                                        dir.resolve(renamed)
                                );
                            } catch (IOException e) {
                                SaveMod.LOGGER.error(
                                        "Could not rename save",
                                        e
                                );
                            }

                            client.gui.setScreen(
                                    list.getParent()
                            );
                        }
                )
        );
    }

    public void duplicate() {
        try {
            String copy =
                    save.getSaveFileName()
                            .replaceFirst(
                                    "\\.zip$",
                                    " " +
                                            Component.translatable(
                                                    "savemod.name.copy"
                                            ).getString()
                                            + ".zip"
                            );

            Files.copy(
                    file,
                    dir.resolve(copy)
            );

            list.refresh();

        } catch (IOException e) {
            SaveMod.LOGGER.error(
                    "Could not duplicate save",
                    e
            );
        }
    }

    public void delete() {
        client.gui.setScreen(
                new ConfirmScreen(
                        confirmed -> {
                            if (confirmed) {
                                try {
                                    Files.deleteIfExists(file);
                                } catch (IOException e) {
                                    SaveMod.LOGGER.error(
                                            "Could not delete save",
                                            e
                                    );
                                }
                            }

                            list.refresh();
                            client.gui.setScreen(
                                    list.getParent()
                            );
                        },
                        Component.translatable("savemod.delete.question"),
                        Component.translatable(
                                "selectWorld.deleteWarning",
                                save.getSaveName()
                        )
                )
        );
    }
}
