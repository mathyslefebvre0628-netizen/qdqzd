package com.github.rashnain.savemod.gui.widget;

import com.github.rashnain.savemod.SaveMod;
import com.github.rashnain.savemod.SaveSummary;
import com.github.rashnain.savemod.gui.SelectSaveScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class SaveListWidget extends ObjectSelectionList<SaveListEntry> {
    private final SelectSaveScreen parent;
    private List<SaveSummary> saves = new ArrayList<>();
    private String search = "";

    public SaveListWidget(
            SelectSaveScreen parent,
            Minecraft client,
            int width,
            int height,
            int top,
            int itemHeight
    ) {
        super(client, width, height, top, itemHeight);
        this.parent = parent;
        refresh();
    }

    @Override
    public void setSelected(@Nullable SaveListEntry entry) {
        super.setSelected(entry);
        parent.changeButtons(entry != null);
    }

    @Override
    public int getRowWidth() { return 300; }

    public Screen getParent() { return parent; }

    public void refresh() {
        clearEntries();
        saves = getSaves();

        for (SaveSummary summary : saves) {
            if (summary.getSaveName().toLowerCase(Locale.ROOT)
                    .contains(search.toLowerCase(Locale.ROOT))) {
                addEntry(new SaveListEntry(summary, this));
            }
        }
    }

    private List<SaveSummary> getSaves() {
        List<SaveSummary> list = new ArrayList<>();
        if (SaveMod.worldDir == null) return list;

        File dir = SaveMod.DIR.resolve(SaveMod.worldDir).toFile();
        File[] files = dir.listFiles(
                f -> f.isFile()
                        && f.getName().endsWith(".zip")
                        && f.getName().length() > 24
        );

        if (files != null) {
            Arrays.sort(files, Collections.reverseOrder());
            for (File file : files)
                list.add(
                        new SaveSummary(
                                file.getName(),
                                SaveMod.worldDir,
                                file.length()
                        )
                );
        }
        return list;
    }

    public Optional<SaveListEntry> getSelectedAsOptional() {
        return Optional.ofNullable(getSelected());
    }

    public void setSearch(String value) {
        if (!value.equals(search)) {
            search = value;
            refresh();
        }
    }
}
