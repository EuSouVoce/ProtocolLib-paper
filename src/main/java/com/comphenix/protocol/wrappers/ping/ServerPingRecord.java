package com.comphenix.protocol.wrappers.ping;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.Bukkit;

import com.comphenix.protocol.events.InternalStructure;
import com.comphenix.protocol.reflect.EquivalentConverter;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.reflect.accessors.Accessors;
import com.comphenix.protocol.reflect.accessors.ConstructorAccessor;
import com.comphenix.protocol.reflect.accessors.FieldAccessor;
import com.comphenix.protocol.reflect.accessors.MethodAccessor;
import com.comphenix.protocol.utility.MinecraftProtocolVersion;
import com.comphenix.protocol.utility.MinecraftReflection;
import com.comphenix.protocol.utility.MinecraftVersion;
import com.comphenix.protocol.wrappers.AutoWrapper;
import com.comphenix.protocol.wrappers.BukkitConverters;
import com.comphenix.protocol.wrappers.Converters;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.comphenix.protocol.wrappers.WrappedGameProfile;
import com.comphenix.protocol.wrappers.WrappedServerPing;
import com.comphenix.protocol.wrappers.codecs.WrappedCodec;
import com.comphenix.protocol.wrappers.codecs.WrappedDynamicOps;
import com.google.common.collect.ImmutableList;

public final class ServerPingRecord implements ServerPingImpl {
    private static Class<?> SERVER_PING;
    private static Class<?> PLAYER_SAMPLE_CLASS;
    private static Class<?> SERVER_DATA_CLASS;

    private static Class<?> GSON_CLASS;
    private static MethodAccessor GSON_TO_JSON;
    private static MethodAccessor GSON_FROM_JSON;
    private static FieldAccessor DATA_SERIALIZER_GSON;
    private static Class<?> JSON_ELEMENT_CLASS;

    private static WrappedChatComponent DEFAULT_DESCRIPTION;

    private static ConstructorAccessor PING_CTOR;
    private static WrappedCodec CODEC;

    private static EquivalentConverter<List<WrappedGameProfile>> PROFILE_LIST_CONVERTER;

    private static boolean initialized = false;
    private static final Object lock = new Object();

    private static void initialize() {
        if (ServerPingRecord.initialized) {
            return;
        }

        synchronized (ServerPingRecord.lock) {
            // may have been initialized while waiting for the lock
            if (ServerPingRecord.initialized) {
                return;
            }

            try {
                ServerPingRecord.SERVER_PING = MinecraftReflection.getServerPingClass();
                ServerPingRecord.PLAYER_SAMPLE_CLASS = MinecraftReflection.getServerPingPlayerSampleClass();
                ServerPingRecord.SERVER_DATA_CLASS = MinecraftReflection.getServerPingServerDataClass();

                ServerPingRecord.PING_CTOR = Accessors.getConstructorAccessor(ServerPingRecord.SERVER_PING.getConstructors()[0]);

                ServerPingRecord.DATA_WRAPPER = AutoWrapper.wrap(ServerData.class, ServerPingRecord.SERVER_DATA_CLASS);
                ServerPingRecord.SAMPLE_WRAPPER = AutoWrapper.wrap(PlayerSample.class, ServerPingRecord.PLAYER_SAMPLE_CLASS);
                ServerPingRecord.FAVICON_WRAPPER = AutoWrapper.wrap(Favicon.class, MinecraftReflection.getMinecraftClass(
                        "network.protocol.status.ServerPing$a", "network.protocol.status.ServerStatus$Favicon"));

                ServerPingRecord.PROFILE_LIST_CONVERTER = BukkitConverters
                        .getListConverter(BukkitConverters.getWrappedGameProfileConverter());

                ServerPingRecord.DEFAULT_DESCRIPTION = WrappedChatComponent.fromLegacyText("A Minecraft Server");

                ServerPingRecord.GSON_CLASS = MinecraftReflection.getMinecraftGsonClass();
                ServerPingRecord.GSON_TO_JSON = Accessors.getMethodAccessor(ServerPingRecord.GSON_CLASS, "toJson", Object.class);
                ServerPingRecord.GSON_FROM_JSON = Accessors.getMethodAccessor(ServerPingRecord.GSON_CLASS, "fromJson", String.class, Class.class);
                ServerPingRecord.DATA_SERIALIZER_GSON = Accessors.getFieldAccessor(MinecraftReflection.getPacketDataSerializerClass(),
                        ServerPingRecord.GSON_CLASS, true);
                ServerPingRecord.JSON_ELEMENT_CLASS = MinecraftReflection.getLibraryClass("com.google.gson.JsonElement");
                ServerPingRecord.CODEC = WrappedCodec.fromHandle(
                        Accessors.getFieldAccessor(ServerPingRecord.SERVER_PING, MinecraftReflection.getCodecClass(), false).get(null));
            } catch (final Exception ex) {
                throw new RuntimeException("Failed to initialize Server Ping", ex);
            } finally {
                ServerPingRecord.initialized = true;
            }
        }
    }

