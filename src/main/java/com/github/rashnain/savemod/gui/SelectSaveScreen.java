package com.github.rashnain.savemod.gui;

import com.github.rashnain.savemod.SaveMod;
import com.github.rashnain.savemod.gui.widget.SaveListEntry;
import com.github.rashnain.savemod.gui.widget.SaveListWidget;
import com.github.rashnain.savemod.util.ZipUtil;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;

public class SelectSaveScreen extends Screen {

    private static final DateTimeFormatter FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    protected final Screen parent;
    protected final Runnable actionWhenClosed;

    private SaveListWidget saveList;
    private EditBox searchBox;

    private Button loadButton;
    private Button renameButton;
    private Button duplicateButton;
    private Button deleteButton;

    public SelectSaveScreen(Screen parent) {
        this(parent, null);
    }

    public SelectSaveScreen(
            Screen parent,
            Runnable actionWhenClosed
    ) {
        super(Component.translatable("savemod.list.title"));

        this.parent = parent;
        this.actionWhenClosed = actionWhenClosed;
    }

    @Override
    public boolean keyPressed(
            net.minecraft.client.input.KeyEvent keyEvent
    ) {
        if (super.keyPressed(keyEvent)) {
            return true;
        }

        if (keyEvent.isSelection()) {
            if (saveList != null) {
                saveList
                        .getSelectedAsOptional()
                        .ifPresent(SaveListEntry::load);
            }

            return true;
        }

        return false;
    }

    @Override
    protected void init() {

        HeaderAndFooterLayout layout =
                new HeaderAndFooterLayout(
                        this,
                        49,
                        60
                );

        LinearLayout header =
                layout.addToHeader(
                        LinearLayout.vertical()
                                .spacing(4)
                );

        header
                .defaultCellSetting()
                .alignHorizontallyCenter();

        header.addChild(
                new StringWidget(
                        title,
                        font
                )
        );

        searchBox =
                header.addChild(
                        new EditBox(
                                font,
                                0,
                                0,
                                220,
                                20,
                                null,
                                Component.empty()
                        )
                );

        searchBox.setResponder(
                text -> {

                    if (saveList == null) {
                        return;
                    }

                    saveList.setSearch(text);

                    changeButtons(
                            saveList.getSelected() != null
                    );
                }
        );

        GridLayout footer =
                layout.addToFooter(
                        new GridLayout()
                                .columnSpacing(8)
                                .rowSpacing(4)
                );

        footer
                .defaultCellSetting()
                .alignHorizontallyCenter();

        GridLayout.RowHelper add =
                footer.createRowHelper(4);

        saveList =
                new SaveListWidget(
                        this,
                        minecraft,
                        width,
                        layout.getContentHeight(),
                        layout.getHeaderHeight(),
                        36
                );

        layout.addToContents(
                saveList
        );

        loadButton =
                add.addChild(
                        Button.builder(
                                Component.translatable(
                                        "savemod.list.play"
                                ),
                                _ ->
                                        saveList
                                                .getSelectedAsOptional()
                                                .ifPresent(
                                                        SaveListEntry::load
                                                )
                        ).build(),
                        2
                );

        loadButton.active = false;

        add.addChild(
                Button.builder(
                        Component.translatable(
                                "savemod.list.create"
                        ),
                        _ ->
                                minecraft.gui.setScreen(
                                        new NameSaveScreen(
                                                this,
                                                "",
                                                SaveMod.worldDir,
                                                this::save
                                        )
                                )
                ).build(),
                2
        );

        renameButton =
                add.addChild(
                        Button.builder(
                                Component.translatable(
                                        "savemod.list.rename"
                                ),
                                _ ->
                                        saveList
                                                .getSelectedAsOptional()
                                                .ifPresent(
                                                        SaveListEntry::rename
                                                )
                        )
                                .width(71)
                                .build()
                );

        renameButton.active = false;

        deleteButton =
                add.addChild(
                        Button.builder(
                                Component.translatable(
                                        "savemod.list.delete"
                                ),
                                _ ->
                                        saveList
                                                .getSelectedAsOptional()
                                                .ifPresent(
                                                        SaveListEntry::delete
                                                )
                        )
                                .width(71)
                                .build()
                );

        deleteButton.active = false;

        duplicateButton =
                add.addChild(
                        Button.builder(
                                Component.translatable(
                                        "savemod.list.duplicate"
                                ),
                                _ ->
                                        saveList
                                                .getSelectedAsOptional()
                                                .ifPresent(
                                                        SaveListEntry::duplicate
                                                )
                        )
                                .width(71)
                                .build()
                );

        duplicateButton.active = false;

        add.addChild(
                Button.builder(
                        CommonComponents.GUI_DONE,
                        _ -> onClose()
                )
                        .width(71)
                        .build()
        );

        layout.visitWidgets(
                this::addRenderableWidget
        );

        layout.arrangeElements();
    }

