package me.matl114.hackUtils;

import io.netty.channel.ChannelPipeline;

import io.netty.handler.proxy.HttpProxyHandler;
import io.netty.handler.proxy.Socks4ProxyHandler;
import io.netty.handler.proxy.Socks5ProxyHandler;
import me.matl114.managers.Config;
import me.matl114.managers.Configs;
import me.matl114.utils.Debug;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.listener.PacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class NetworksTasks {
    static Config.FlagRef proxyEnable = Configs.HTTP_CONFIG.getBoolean(Configs.HTTP_PROXY_ENABLE);
    static Config.IntRef proxyPort = Configs.HTTP_CONFIG.getInt(Configs.HTTP_PROXY_PORT);
    static Config.StringRef proxyIp = Configs.HTTP_CONFIG.getString(Configs.HTTP_PROXY_SERVER);
    static Config.StringRef proxyUser = Configs.HTTP_CONFIG.getString(Configs.HTTP_PROXY_USERNAME);
    static Config.StringRef proxyPassword = Configs.HTTP_CONFIG.getString(Configs.HTTP_PROXY_OPTIONAL_PASSWORD);
    static Config.EnumRef<Configs.HttpProxyType> proxyType = Configs.HTTP_CONFIG.getEnum(Configs.HTTP_PROXY_TYPE);
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    public static void redirectIpPre(ChannelPipeline ch){
        if(proxyEnable.get()){
            int port = proxyPort.get();
            if(port > 0){
                String username = proxyUser.getValue();
                boolean userNo = username == null || username.isEmpty();
                String password = proxyPassword.getValue();
                switch (proxyType.getValue()){
                    case Configs.HttpProxyType.SOCKS:
                        if(password == null || password.isEmpty()){
                            ch.addFirst("socks5CLientProxy", new Socks4ProxyHandler(
                                new InetSocketAddress(proxyIp.getValue(), port),userNo?null :username
                            ));
                        }else {
                            ch.addFirst("socks5CLientProxy", new Socks5ProxyHandler(
                                new InetSocketAddress(proxyIp.getValue(), port), userNo? null: username, password
                            ));
                        }
                        break;
                    case Configs.HttpProxyType.HTTP:
                        ch.addFirst("httpCLientProxy", new HttpProxyHandler(
                            new InetSocketAddress(proxyIp.getValue(), port),userNo?null :username, (password == null || password.isEmpty())?null:password
                        ));
                        break;
                    case Configs.HttpProxyType.HTTPS:
//                        try{
//                            SslContext context = SslContextBuilder.forClient().build();
//                            ch.pipeline().addFirst("httpCLientProxy",  new HttpProxyHandler(
//                                new InetSocketAddress(proxyIp.get(), port),userNo?null :username, (password == null || password.isEmpty())?null:password,
//                            ));
//                            //ch.pipeline().addFirst(context.newHandler())
//                        }catch (Throwable e){
//                            Debug.info("Fail to create SSL context");
//                        }

                        break;
                }


            }
        }


    }



}
