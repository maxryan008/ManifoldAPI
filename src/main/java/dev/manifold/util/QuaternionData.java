package dev.manifold.util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.joml.Quaternionf;

public class QuaternionData {
    public static final Codec<QuaternionData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.FLOAT.fieldOf("x").forGetter(q -> q.x),
            Codec.FLOAT.fieldOf("y").forGetter(q -> q.y),
            Codec.FLOAT.fieldOf("z").forGetter(q -> q.z),
            Codec.FLOAT.fieldOf("w").forGetter(q -> q.w)
    ).apply(instance, QuaternionData::new));
    public final float x;
    public final float y;
    public final float z;
    public final float w;

    public QuaternionData(float x, float y, float z, float w) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.w = w;
    }

    public QuaternionData(Quaternionf quat) {
        this(quat.x, quat.y, quat.z, quat.w);
    }

    public Quaternionf toQuaternionf() {
        return new Quaternionf(x, y, z, w);
    }
}
