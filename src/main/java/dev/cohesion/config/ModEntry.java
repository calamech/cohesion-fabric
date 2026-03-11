package dev.cohesion.config;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Represents a mod entry in the server's sync manifest.
 */
public record ModEntry(
        String slug,
        String versionId,
        String sha512,
        String filename,
        String downloadUrl,
        boolean required
) {

    public static final StreamCodec<ByteBuf, ModEntry> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, ModEntry::slug,
            ByteBufCodecs.STRING_UTF8, ModEntry::versionId,
            ByteBufCodecs.STRING_UTF8, ModEntry::sha512,
            ByteBufCodecs.STRING_UTF8, ModEntry::filename,
            ByteBufCodecs.STRING_UTF8, ModEntry::downloadUrl,
            ByteBufCodecs.BOOL, ModEntry::required,
            ModEntry::new
    );
}