    @Override
    public void onClose() {

        if (minecraft != null) {
            minecraft.gui.setScreen(parent);
        }

        if (actionWhenClosed != null) {
            actionWhenClosed.run();
        }
    }

    public void changeButtons(
            boolean active
    ) {

        if (loadButton != null) {
            loadButton.active = active;
        }

        if (renameButton != null) {
            renameButton.active = active;
        }

        if (duplicateButton != null) {
            duplicateButton.active = active;
        }

        if (deleteButton != null) {
            deleteButton.active = active;
        }
    }

    public void save(
            String saveName
    ) {

        ProgressScreen screen =
                new ProgressScreen(false);

        screen.progressStartNoAbort(
                Component.translatable(
                        "savemod.message.saving"
                )
        );

        minecraft.setScreenAndShow(screen);

        IntegratedServer server =
                minecraft.getSingleplayerServer();

        if (server != null) {

            CompletableFuture
                    .runAsync(
                            () ->
                                    server.saveEverything(
                                            false,
                                            true,
                                            false
                                    ),
                            server
                    )
                    .thenRunAsync(
                            () ->
                                    finishSaving(
                                            saveName
                                    ),
                            minecraft
                    )
                    .thenRun(
                            screen::stop
                    );

        } else {

            finishSaving(saveName);

            screen.stop();
        }
    }

    private void finishSaving(
            String saveName
    ) {

        try {

            if (SaveMod.worldDir == null
                    || SaveMod.worldDir.isBlank()) {

                throw new IOException(
                        "Aucun monde sélectionné."
                );
            }

            Path dir =
                    SaveMod.DIR.resolve(
                            SaveMod.worldDir
                    );

            Files.createDirectories(
                    dir
            );

            String name =
                    saveName == null
                            || saveName.isBlank()
                            ? "Manual"
                            : saveName;

            Path target =
                    dir.resolve(
                            FORMAT.format(
                                    LocalDateTime.now()
                            )
                                    + "_"
                                    + name
                                    + ".zip"
                    );

            ZipUtil.createBackup(
                    "saves/" + SaveMod.worldDir,
                    target.toString()
            );

            if (saveList != null) {
                saveList.refresh();
            }

            minecraft.gui.toastManager().addToast(
                    new SystemToast(
                            SystemToast.SystemToastId
                                    .PERIODIC_NOTIFICATION,
                            Component.translatable(
                                    "savemod.toast.succesful"
                            ),
                            Component.translatable(
                                    "savemod.toast.succesful.save"
                            )
                    )
            );

            minecraft.gui.setScreen(null);

        } catch (IOException e) {

            SaveMod.LOGGER.error(
                    "Impossible de créer la sauvegarde",
                    e
            );

            minecraft.gui.toastManager().addToast(
                    new SystemToast(
                            SystemToast.SystemToastId
                                    .PERIODIC_NOTIFICATION,
                            Component.translatable(
                                    "savemod.toast.failed"
                            ),
                            Component.translatable(
                                    "savemod.toast.failed.save"
                            )
                    )
            );

            minecraft.gui.setScreen(
                    this
            );
        }
    }

    @Override
    public void extractRenderState(
            GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            float delta
    ) {

        graphics.fill(
                0,
                0,
                width,
                height,
                0xFF060606
        );

        super.extractRenderState(
                graphics,
                mouseX,
                mouseY,
                delta
        );
    }
}
