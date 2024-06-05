package com.comphenix.protocol.injector.netty.channel;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.reflect.FuzzyReflection;
import com.comphenix.protocol.reflect.accessors.Accessors;
import com.comphenix.protocol.reflect.accessors.FieldAccessor;
import com.comphenix.protocol.reflect.accessors.MethodAccessor;
import com.comphenix.protocol.reflect.fuzzy.FuzzyFieldContract;
import com.comphenix.protocol.utility.MinecraftReflection;

import io.netty.channel.Channel;
import io.netty.util.AttributeKey;

@SuppressWarnings("unchecked")
final class ChannelProtocolUtil {

    public static final BiFunction<Channel, PacketType.Sender, PacketType.Protocol> PROTOCOL_RESOLVER;

    static {
        final Class<?> networkManagerClass = MinecraftReflection.getNetworkManagerClass();
        final List<Field> attributeKeys = FuzzyReflection.fromClass(networkManagerClass, true)
                .getFieldList(FuzzyFieldContract.newBuilder()
                        .typeExact(AttributeKey.class)
                        .requireModifier(Modifier.STATIC)
                        .declaringClassExactType(networkManagerClass)
                        .build());

        BiFunction<Channel, PacketType.Sender, PacketType.Protocol> baseResolver = null;
        if (attributeKeys.isEmpty()) {
            // since 1.20.5 the protocol is stored as final field in de-/encoder
            baseResolver = new Post1_20_5WrappedResolver();
        } else if (attributeKeys.size() == 1) {
            // if there is only one attribute key we can assume it's the correct one (1.8 -
            // 1.20.1)
            final Object protocolKey = Accessors.getFieldAccessor(attributeKeys.get(0)).get(null);
            baseResolver = new Pre1_20_2DirectResolver((AttributeKey<Object>) protocolKey);
        } else if (attributeKeys.size() > 1) {
            // most likely 1.20.2+: 1 protocol key per protocol direction
            AttributeKey<Object> serverBoundKey = null;
            AttributeKey<Object> clientBoundKey = null;

            for (final Field keyField : attributeKeys) {
                final AttributeKey<Object> key = (AttributeKey<Object>) Accessors.getFieldAccessor(keyField).get(null);
                if (key.name().equals("protocol")) {
                    // legacy (pre 1.20.2 name) - fall back to the old behaviour
                    baseResolver = new Pre1_20_2DirectResolver(key);
                    break;
                }

                if (key.name().contains("protocol")) {
                    // one of the two protocol keys for 1.20.2
                    if (key.name().contains("server")) {
                        serverBoundKey = key;
                    } else {
                        clientBoundKey = key;
                    }
                }
            }

            if (baseResolver == null) {
                if ((serverBoundKey == null || clientBoundKey == null)) {
                    // neither pre 1.20.2 key nor 1.20.2+ keys are available
                    throw new ExceptionInInitializerError("Unable to resolve protocol state attribute keys");
                } else {
                    baseResolver = new Post1_20_2WrappedResolver(serverBoundKey, clientBoundKey);
                }
            }
        } else {
            throw new ExceptionInInitializerError("Unable to resolve protocol state attribute key(s)");
        }

        // decorate the base resolver by wrapping its return value into our packet type
        // value
        PROTOCOL_RESOLVER = baseResolver;
    }

    private static final class Pre1_20_2DirectResolver
            implements BiFunction<Channel, PacketType.Sender, PacketType.Protocol> {

        private final AttributeKey<Object> attributeKey;

        public Pre1_20_2DirectResolver(final AttributeKey<Object> attributeKey) {
            this.attributeKey = attributeKey;
        }

        @Override
        public PacketType.Protocol apply(final Channel channel, final PacketType.Sender sender) {
            return PacketType.Protocol.fromVanilla((Enum<?>) channel.attr(this.attributeKey).get());
        }
    }

