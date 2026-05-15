package com.chenweikeng.mcparks.monkeycraft;

import com.chenweikeng.monkeycraft_api.v1.MonkeycraftApiProvider;
import com.chenweikeng.monkeycraft_api.v1.MonkeycraftApiRegistration;
import net.minecraft.client.Minecraft;

public final class MonkeycraftProvider implements MonkeycraftApiProvider {

    public static void registerOnce() {
        if (MonkeycraftApiRegistration.isAvailable()) return;
        try {
            MonkeycraftApiRegistration.register(new MonkeycraftProvider());
        } catch (IllegalStateException ignored) {
        }
    }

    @Override
    public void setTimedNotification(Long fireAtEpochMs, String title, String body,
                                     boolean sound, String countDownText) {
    }

    @Override
    public void cancelTimedNotification() {
    }

    @Override
    public void sendImmediateNotification(String title, String body, boolean sound) {
    }

    @Override
    public void startHibernation(String message) {
    }

    @Override
    public void setHibernationMessage(String message) {
    }

    @Override
    public void endHibernation() {
    }

    @Override
    public boolean isClientConnected() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.getConnection() != null;
    }

    @Override
    public boolean isHibernating() {
        return false;
    }

    @Override
    public boolean isServerStarted() {
        return false;
    }
}
