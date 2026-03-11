package dev.cohesion.network;

import dev.cohesion.CohesionConstants;
import dev.cohesion.config.ModEntry;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;

public record ModManifestS2CPayload(List<ModEntry> entries) implements CustomPacketPayload {

    public static final Type<ModManifestS2CPayload> TYPE =
            new Type<>(CohesionConstants.MOD_MANIFEST_PACKET_ID);

    public static final StreamCodec<ByteBuf, ModManifestS2CPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ModEntry.STREAM_CODEC.apply(ByteBufCodecs.list()),
                    ModManifestS2CPayload::entries,
                    ModManifestS2CPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
