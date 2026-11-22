package me.matl114.hooks;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.systems.friends.Friend;
import meteordevelopment.meteorclient.systems.friends.Friends;

public abstract class MeteorHooks implements IHooks {
    private static MeteorHooks INSTANCE;

    public static MeteorHooks getInstance() {
        if (INSTANCE == null) {
            try {
                INSTANCE = new Impl();
            } catch (Throwable e) {
                INSTANCE = new Default();
            }
        }
        return INSTANCE;
    }

    public abstract boolean isFriend(String player);

    public static class Impl extends MeteorHooks {
        public Impl() {
            Class<?> clazz = MeteorClient.class;
            clazz = Friends.class;
            clazz = Friend.class;
        }

        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public boolean isFriend(String player) {
            try {
                return Friends.get().get(player) != null;
            } catch (Throwable e) {
                return false;
            }
        }
    }

    public static class Default extends MeteorHooks {

        @Override
        public boolean isEnabled() {
            return false;
        }

        @Override
        public boolean isFriend(String player) {
            return false;
        }
    }
}
