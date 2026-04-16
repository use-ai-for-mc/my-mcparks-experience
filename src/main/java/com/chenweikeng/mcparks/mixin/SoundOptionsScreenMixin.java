package com.chenweikeng.mcparks.mixin;

import com.chenweikeng.mcparks.audio.MCParksAudioService;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.SoundOptionsScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SoundOptionsScreen.class)
public abstract class SoundOptionsScreenMixin extends Screen {

    protected SoundOptionsScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void mcparks$addVolumeSlider(CallbackInfo ci) {
        MCParksAudioService service = MCParksAudioService.getInstance();
        if (!service.isConnected()) {
            return;
        }

        int currentVol = service.getUserVolume();
        double initialValue = currentVol / 100.0;

        // Find the Done button and shift it down to make room for our slider
        Button doneButton = null;
        for (GuiEventListener child : this.children()) {
            if (child instanceof Button btn) {
                doneButton = btn;
            }
        }

        int sliderWidth = 310;
        int x = this.width / 2 - 155;
        int y;

        if (doneButton != null) {
            // Place our slider where the Done button is, move Done button down
            y = doneButton.y;
            doneButton.y += 24;
        } else {
            // Fallback: place at bottom
            y = this.height - 50;
        }

        this.addRenderableWidget(new AbstractSliderButton(x, y, sliderWidth, 20,
                Component.translatable("options.mcparks.volume").append(": " + currentVol + "%"),
                initialValue) {
            @Override
            protected void updateMessage() {
                int percent = (int) (this.value * 100);
                this.setMessage(Component.translatable("options.mcparks.volume").append(": " + percent + "%"));
            }

            @Override
            protected void applyValue() {
                service.setUserVolumeFromSlider((int) Math.round(this.value * 100));
            }
        });
    }
}
