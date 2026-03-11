package dev.cohesion.network;

import dev.cohesion.CohesionConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ManifestAckC2SPayload(boolean accepted) implements CustomPacketPayload {

    public static final Type<ManifestAckC2SPayload> TYPE =
            new Type<>(CohesionConstants.MANIFEST_ACK_PACKET_ID);

    public static final StreamCodec<ByteBuf, ManifestAckC2SPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL,
                    ManifestAckC2SPayload::accepted,
                    ManifestAckC2SPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