    public static final class PlayerSample {
        public int max;
        public int online;
        public Object sample;

        public PlayerSample(final int max, final int online, final Object sample) {
            this.max = max;
            this.online = online;
            this.sample = sample;
        }

        public PlayerSample() {
            this(0, 0, null);
        }
    }

    public static final class ServerData {
        public String name;
        public int protocol;

        public ServerData(final String name, final int protocol) {
            this.name = name;
            this.protocol = protocol;
        }

        public ServerData() {
            this("", 0);
        }
    }

    static final byte[] EMPTY_FAVICON = new byte[0];

    public static final class Favicon {
        public byte[] iconBytes;

        public Favicon(final byte[] iconBytes) {
            this.iconBytes = iconBytes;
        }

        public Favicon() {
            this(ServerPingRecord.EMPTY_FAVICON);
        }
    }

    private static AutoWrapper<PlayerSample> SAMPLE_WRAPPER;

    private static AutoWrapper<ServerData> DATA_WRAPPER;

    private static AutoWrapper<Favicon> FAVICON_WRAPPER;

    private WrappedChatComponent description;
    private PlayerSample playerSample;
    private ServerData serverData;
    private final Favicon favicon;
    private boolean enforceSafeChat;
    private boolean playersVisible = true;

    private static ServerData defaultData() {
        final String name = MinecraftVersion.getCurrentVersion().toString();
        final int protocol = MinecraftProtocolVersion.getCurrentVersion();

        return new ServerData(name, protocol);
    }

    private static PlayerSample defaultSample() {
        final int max = Bukkit.getMaxPlayers();
        final int online = Bukkit.getOnlinePlayers().size();

        return new PlayerSample(max, online, new ArrayList<>());
    }

    private static Favicon defaultFavicon() {
        return new Favicon();
    }

    public static ServerPingRecord fromJson(final String json) {

        final Object jsonElement = ServerPingRecord.GSON_FROM_JSON.invoke(ServerPingRecord.DATA_SERIALIZER_GSON.get(null), json, ServerPingRecord.JSON_ELEMENT_CLASS);

        final Object decoded = ServerPingRecord.CODEC.parse(jsonElement, WrappedDynamicOps.json(false))
                .getOrThrow(e -> new IllegalStateException("Failed to decode: " + e));
        return new ServerPingRecord(decoded);
    }

    public ServerPingRecord(final Object handle) {
        ServerPingRecord.initialize();
        if (handle.getClass() != ServerPingRecord.SERVER_PING) {
            throw new IllegalArgumentException(
                    "Expected handle of type " + ServerPingRecord.SERVER_PING.getName() + " but got " + handle.getClass().getName());
        }

        final StructureModifier<Object> modifier = new StructureModifier<>(handle.getClass()).withTarget(handle);
        final InternalStructure structure = new InternalStructure(handle, modifier);

        this.description = structure.getChatComponents().readSafely(0);

        final StructureModifier<Optional<Object>> optionals = structure.getOptionals(Converters.passthrough(Object.class));

        final Optional<Object> sampleHandle = optionals.readSafely(0);
        this.playerSample = sampleHandle.isPresent() ? ServerPingRecord.SAMPLE_WRAPPER.wrap(sampleHandle.get()) : ServerPingRecord.defaultSample();

        final Optional<Object> dataHandle = optionals.readSafely(1);
        this.serverData = dataHandle.isPresent() ? ServerPingRecord.DATA_WRAPPER.wrap(dataHandle.get()) : ServerPingRecord.defaultData();

        final Optional<Object> faviconHandle = optionals.readSafely(2);
        this.favicon = faviconHandle.isPresent() ? ServerPingRecord.FAVICON_WRAPPER.wrap(faviconHandle.get()) : ServerPingRecord.defaultFavicon();

        this.enforceSafeChat = structure.getBooleans().readSafely(0);
    }

    public ServerPingRecord() {
        ServerPingRecord.initialize();

        this.description = ServerPingRecord.DEFAULT_DESCRIPTION;
        this.playerSample = ServerPingRecord.defaultSample();
        this.serverData = ServerPingRecord.defaultData();
        this.favicon = ServerPingRecord.defaultFavicon();
    }

    @Override
    public WrappedChatComponent getMotD() {
        return this.description;
    }

