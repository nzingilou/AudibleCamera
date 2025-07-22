package com.audiblecamera.mixin;

import com.audiblecamera.AudibleCameraMod;
import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Keyboard.class)
public class KeyboardMixin {
    
    @Inject(method = "onKey", at = @At("HEAD"), cancellable = true)
    private void onKey(long window, int key, int scancode, int action, int modifiers, CallbackInfo ci) {
        // PERFECT ZERO-CONFLICT SYSTEM:
        // Camera OFF: Only X key intercepted, everything else passes through
        // Camera ON: ALL mod keys intercepted, other mods see NOTHING
        
        if (action == GLFW.GLFW_PRESS || action == GLFW.GLFW_RELEASE || action == GLFW.GLFW_REPEAT) {
            
            // X key is intercepted EXCEPT when in any GUI
            if (key == GLFW.GLFW_KEY_X) {
                MinecraftClient client = MinecraftClient.getInstance();
                if (client != null && client.currentScreen != null) {
                    // Don't intercept X key if player is in any GUI
                    // Specifically check for chat, inventory, game menu, and cloth config
                    Screen currentScreen = client.currentScreen;
                    if (currentScreen instanceof ChatScreen || 
                        currentScreen instanceof HandledScreen || 
                        currentScreen instanceof GameMenuScreen ||
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
                    case GLFW.GLFW_KEY_UP:
                    case GLFW.GLFW_KEY_DOWN:
                    case GLFW.GLFW_KEY_LEFT:
                    case GLFW.GLFW_KEY_RIGHT:
                    case GLFW.GLFW_KEY_PAGE_UP:
                    case GLFW.GLFW_KEY_PAGE_DOWN:
                    
                    // Configuration F-keys - completely hijacked
                    case GLFW.GLFW_KEY_F4:
                    case GLFW.GLFW_KEY_F5:
                    case GLFW.GLFW_KEY_F6:
                    case GLFW.GLFW_KEY_F7:
                    case GLFW.GLFW_KEY_F8:
                    case GLFW.GLFW_KEY_F9:
                    case GLFW.GLFW_KEY_F10:
                    case GLFW.GLFW_KEY_F11:
                    case GLFW.GLFW_KEY_F12:
                    
                    // Core camera controls - completely hijacked
                    case GLFW.GLFW_KEY_R:
                    case GLFW.GLFW_KEY_P:
                    case GLFW.GLFW_KEY_ESCAPE:
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