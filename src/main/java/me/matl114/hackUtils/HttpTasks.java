package me.matl114.hackUtils;

import io.netty.channel.ChannelPipeline;

import me.matl114.managers.Config;
import me.matl114.managers.Configs;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class HttpTasks {
    static AtomicBoolean proxyEnable = Configs.HTTP_CONFIG.getBoolean(Configs.HTTP_PROXY_ENABLE);
    static AtomicInteger proxyPort = Configs.HTTP_CONFIG.getInt(Configs.HTTP_PROXY_PORT);
    static Config.StringRef proxyIp = Configs.HTTP_CONFIG.getString(Configs.HTTP_PROXY_SERVER);
    static Config.StringRef proxyUser = Configs.HTTP_CONFIG.getString(Configs.HTTP_PROXY_USERNAME);
    static Config.StringRef proxyPassword = Configs.HTTP_CONFIG.getString(Configs.HTTP_PROXY_OPTIONAL_PASSWORD);
    static Config.StringRef proxyType = Configs.HTTP_CONFIG.getString(Configs.HTTP_PROXY_TYPE);
    public static void redirectIpPre(ChannelPipeline ch){
        if(proxyEnable.get()){
//            int port = proxyPort.get();
//            if(port > 0){
//                String username = proxyUser.get();
//                boolean userNo = username == null || username.isEmpty();
//                String password = proxyPassword.get();
//                switch (proxyType.get()){
//                    case "socks":
//                        if(password == null || password.isEmpty()){
//                            ch.addFirst("socks5CLientProxy", new Socks4ProxyHandler(
//                                new InetSocketAddress(proxyIp.get(), port),userNo?null :username
//                            ));
//                        }else {
//                            ch.addFirst("socks5CLientProxy", new Socks5ProxyHandler(
//                                new InetSocketAddress(proxyIp.get(), port), userNo? null: username, password
//                            ));
//                        }
//                        break;
//                    case "http":
//                        ch.addFirst("httpCLientProxy", new HttpProxyHandler(
//                            new InetSocketAddress(proxyIp.get(), port),userNo?null :username, (password == null || password.isEmpty())?null:password
//                        ));
//                        break;
//                    case "https":
////                        try{
////                            SslContext context = SslContextBuilder.forClient().build();
////                            ch.pipeline().addFirst("httpCLientProxy",  new HttpProxyHandler(
////                                new InetSocketAddress(proxyIp.get(), port),userNo?null :username, (password == null || password.isEmpty())?null:password,
////                            ));
////                            //ch.pipeline().addFirst(context.newHandler())
////                        }catch (Throwable e){
////                            Debug.info("Fail to create SSL context");
////                        }
//
//                        break;
//                }
//
//
//            }
        }


    }



}
