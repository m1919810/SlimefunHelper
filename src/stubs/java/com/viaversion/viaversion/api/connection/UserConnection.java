package com.viaversion.viaversion.api.connection;

import io.netty.channel.Channel;

public interface UserConnection {

    Channel getChannel();
}
