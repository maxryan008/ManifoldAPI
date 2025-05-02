package dev.manifold;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ConstructSaveData extends SavedData {
    public static final Codec<ConstructSaveData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.unboundedMap(
                            Codec.STRING.xmap(UUID::fromString, UUID::toString),
                            DynamicConstruct.CODEC
                    ).fieldOf("constructs").forGetter(data -> data.constructs)
            ).apply(instance, ConstructSaveData::new)
    );
    public static final SavedData.Factory<ConstructSaveData> FACTORY = new SavedData.Factory<>(
            ConstructSaveData::new,
            ConstructSaveData::load,
            null
    );
    private final Map<UUID, DynamicConstruct> constructs = new HashMap<>();

    public ConstructSaveData() {
    }

    public ConstructSaveData(Map<UUID, DynamicConstruct> constructs) {
        this.constructs.putAll(constructs);
        this.setDirty();
    }

    public static ConstructSaveData load(CompoundTag tag, HolderLookup.Provider provider) {
        return CODEC.parse(NbtOps.INSTANCE, tag)
                .resultOrPartial(System.err::println)
                .orElseGet(() -> {
                    System.err.println("ConstructSaveData: Failed to load from tag.");
                    return new ConstructSaveData();
                });
    }

    @Override
    public @NotNull CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        CODEC.encodeStart(NbtOps.INSTANCE, this)
                .resultOrPartial(System.err::println)
                .ifPresent(encoded -> {
                    if (encoded instanceof CompoundTag compoundTag) {
                        tag.merge(compoundTag);
                    } else {
                        System.err.println("ConstructSaveData: Encoded tag was not a CompoundTag!");
                    }
                });
        return tag;
    }

    public Map<UUID, DynamicConstruct> getConstructs() {
        return constructs;
    }
}
