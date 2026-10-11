package dev.waco0311.clipboardtoolkit;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.content.equipment.clipboard.ClipboardContent;
import com.simibubi.create.content.equipment.clipboard.ClipboardEntry;
import com.simibubi.create.content.equipment.clipboard.ClipboardOverrides.ClipboardType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.List;

// Independent snapshots: the standard clipboard is only the combined presentation.
public record AreaContents(ClipboardContent placed, ClipboardContent stored) {
    public static final Codec<AreaContents> CODEC = RecordCodecBuilder.create(i -> i.group(
            ClipboardContent.CODEC.fieldOf("placed").forGetter(AreaContents::placed),
            ClipboardContent.CODEC.fieldOf("stored").forGetter(AreaContents::stored)
    ).apply(i, AreaContents::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, AreaContents> STREAM_CODEC = StreamCodec.composite(
            ClipboardContent.STREAM_CODEC, AreaContents::placed,
            ClipboardContent.STREAM_CODEC, AreaContents::stored, AreaContents::new);

    public ClipboardContent display() {
        List<List<ClipboardEntry>> pages = new ArrayList<>();
        appendSection(pages, placed, "placed");
        appendSection(pages, stored, "stored");
        if (pages.size() > 50) throw new ClipboardLists.ScanLimitException();
        return new ClipboardContent(ClipboardType.WRITTEN, List.copyOf(pages), true);
    }

    private static void appendSection(List<List<ClipboardEntry>> pages, ClipboardContent content, String name) {
        List<ClipboardEntry> entries = content.pages().stream().flatMap(List::stream).toList();
        for (int offset = 0; offset < Math.max(1, entries.size()); offset += 6) {
            List<ClipboardEntry> page = new ArrayList<>();
            page.add(new ClipboardEntry(false, Component.translatable("area.clipboardtoolkit." + name)));
            if (entries.isEmpty()) page.add(new ClipboardEntry(false, Component.translatable("area.clipboardtoolkit.none")));
            for (int index = offset; index < Math.min(offset + 6, entries.size()); index++) {
                var entry = entries.get(index);
                page.add(new ClipboardEntry(false, entry.text.copy()).displayItem(entry.icon.copy(), entry.itemAmount));
            }
            pages.add(List.copyOf(page));
        }
    }
}

