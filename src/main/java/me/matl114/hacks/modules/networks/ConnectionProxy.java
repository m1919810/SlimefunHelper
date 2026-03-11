package me.matl114.hacks.modules.networks;

import io.netty.channel.ChannelPipeline;
import io.netty.handler.proxy.HttpProxyHandler;
import io.netty.handler.proxy.Socks4ProxyHandler;
import io.netty.handler.proxy.Socks5ProxyHandler;
import java.net.InetSocketAddress;
import java.util.Locale;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.gui.basic.*;
import me.matl114.hacks.api.BaseModule;
import me.matl114.managers.Configs;
import me.matl114.managers.config.*;
import me.matl114.utils.Debug;
import net.minecraft.text.Text;
import org.jetbrains.annotations.ApiStatus;

public class ConnectionProxy extends BaseModule {
    public static final String[] HTTP_PROXY_SERVER = {"proxy-server", "host"};
    public static final String[] HTTP_PROXY_PORT = {"proxy-server", "port"};
    public static final String[] HTTP_PROXY_ENABLE = {"proxy-server", "enable"};
    public static final String[] HTTP_PROXY_USERNAME = {"proxy-server", "username"};
    public static final String[] HTTP_PROXY_OPTIONAL_PASSWORD = {"proxy-server", "password?"};
    public static final String[] HTTP_PROXY_TYPE = {"proxy-server", "type"};

    public ConnectionProxy() {
        bindFlag(enable);
    }

    public final FlagRef enable =
            flagBuilder(Configs.HTTP_CONFIG, HTTP_PROXY_ENABLE).build();

    public final IntRef port = builder(Configs.HTTP_CONFIG, HTTP_PROXY_PORT, IntRef.TYPE)
            .defaultValue(7890)
            .validator(Configs.intRange(0, 65536))
            .build();

    public final StringRef ip = builder(Configs.HTTP_CONFIG, HTTP_PROXY_SERVER, StringRef.TYPE)
            .defaultValue("127.0.0.1")
            .build();

    public final EnumRef<HttpProxyType> proxyType = builder(Configs.HTTP_CONFIG, HTTP_PROXY_TYPE, HttpProxyType.class)
            .defaultValue(HttpProxyType.SOCKS)
            .build();

    public final StringRef userName = builder(Configs.HTTP_CONFIG, HTTP_PROXY_USERNAME, StringRef.TYPE)
            .defaultValue("")
            .build();

    public final StringRef password = builder(Configs.HTTP_CONFIG, HTTP_PROXY_OPTIONAL_PASSWORD, StringRef.TYPE)
            .defaultValue("")
            .build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getConnectionChannelInitialize(), this::onPipelineInitialize);
    }

    public void onPipelineInitialize(Event<ChannelPipeline> chEvent) {
        var ch = chEvent.context();
        if (isActive()) {
            int port = this.port.get();
            if (port > 0) {
                String username = this.userName.getValue();
                boolean userNo = username == null || username.isEmpty();
                String password = this.password.getValue();
                InetSocketAddress addr;
                try {
                    addr = new InetSocketAddress(ip.get(), port);
                } catch (Throwable e) {
                    Debug.info("Invalid address :", ip.get(), port, e);
                    Debug.info(e);
                    return;
                }

                switch (proxyType.getValue()) {
                    case HttpProxyType.SOCKS -> {
                        if (password == null || password.isEmpty()) {
                            ch.addFirst("socks4ClientProxy", new Socks4ProxyHandler(addr, userNo ? null : username));
                        } else {
                            ch.addFirst(
                                    "socks5ClientProxy",
                                    new Socks5ProxyHandler(addr, userNo ? null : username, password));
                        }
                    }

                    case HttpProxyType.HTTP -> {
                        ch.addFirst(
                                "httpClientProxy",
                                new HttpProxyHandler(
                                        addr,
                                        userNo ? null : username,
                                        (password == null || password.isEmpty()) ? null : password));
                    }

                    case HttpProxyType.HTTPS -> {
                        // not developed yet
                    }
                }
            }
        }
    }

    public enum HttpProxyType implements ConfigEnum {
        SOCKS,
        HTTP,
        @ApiStatus.Experimental
        HTTPS;

        public Text getDisplay() {
            return Text.literal(name().toLowerCase(Locale.ROOT));
        }
    }
}