    private static final class Post1_20_2WrappedResolver
            implements BiFunction<Channel, PacketType.Sender, PacketType.Protocol> {

        private final AttributeKey<Object> serverBoundKey;
        private final AttributeKey<Object> clientBoundKey;

        // lazy initialized when needed
        private FieldAccessor protocolAccessor;

        public Post1_20_2WrappedResolver(final AttributeKey<Object> serverBoundKey, final AttributeKey<Object> clientBoundKey) {
            this.serverBoundKey = serverBoundKey;
            this.clientBoundKey = clientBoundKey;
        }

        @Override
        public PacketType.Protocol apply(final Channel channel, final PacketType.Sender sender) {
            final AttributeKey<Object> key = this.getKeyForSender(sender);
            final Object codecData = channel.attr(key).get();
            if (codecData == null) {
                // If the codec handler was not found, fallback to HANDSHAKING
                // Fixes https://github.com/dmulloy2/ProtocolLib/issues/2601
                return PacketType.Protocol.HANDSHAKING;
            }

            final FieldAccessor protocolAccessor = this.getProtocolAccessor(codecData.getClass());
            return PacketType.Protocol.fromVanilla((Enum<?>) protocolAccessor.get(codecData));
        }

        private AttributeKey<Object> getKeyForSender(final PacketType.Sender sender) {
            return switch (sender) {
                case SERVER -> this.clientBoundKey;
                case CLIENT -> this.serverBoundKey;
                default -> throw new IllegalArgumentException("Illegal packet sender " + sender.name());
            };
        }

        private FieldAccessor getProtocolAccessor(final Class<?> codecClass) {
            if (this.protocolAccessor == null) {
                final Class<?> enumProtocolClass = MinecraftReflection.getEnumProtocolClass();
                this.protocolAccessor = Accessors.getFieldAccessor(codecClass, enumProtocolClass, true);
            }

            return this.protocolAccessor;
        }
    }

    /**
     * Since 1.20.5 the protocol is stored as final field in de-/encoder
     */
    private static final class Post1_20_5WrappedResolver
            implements BiFunction<Channel, PacketType.Sender, PacketType.Protocol> {

        // lazy initialized when needed
        private Function<Object, Object> serverProtocolAccessor;
        private Function<Object, Object> clientProtocolAccessor;

        @Override
        public PacketType.Protocol apply(final Channel channel, final PacketType.Sender sender) {
            final String key = this.getKeyForSender(sender);
            final Object codecHandler = channel.pipeline().get(key);
            if (codecHandler == null) {
                final String unconfiguratedKey = this.getUnconfiguratedKeyForSender(sender);
                if (channel.pipeline().get(unconfiguratedKey) != null) {
                    return PacketType.Protocol.HANDSHAKING;
                }
                return null;
            }

            final Function<Object, Object> protocolAccessor = this.getProtocolAccessor(codecHandler.getClass(), sender);
            return PacketType.Protocol.fromVanilla((Enum<?>) protocolAccessor.apply(codecHandler));
        }

        private Function<Object, Object> getProtocolAccessor(final Class<?> codecHandler, final PacketType.Sender sender) {
            switch (sender) {
                case SERVER:
                    if (this.serverProtocolAccessor == null) {
                        this.serverProtocolAccessor = this.getProtocolAccessor(codecHandler);
                    }
                    return this.serverProtocolAccessor;
                case CLIENT:
                    if (this.clientProtocolAccessor == null) {
                        this.clientProtocolAccessor = this.getProtocolAccessor(codecHandler);
                    }
                    return this.clientProtocolAccessor;
                default:
                    throw new IllegalArgumentException("Illegal packet sender " + sender.name());
            }
        }

        private String getKeyForSender(final PacketType.Sender sender) {
            return switch (sender) {
                case SERVER -> "encoder";
                case CLIENT -> "decoder";
                default -> throw new IllegalArgumentException("Illegal packet sender " + sender.name());
            };
        }

        private String getUnconfiguratedKeyForSender(final PacketType.Sender sender) {
            return switch (sender) {
                case SERVER -> "outbound_config";
                case CLIENT -> "inbound_config";
                default -> throw new IllegalArgumentException("Illegal packet sender " + sender.name());
            };
        }

        private Function<Object, Object> getProtocolAccessor(final Class<?> codecHandler) {
            final Class<?> protocolInfoClass = MinecraftReflection.getProtocolInfoClass();

            final MethodAccessor protocolAccessor = Accessors.getMethodAccessor(FuzzyReflection
                    .fromClass(protocolInfoClass)
                    .getMethodByReturnTypeAndParameters("id", MinecraftReflection.getEnumProtocolClass(),
                            new Class[0]));

            final FieldAccessor protocolInfoAccessor = Accessors.getFieldAccessor(codecHandler, protocolInfoClass, true);

            // get ProtocolInfo from handler and get EnumProtocol of ProtocolInfo
            return handler -> {
                final Object protocolInfo = protocolInfoAccessor.get(handler);
                return protocolAccessor.invoke(protocolInfo);
            };
        }
    }
}