    @Override
    public void setMotD(final WrappedChatComponent description) {
        this.description = description;
    }

    @Override
    public int getPlayersMaximum() {
        return this.playerSample.max;
    }

    @Override
    public void setPlayersMaximum(final int maxPlayers) {
        this.playerSample.max = maxPlayers;
    }

    @Override
    public int getPlayersOnline() {
        return this.playerSample.online;
    }

    @Override
    public void setPlayersOnline(final int onlineCount) {
        this.playerSample.online = onlineCount;
    }

    @Override
    public ImmutableList<WrappedGameProfile> getPlayers() {
        if (this.playerSample.sample == null) {
            return ImmutableList.of();
        }

        final List<WrappedGameProfile> list = ServerPingRecord.PROFILE_LIST_CONVERTER.getSpecific(this.playerSample.sample);
        if (list == null) {
            return ImmutableList.of();
        }

        return ImmutableList.copyOf(list);
    }

    @Override
    public void setPlayers(final Iterable<? extends WrappedGameProfile> playerSample) {
        if (playerSample == null) {
            this.playerSample.sample = null;
            return;
        }

        final List<WrappedGameProfile> list = Converters.toList(playerSample);
        this.playerSample.sample = ServerPingRecord.PROFILE_LIST_CONVERTER.getGeneric(list);
    }

    @Override
    public String getVersionName() {
        return this.serverData.name;
    }

    @Override
    public void setVersionName(final String versionName) {
        this.serverData.name = versionName;
    }

    @Override
    public int getVersionProtocol() {
        return this.serverData.protocol;
    }

    @Override
    public void setVersionProtocol(final int protocolVersion) {
        this.serverData.protocol = protocolVersion;
    }

    @Override
    public WrappedServerPing.CompressedImage getFavicon() {
        return new WrappedServerPing.CompressedImage("data:image/png;base64", this.favicon.iconBytes);
    }

    @Override
    public void setFavicon(final WrappedServerPing.CompressedImage favicon) {
        this.favicon.iconBytes = favicon.getDataCopy();
    }

    @Override
    public boolean isEnforceSecureChat() {
        return this.enforceSafeChat;
    }

    @Override
    public void setEnforceSecureChat(final boolean safeChat) {
        this.enforceSafeChat = safeChat;
    }

    @Override
    public void resetPlayers() {
        this.playerSample = ServerPingRecord.defaultSample();
    }

    @Override
    public void resetVersion() {
        this.serverData = ServerPingRecord.defaultData();
    }

    @Override
    public boolean arePlayersVisible() {
        return this.playersVisible;
    }

    @Override
    public void setPlayersVisible(final boolean visible) {
        this.playersVisible = visible;
    }

    @Override
    public String getJson() {
        final Object encoded = ServerPingRecord.CODEC.encode(this.getHandle(), WrappedDynamicOps.json(false))
                .getOrThrow(e -> new IllegalStateException("Failed to encode: " + e));
        return (String) ServerPingRecord.GSON_TO_JSON.invoke(ServerPingRecord.DATA_SERIALIZER_GSON.get(null), encoded);
    }

    @Override
    public Object getHandle() {
        final WrappedChatComponent wrappedDescription = this.description != null ? this.description : ServerPingRecord.DEFAULT_DESCRIPTION;
        final Object descHandle = wrappedDescription.getHandle();

        final Optional<Object> playersHandle = Optional
                .ofNullable(ServerPingRecord.SAMPLE_WRAPPER.unwrap(this.playerSample != null ? this.playerSample : new ArrayList<>())); // sample
                                                                                                             // has to
                                                                                                             // be
                                                                                                             // non-null
                                                                                                             // in
                                                                                                             // handle
        final Optional<Object> versionHandle = Optional
                .ofNullable(this.serverData != null ? ServerPingRecord.DATA_WRAPPER.unwrap(this.serverData) : null);
        final Optional<Object> favHandle = Optional.ofNullable(this.favicon != null ? ServerPingRecord.FAVICON_WRAPPER.unwrap(this.favicon) : null);

        return ServerPingRecord.PING_CTOR.invoke(descHandle, playersHandle, versionHandle, favHandle, this.enforceSafeChat);
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj instanceof final ServerPingRecord other) {
            return Objects.equals(this.description, other.description)
                    && Objects.equals(this.playerSample, other.playerSample)
                    && Objects.equals(this.serverData, other.serverData)
                    && ((this.favicon == null && other.favicon.iconBytes == null)
                            || ((this.favicon != null) == (other.favicon != null)
                                    && Arrays.equals(this.favicon.iconBytes, other.favicon.iconBytes)));
        }
        return false;

    }
}
