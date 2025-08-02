package io.github.nzingilou.audiblecamera.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.nzingilou.audiblecamera.AudibleCameraMod;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public class KeyboardHandlerMixin {
    
    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void keyPress(long window, int key, int scancode, int action, int modifiers, CallbackInfo ci) {
        // PERFECT ZERO-CONFLICT SYSTEM:
        // Camera OFF: Only X key intercepted, everything else passes through
        // Camera ON: ALL mod keys intercepted, other mods see NOTHING
        
        if (action == InputConstants.PRESS || action == InputConstants.RELEASE || action == InputConstants.REPEAT) {
            
            // X key is intercepted EXCEPT when in any GUI
            if (key == InputConstants.KEY_X) {
                Minecraft client = Minecraft.getInstance();
                if (client != null && client.screen != null) {
                    // Don't intercept X key if player is in any GUI
                    // Specifically check for chat, inventory, game menu, and cloth config
                    Screen currentScreen = client.screen;
                    if (currentScreen instanceof ChatScreen || 
                        currentScreen instanceof AbstractContainerScreen || 
                        currentScreen instanceof PauseScreen ||
                        currentScreen.getClass().getName().contains("cloth") ||
                        currentScreen.getClass().getName().contains("config")) {
                        return; // Let the GUI handle the X key
                    }
                }
                // Let the mod handle X key, block other mods from seeing it
                ci.cancel();
                return;
            }
            
            // When camera is ACTIVE, steal ALL other mod keys
            if (AudibleCameraMod.isCameraActive()) {
                switch (key) {
                    // Movement keys - completely hijacked
                    case InputConstants.KEY_UP:
                    case InputConstants.KEY_DOWN:
                    case InputConstants.KEY_LEFT:
                    case InputConstants.KEY_RIGHT:
                    case InputConstants.KEY_PAGEUP:
                    case InputConstants.KEY_PAGEDOWN:
                    
                    // Configuration F-keys - completely hijacked
                    case InputConstants.KEY_F4:
                    case InputConstants.KEY_F5:
                    case InputConstants.KEY_F6:
                    case InputConstants.KEY_F7:
                    case InputConstants.KEY_F8:
                    case InputConstants.KEY_F9:
                    case InputConstants.KEY_F10:
                    case InputConstants.KEY_F11:
                    case InputConstants.KEY_F12:
                    
                    // Core camera controls - completely hijacked
                    case InputConstants.KEY_R:
                    case InputConstants.KEY_P:
                    case InputConstants.KEY_ESCAPE:
                        // STEAL: Other mods never see these when camera is active
                        ci.cancel();
                        return;
                }
            }
            
            // When camera is INACTIVE, all keys (except X) pass through normally
            // Other mods get F10, R, arrows, etc. exactly as if this mod doesn't exist
        }
    }
} 