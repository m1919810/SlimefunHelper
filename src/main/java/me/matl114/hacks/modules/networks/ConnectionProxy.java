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
import me.matl114.hacks.api.ModulePath;
import me.matl114.managers.Configs;
import me.matl114.managers.config.*;
import me.matl114.utils.Debug;
import net.minecraft.text.Text;
import org.jetbrains.annotations.ApiStatus;

public class ConnectionProxy extends BaseModule {
    public final ModulePath proxyServer = makePath(Configs.HTTP_CONFIG, "proxy-server");

    public ConnectionProxy() {
        bindFlag(enable);
    }

    public final FlagRef enable =
            flagBuilder(proxyServer.add("enable")).build();

    public final IntRef port = builder(proxyServer.add("port"), IntRef.TYPE)
            .defaultValue(7890)
            .validator(Configs.intRange(0, 65536))
            .build();

    public final StringRef ip = builder(proxyServer.add("host"), StringRef.TYPE)
            .defaultValue("127.0.0.1")
            .build();

    public final EnumRef<HttpProxyType> proxyType = builder(proxyServer.add("type"), HttpProxyType.class)
            .defaultValue(HttpProxyType.SOCKS)
            .build();

    public final StringRef userName = builder(proxyServer.add("username"), StringRef.TYPE)
            .defaultValue("")
            .build();

    public final StringRef password = builder(proxyServer.add("password?"), StringRef.TYPE)
            .defaultValue("")
            .build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getConnectionChannelInitialize(), this::onPipelineInitialize);
    }

    public void onPipelineInitialize(Event<ChannelPipeline> chEvent) {
        var ch = chEvent.context();
        if (isActive() && !chEvent.<Boolean>getArgs(1)) {
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
