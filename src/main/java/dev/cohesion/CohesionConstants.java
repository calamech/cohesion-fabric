package dev.cohesion;

import net.minecraft.resources.Identifier;

public final class CohesionConstants {

    public static final String MOD_ID = "cohesion";

    public static final Identifier MOD_MANIFEST_PACKET_ID = Identifier.fromNamespaceAndPath(MOD_ID, "mod_manifest");
    public static final Identifier MANIFEST_ACK_PACKET_ID = Identifier.fromNamespaceAndPath(MOD_ID, "manifest_ack");

    public static final String CACHE_DIR_NAME = "cohesion";
    public static final String CACHE_SUBDIR = "cache";
    public static final String REGISTRY_FILENAME = "managed_mods.json";
    public static final String SERVER_CONFIG_FILENAME = "cohesion_server.json";
    public static final String PENDING_RESTART_FILENAME = "pending_restart";

    public static final String MODRINTH_API_BASE = "https://api.modrinth.com/v2";
    public static final String USER_AGENT = "cohesion/1.0.0 (fabric-mod)";

    private CohesionConstants() {}
}
