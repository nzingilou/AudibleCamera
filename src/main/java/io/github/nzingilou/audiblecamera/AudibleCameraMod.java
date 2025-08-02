package io.github.nzingilou.audiblecamera;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.text.Text;
import net.minecraft.world.World;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.sound.SoundEvents;
import net.minecraft.sound.SoundCategory;
import net.minecraft.client.gui.screen.GameMenuScreen;
import org.lwjgl.glfw.GLFW;

public class AudibleCameraMod implements ClientModInitializer {
    
    // Key bindings
    private static KeyBinding CAMERA_TOGGLE_KEY;
    private static KeyBinding CAMERA_UP_KEY;
    private static KeyBinding CAMERA_DOWN_KEY;
    private static KeyBinding CAMERA_LEFT_KEY;
    private static KeyBinding CAMERA_RIGHT_KEY;
    private static KeyBinding CAMERA_FORWARD_KEY;
    private static KeyBinding CAMERA_BACKWARD_KEY;
    private static KeyBinding PAUSE_TOGGLE_KEY;
    
    // F-key bindings for configuration
    private static KeyBinding TOGGLE_START_POSITION_KEY;
    private static KeyBinding TOGGLE_MOVEMENT_MODE_KEY;
    private static KeyBinding TOGGLE_COORDINATES_KEY;
    private static KeyBinding TOGGLE_DIRECTIONS_KEY;
    private static KeyBinding TOGGLE_RELATIVE_DIRECTIONS_KEY;
    private static KeyBinding SOLID_BLOCKS_ONLY_KEY;
    private static KeyBinding LIGHT_LEVEL_TOGGLE_KEY;
    private static KeyBinding SCAN_AREA_KEY;
    private static KeyBinding CURRENT_POSITION_KEY;
    private static KeyBinding RECENTER_KEY;
    private static KeyBinding CAMERA_EXIT_KEY;
    
    // Camera state - keeping cameraActive static for mixin access, rest instance-based
    private static boolean cameraActive = false;
    private boolean pauseMode = false;
    private boolean gamePaused = false;
    private BlockPos cameraPosition = BlockPos.ORIGIN;
    
    // Thread management for F9 scanning
    private Thread scanThread = null;
    

    
    // Configuration options - NON-STATIC to prevent persistence bugs
    private boolean startAtHeadLevel = false; // true = head level, false = feet level (default)
    private boolean cardinalDirections = false; // true = only N/S/E/W, false = player facing
    private boolean showCoordinates = true;
    private boolean showDirections = true;
    private boolean relativeDirections = false; // true = ahead/behind/left/right, false = north/south/east/west
    private boolean solidBlocksOnly = false;
    private boolean showLightLevel = false; // true = show light levels for safety
    
    // Per-key movement debouncing to prevent spam but allow different directions
    private long lastVerticalMovementTime = 0;
    private long lastForwardBackwardMovementTime = 0;
    private long lastLeftRightMovementTime = 0; 
    private static final long MOVEMENT_COOLDOWN_MS = 100; // 100ms between same-type movements
    
    // Prevent auto-movement when camera first activates
    private long cameraActivationTime = 0;
    private static final long ACTIVATION_DELAY_MS = 200; // 200ms delay after activation
    
    // X key state tracking (since mixin intercepts it)  
    private boolean xKeyPreviouslyPressed = false;
    
    // State tracking for all intercepted keys (since mixin intercepts them when camera is active)
    private boolean upKeyPreviouslyPressed = false;
    private boolean downKeyPreviouslyPressed = false;
    private boolean leftKeyPreviouslyPressed = false;
    private boolean rightKeyPreviouslyPressed = false;
    private boolean pageUpKeyPreviouslyPressed = false;
    private boolean pageDownKeyPreviouslyPressed = false;
    private boolean rKeyPreviouslyPressed = false;
    private boolean pKeyPreviouslyPressed = false;
    private boolean escapeKeyPreviouslyPressed = false;
    private boolean f4KeyPreviouslyPressed = false;
    private boolean f5KeyPreviouslyPressed = false;
    private boolean f6KeyPreviouslyPressed = false;
    private boolean f7KeyPreviouslyPressed = false;
    private boolean f8KeyPreviouslyPressed = false;
    private boolean f9KeyPreviouslyPressed = false;
    private boolean f10KeyPreviouslyPressed = false;
    private boolean f11KeyPreviouslyPressed = false;
    private boolean f12KeyPreviouslyPressed = false;
    
    // Helper method for direct GLFW key detection with state tracking (for intercepted keys)
    private boolean wasKeyJustPressed(MinecraftClient client, int glfwKey, boolean[] previousState) {
        if (client.getWindow() == null) return false;
        
        long window = client.getWindow().getHandle();
        boolean currentlyPressed = GLFW.glfwGetKey(window, glfwKey) == GLFW.GLFW_PRESS;
        boolean wasJustPressed = currentlyPressed && !previousState[0];
        previousState[0] = currentlyPressed;
        return wasJustPressed;
    }
    
    @Override
    public void onInitializeClient() {
        // Register key bindings - perfect zero-conflict system with mixin interception
        CAMERA_TOGGLE_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.audiblecamera.toggle", 
            GLFW.GLFW_KEY_X, 
            "category.audiblecamera.main"
        ));
        
        CAMERA_UP_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.audiblecamera.up", 
            GLFW.GLFW_KEY_PAGE_UP,
            "category.audiblecamera.main"
        ));
        
        CAMERA_DOWN_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.audiblecamera.down", 
            GLFW.GLFW_KEY_PAGE_DOWN,
            "category.audiblecamera.main"
        ));
        
        CAMERA_LEFT_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.audiblecamera.left", 
            GLFW.GLFW_KEY_LEFT, 
            "category.audiblecamera.main"
        ));
        
        CAMERA_RIGHT_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.audiblecamera.right", 
            GLFW.GLFW_KEY_RIGHT, 
            "category.audiblecamera.main"
        ));
        
        CAMERA_FORWARD_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.audiblecamera.forward", 
            GLFW.GLFW_KEY_UP, 
            "category.audiblecamera.main"
        ));
        
        CAMERA_BACKWARD_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.audiblecamera.backward", 
            GLFW.GLFW_KEY_DOWN, 
            "category.audiblecamera.main"
        ));
        
        PAUSE_TOGGLE_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.audiblecamera.pause_toggle", 
            GLFW.GLFW_KEY_P, 
            "category.audiblecamera.main"
        ));
        
        // F-key configuration bindings (mixin intercepts these when camera active to prevent conflicts)
        TOGGLE_START_POSITION_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.audiblecamera.toggle_start", 
            GLFW.GLFW_KEY_F6, 
            "category.audiblecamera.main"
        ));
        
        TOGGLE_MOVEMENT_MODE_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.audiblecamera.toggle_movement", 
            GLFW.GLFW_KEY_F7, 
            "category.audiblecamera.main"
        ));
        
        TOGGLE_COORDINATES_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.audiblecamera.toggle_coordinates", 
            GLFW.GLFW_KEY_F8, 
            "category.audiblecamera.main"
        ));
        
        TOGGLE_DIRECTIONS_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.audiblecamera.toggle_directions", 
            GLFW.GLFW_KEY_F5, 
            "category.audiblecamera.main"
        ));
        
        TOGGLE_RELATIVE_DIRECTIONS_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.audiblecamera.toggle_relative_directions", 
            GLFW.GLFW_KEY_F4, 
            "category.audiblecamera.main"
        ));
        
        SOLID_BLOCKS_ONLY_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.audiblecamera.solid_blocks_only", 
            GLFW.GLFW_KEY_F11, 
            "category.audiblecamera.main"
        ));
        
        LIGHT_LEVEL_TOGGLE_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.audiblecamera.light_level_toggle", 
            GLFW.GLFW_KEY_F10, 
            "category.audiblecamera.main"
        ));
        
        SCAN_AREA_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.audiblecamera.scan_area", 
            GLFW.GLFW_KEY_F9, 
            "category.audiblecamera.main"
        ));
        
        CURRENT_POSITION_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.audiblecamera.current_position", 
            GLFW.GLFW_KEY_F12, 
            "category.audiblecamera.main"
        ));
        
        RECENTER_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.audiblecamera.recenter", 
            GLFW.GLFW_KEY_R, 
            "category.audiblecamera.main"
        ));
        
        // Alternative exit key for safety
        CAMERA_EXIT_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.audiblecamera.exit", 
            GLFW.GLFW_KEY_ESCAPE, 
            "category.audiblecamera.main"
        ));
        
        // Register tick event
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // Comprehensive null safety check
            if (client == null || client.player == null || client.world == null) return;
            
            // Handle camera toggle (using direct key detection since mixin intercepts X key)
            boolean xKeyPressed = false;
            if (client.getWindow() != null) {
                long window = client.getWindow().getHandle();
                xKeyPressed = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_X) == GLFW.GLFW_PRESS;
            }
            
            // Don't activate camera if any GUI is open
            if (xKeyPressed && !xKeyPreviouslyPressed && client.currentScreen == null) {
                if (!cameraActive) {
                    // Activate camera at player position (head or feet level)
                    cameraPosition = getStartPosition(client);
                    cameraActive = true;
                    cameraActivationTime = System.currentTimeMillis(); // Prevent immediate movement
                    // Null safety for all player interactions
                    if (client.player != null) {
                        client.player.sendMessage(Text.literal("Audible Camera activated. Use arrows and Page Up/Down to explore!"), true);
                        playSound(client, SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), 1.0f, 1.0f);
                        // Announce starting position
                        announceBlock(client, cameraPosition);
                    }
                                    } else {
                        // Deactivate camera
                        cameraActive = false;
                        if (gamePaused && pauseMode) {
                            // Unpause if we were in pause mode
                            client.setScreen(null);
                            gamePaused = false;
                        }
                        if (client.player != null) {
                            client.player.sendMessage(Text.literal("Audible Camera deactivated."), true);
                            playSound(client, SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), 1.0f, 0.5f);
                        }
                    }
            }
            
            // Update X key state tracking
            xKeyPreviouslyPressed = xKeyPressed;
            
            // Handle Escape key to exit camera (using direct GLFW detection since mixin intercepts it)
            if (cameraActive) {
                boolean[] escapeState = {escapeKeyPreviouslyPressed};
                if (wasKeyJustPressed(client, GLFW.GLFW_KEY_ESCAPE, escapeState)) {
                    escapeKeyPreviouslyPressed = escapeState[0];
                    cameraActive = false;
                    if (gamePaused && pauseMode) {
                        // Unpause if we were in pause mode
                        client.setScreen(null);
                        gamePaused = false;
                    }
                    // Null safety check before sending message
                    if (client.player != null) {
                        client.player.sendMessage(Text.literal("Audible Camera deactivated."), true);
                        playSound(client, SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), 1.0f, 0.5f);
                    }
                } else {
                    escapeKeyPreviouslyPressed = escapeState[0];
                }
            }
            
            // Handle configuration toggles and pause (only when camera is active)
            if (cameraActive) {
                // PERFECT ZERO-CONFLICT SYSTEM: Mixin hijacks ALL these keys when camera is active
                // Other mods never see F4-F12, arrows, Page Up/Down, R, P, ESC when camera is on
                // When camera is off, only X key is intercepted - everything else passes through
                
                // P - Toggle pause mode (using direct GLFW detection since mixin intercepts it)
                boolean[] pState = {pKeyPreviouslyPressed};
                if (wasKeyJustPressed(client, GLFW.GLFW_KEY_P, pState)) {
                    pKeyPreviouslyPressed = pState[0];
                    pauseMode = !pauseMode;
                    if (pauseMode) {
                        if (client.player != null) {
                            client.player.sendMessage(Text.literal("Pause mode enabled. Game will pause during camera use."), true);
                        }
                        // Force pause the game immediately
                        client.setScreen(new GameMenuScreen(true));
                        gamePaused = true;
                    } else {
                        if (client.player != null) {
                            client.player.sendMessage(Text.literal("Pause mode disabled. Camera works while moving."), true);
                        }
                        if (gamePaused) {
                            // Unpause if currently paused
                            client.setScreen(null);
                            gamePaused = false;
                        }
                    }
                } else {
                    pKeyPreviouslyPressed = pState[0];
                }
                
                // F-key configuration toggles (using direct GLFW detection since mixin intercepts them)
                // F6 - Toggle start position
                boolean[] f6State = {f6KeyPreviouslyPressed};
                if (wasKeyJustPressed(client, GLFW.GLFW_KEY_F6, f6State)) {
                    f6KeyPreviouslyPressed = f6State[0];
                    startAtHeadLevel = !startAtHeadLevel;
                    String mode = startAtHeadLevel ? "eye level" : "feet level";
                    if (client.player != null) {
                        client.player.sendMessage(Text.literal("Start position: " + mode), true);
                        playSound(client, SoundEvents.UI_BUTTON_CLICK.value(), 0.5f, 1.0f);
                    }
                } else {
                    f6KeyPreviouslyPressed = f6State[0];
                }
                
                // F7 - Toggle movement mode
                boolean[] f7State = {f7KeyPreviouslyPressed};
                if (wasKeyJustPressed(client, GLFW.GLFW_KEY_F7, f7State)) {
                    f7KeyPreviouslyPressed = f7State[0];
                    cardinalDirections = !cardinalDirections;
                    String mode = cardinalDirections ? "cardinal directions" : "player facing";
                    if (client.player != null) {
                        client.player.sendMessage(Text.literal("Movement mode: " + mode), true);
                        playSound(client, SoundEvents.UI_BUTTON_CLICK.value(), 0.5f, 1.0f);
                    }
                } else {
                    f7KeyPreviouslyPressed = f7State[0];
                }
                
                // F8 - Toggle coordinates
                boolean[] f8State = {f8KeyPreviouslyPressed};
                if (wasKeyJustPressed(client, GLFW.GLFW_KEY_F8, f8State)) {
                    f8KeyPreviouslyPressed = f8State[0];
                    showCoordinates = !showCoordinates;
                    String state = showCoordinates ? "enabled" : "disabled";
                    if (client.player != null) {
                        client.player.sendMessage(Text.literal("Coordinates: " + state), true);
                        playSound(client, SoundEvents.UI_BUTTON_CLICK.value(), 0.5f, 1.0f);
                    }
                } else {
                    f8KeyPreviouslyPressed = f8State[0];
                }
                
                // F5 - Toggle directions
                boolean[] f5State = {f5KeyPreviouslyPressed};
                if (wasKeyJustPressed(client, GLFW.GLFW_KEY_F5, f5State)) {
                    f5KeyPreviouslyPressed = f5State[0];
                    showDirections = !showDirections;
                    String state = showDirections ? "enabled" : "disabled";
                    if (client.player != null) {
                        client.player.sendMessage(Text.literal("Directions: " + state), true);
                        playSound(client, SoundEvents.UI_BUTTON_CLICK.value(), 0.5f, 1.0f);
                    }
                } else {
                    f5KeyPreviouslyPressed = f5State[0];
                }
                
                // F4 - Toggle relative directions
                boolean[] f4State = {f4KeyPreviouslyPressed};
                if (wasKeyJustPressed(client, GLFW.GLFW_KEY_F4, f4State)) {
                    f4KeyPreviouslyPressed = f4State[0];
                    relativeDirections = !relativeDirections;
                    String mode = relativeDirections ? "relative (ahead/behind/left/right)" : "absolute (north/south/east/west)";
                    if (client.player != null) {
                        client.player.sendMessage(Text.literal("Direction mode: " + mode), true);
                        playSound(client, SoundEvents.UI_BUTTON_CLICK.value(), 0.5f, 1.0f);
                    }
                } else {
                    f4KeyPreviouslyPressed = f4State[0];
                }
                
                // F11 - Toggle solid blocks only
                boolean[] f11State = {f11KeyPreviouslyPressed};
                if (wasKeyJustPressed(client, GLFW.GLFW_KEY_F11, f11State)) {
                    f11KeyPreviouslyPressed = f11State[0];
                    solidBlocksOnly = !solidBlocksOnly;
                    String state = solidBlocksOnly ? "solid blocks only" : "all blocks";
                    if (client.player != null) {
                        client.player.sendMessage(Text.literal("Block filter: " + state), true);
                        playSound(client, SoundEvents.UI_BUTTON_CLICK.value(), 0.5f, 1.0f);
                    }
                } else {
                    f11KeyPreviouslyPressed = f11State[0];
                }
                
                // F10 - Toggle light level announcements
                boolean[] f10State = {f10KeyPreviouslyPressed};
                if (wasKeyJustPressed(client, GLFW.GLFW_KEY_F10, f10State)) {
                    f10KeyPreviouslyPressed = f10State[0];
                    showLightLevel = !showLightLevel;
                    String state = showLightLevel ? "enabled" : "disabled";
                    if (client.player != null) {
                        client.player.sendMessage(Text.literal("Light levels: " + state), true);
                        playSound(client, SoundEvents.UI_BUTTON_CLICK.value(), 0.5f, 1.0f);
                    }
                } else {
                    f10KeyPreviouslyPressed = f10State[0];
                }
                
                // F9 - Scan area
                boolean[] f9State = {f9KeyPreviouslyPressed};
                if (wasKeyJustPressed(client, GLFW.GLFW_KEY_F9, f9State)) {
                    f9KeyPreviouslyPressed = f9State[0];
                    scanArea(client, cameraPosition);
                } else {
                    f9KeyPreviouslyPressed = f9State[0];
                }
                
                // F12 - Announce current position
                boolean[] f12State = {f12KeyPreviouslyPressed};
                if (wasKeyJustPressed(client, GLFW.GLFW_KEY_F12, f12State)) {
                    f12KeyPreviouslyPressed = f12State[0];
                    announceCurrentPosition(client, cameraPosition);
                } else {
                    f12KeyPreviouslyPressed = f12State[0];
                }
            }
            
            // R - Recenter on player (only works when camera is already active)
            if (cameraActive) {
                boolean[] rState = {rKeyPreviouslyPressed};
                if (wasKeyJustPressed(client, GLFW.GLFW_KEY_R, rState)) {
                    rKeyPreviouslyPressed = rState[0];
                    cameraPosition = getStartPosition(client);
                    if (client.player != null) {
                        client.player.sendMessage(Text.literal("Camera recentered on player."), true);
                        playSound(client, SoundEvents.ENTITY_ENDERMAN_TELEPORT, 0.5f, 1.5f);
                        announceBlock(client, cameraPosition);
                    }
                } else {
                    rKeyPreviouslyPressed = rState[0];
                }
            }
            
            // Handle camera movement (only when camera is active)
            if (cameraActive) {
                // Pause is handled by P key toggle - no automatic pausing
                
                boolean moved = false;
                BlockPos newPosition = cameraPosition;
                
                // Smart movement with per-direction debouncing
                long currentTime = System.currentTimeMillis();
                
                // Prevent movement immediately after camera activation to avoid auto-movement
                if (currentTime - cameraActivationTime < ACTIVATION_DELAY_MS) {
                    return; // Skip movement processing for 200ms after activation
                }
                
                // BULLETPROOF MOVEMENT LOGIC - using direct GLFW detection since mixin intercepts these keys
                
                // Update all key states first
                boolean[] pageUpState = {pageUpKeyPreviouslyPressed};
                boolean[] pageDownState = {pageDownKeyPreviouslyPressed};
                boolean[] leftState = {leftKeyPreviouslyPressed};
                boolean[] rightState = {rightKeyPreviouslyPressed};
                boolean[] upState = {upKeyPreviouslyPressed};
                boolean[] downState = {downKeyPreviouslyPressed};
                
                boolean pageUpPressed = wasKeyJustPressed(client, GLFW.GLFW_KEY_PAGE_UP, pageUpState);
                boolean pageDownPressed = wasKeyJustPressed(client, GLFW.GLFW_KEY_PAGE_DOWN, pageDownState);
                boolean leftPressed = wasKeyJustPressed(client, GLFW.GLFW_KEY_LEFT, leftState);
                boolean rightPressed = wasKeyJustPressed(client, GLFW.GLFW_KEY_RIGHT, rightState);
                boolean upPressed = wasKeyJustPressed(client, GLFW.GLFW_KEY_UP, upState);
                boolean downPressed = wasKeyJustPressed(client, GLFW.GLFW_KEY_DOWN, downState);
                
                // Update state tracking
                pageUpKeyPreviouslyPressed = pageUpState[0];
                pageDownKeyPreviouslyPressed = pageDownState[0];
                leftKeyPreviouslyPressed = leftState[0];
                rightKeyPreviouslyPressed = rightState[0];
                upKeyPreviouslyPressed = upState[0];
                downKeyPreviouslyPressed = downState[0];
                
                // Vertical movement (Page Up/Down - completely separate from arrows)
                if (pageUpPressed && currentTime - lastVerticalMovementTime >= MOVEMENT_COOLDOWN_MS) {
                    newPosition = cameraPosition.up();
                    moved = true;
                    lastVerticalMovementTime = currentTime;
                }
                else if (pageDownPressed && currentTime - lastVerticalMovementTime >= MOVEMENT_COOLDOWN_MS) {
                    newPosition = cameraPosition.down();
                    moved = true;
                    lastVerticalMovementTime = currentTime;
                }
                // Left/Right movement (separate cooldown from forward/backward)
                else if (leftPressed && currentTime - lastLeftRightMovementTime >= MOVEMENT_COOLDOWN_MS) {
                    newPosition = getLeftPosition(client, cameraPosition);
                    moved = true;
                    lastLeftRightMovementTime = currentTime;
                }
                else if (rightPressed && currentTime - lastLeftRightMovementTime >= MOVEMENT_COOLDOWN_MS) {
                    newPosition = getRightPosition(client, cameraPosition);
                    moved = true;
                    lastLeftRightMovementTime = currentTime;
                }
                // Forward/backward movement (separate cooldown from left/right)
                else if (upPressed && currentTime - lastForwardBackwardMovementTime >= MOVEMENT_COOLDOWN_MS) {
                    newPosition = getForwardPosition(client, cameraPosition);
                    moved = true;
                    lastForwardBackwardMovementTime = currentTime;
                }
                else if (downPressed && currentTime - lastForwardBackwardMovementTime >= MOVEMENT_COOLDOWN_MS) {
                    newPosition = getBackwardPosition(client, cameraPosition);
                    moved = true;
                    lastForwardBackwardMovementTime = currentTime;
                }
                
                if (moved) {
                    // Check if new position is within 16 block range
                    if (client.player == null) return; // Micro-safety check before getBlockPos()
                    BlockPos playerPos = client.player.getBlockPos();
                    double distance = Math.sqrt(newPosition.getSquaredDistance(playerPos));
                    
                    if (distance <= 16.0) {
                        cameraPosition = newPosition;
                        announceBlock(client, cameraPosition);
                    } else {
                        // Prevent movement beyond range and notify user
                        if (client.player != null) {
                            client.player.sendMessage(Text.literal("Camera range limit reached (16 blocks)"), true);
                            playSound(client, SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), 0.5f, 0.8f);
                        }
                    }
                }
            }
        });
    }
    
    private BlockPos getStartPosition(MinecraftClient client) {
        if (client.player == null) return BlockPos.ORIGIN;
        if (startAtHeadLevel) {
            // Use actual eye position for true head level
            Vec3d eyePos = client.player.getEyePos();
            return new BlockPos((int)eyePos.x, (int)eyePos.y, (int)eyePos.z);
        } else {
            // Feet level = block player is standing on
            return client.player.getBlockPos().down();
        }
    }
    
    private BlockPos getForwardPosition(MinecraftClient client, BlockPos pos) {
        if (client.player == null) return pos.north();
        
        // Forward movement is always horizontal only, regardless of eye/foot level
        if (cardinalDirections) {
            return pos.north();
        } else {
            // Use player's horizontal facing direction only
            Direction facing = client.player.getHorizontalFacing();
            return pos.offset(facing);
        }
    }
    
    private BlockPos getBackwardPosition(MinecraftClient client, BlockPos pos) {
        if (client.player == null) return pos.south();
        
        // Backward movement is always horizontal only, regardless of eye/foot level
        if (cardinalDirections) {
            return pos.south();
        } else {
            Direction facing = client.player.getHorizontalFacing().getOpposite();
            return pos.offset(facing);
        }
    }
    
    private BlockPos getLeftPosition(MinecraftClient client, BlockPos pos) {
        if (client.player == null) return pos.west();
        
        // Left/right movement is always horizontal regardless of eye/foot level
        if (cardinalDirections) {
            return pos.west();
        } else {
            Direction facing = client.player.getHorizontalFacing();
            Direction left = facing.rotateYCounterclockwise();
            return pos.offset(left);
        }
    }
    
    private BlockPos getRightPosition(MinecraftClient client, BlockPos pos) {
        if (client.player == null) return pos.east();
        
        // Left/right movement is always horizontal regardless of eye/foot level
        if (cardinalDirections) {
            return pos.east();
        } else {
            Direction facing = client.player.getHorizontalFacing();
            Direction right = facing.rotateYClockwise();
            return pos.offset(right);
        }
    }
    
    private void scanArea(MinecraftClient client, BlockPos center) {
        if (client == null || client.world == null || client.player == null) return;
        
        client.player.sendMessage(Text.literal("Scanning 3x3x3 area..."), true);
        
        // Pre-scan blocks on main thread for thread safety
        java.util.List<ScanResult> scanResults = new java.util.ArrayList<>();
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    BlockPos scanPos = center.add(x, y, z);
                    BlockState blockState = client.world.getBlockState(scanPos);
                    
                    // Skip if solid blocks only and this isn't solid (including air)
                    if (solidBlocksOnly && !blockState.isSolid()) continue;
                    
                    String blockName = blockState.getBlock().getName().getString();
                    if (blockName.toLowerCase().endsWith(" block")) {
                        blockName = blockName.substring(0, blockName.length() - 6);
                    }
                    
                    String direction = "";
                    if (x < 0) direction += "west ";
                    else if (x > 0) direction += "east ";
                    if (y < 0) direction += "down ";
                    else if (y > 0) direction += "up ";
                    if (z < 0) direction += "north ";
                    else if (z > 0) direction += "south ";
                    if (direction.isEmpty()) direction = "center ";
                    
                    scanResults.add(new ScanResult(scanPos, blockState, blockName, direction.trim()));
                }
            }
        }
        
        // Stop any existing scan thread first (prevent thread leaks)
        if (scanThread != null && scanThread.isAlive()) {
            scanThread.interrupt();
        }
        
        // Create properly managed thread for spatial audio scanning  
        scanThread = new Thread(() -> {
            try {
                int delayMs = 500;
                
                for (ScanResult result : scanResults) {
                    // Check if thread was interrupted (clean shutdown)
                    if (Thread.currentThread().isInterrupted()) {
                        return;
                    }
                    
                    // Send message and play positioned sound on main thread (no client capture)
                    MinecraftClient.getInstance().execute(() -> {
                        MinecraftClient currentClient = MinecraftClient.getInstance();
                        if (currentClient != null && currentClient.player != null) {
                            currentClient.player.sendMessage(Text.literal(result.direction + ": " + result.blockName), true);
                            playPositionedSound(currentClient, result.pos, result.blockState);
                        }
                    });
                    Thread.sleep(delayMs);
                }
                
                // Final completion sound (if not interrupted)
                if (!Thread.currentThread().isInterrupted()) {
                    MinecraftClient.getInstance().execute(() -> {
                        MinecraftClient currentClient = MinecraftClient.getInstance();
                        if (currentClient != null && currentClient.player != null) {
                            playSound(currentClient, SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), 0.7f, 1.0f);
                        }
                    });
                }
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // Restore interrupt status
            }
        }, "AudibleCamera-Scanner");
        
        scanThread.setDaemon(true); // Allow JVM to exit cleanly
        scanThread.start();
    }
    
    // Helper class for thread-safe scanning
    private static class ScanResult {
        final BlockPos pos;
        final BlockState blockState;
        final String blockName;
        final String direction;
        
        ScanResult(BlockPos pos, BlockState blockState, String blockName, String direction) {
            this.pos = pos;
            this.blockState = blockState;
            this.blockName = blockName;
            this.direction = direction;
        }
    }
    
    private void announceCurrentPosition(MinecraftClient client, BlockPos pos) {
        if (client.player == null) return;
        
        BlockPos playerPos = client.player.getBlockPos();
        int deltaX = pos.getX() - playerPos.getX();
        int deltaY = pos.getY() - playerPos.getY();
        int deltaZ = pos.getZ() - playerPos.getZ();
        
        double distance = Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ);
        
        StringBuilder announcement = new StringBuilder("Camera position: ");
        
        if (distance > 0.1) {
            announcement.append(String.format("%.1f blocks ", distance));
            
            if (deltaX > 0) announcement.append("east ");
            else if (deltaX < 0) announcement.append("west ");
            
            if (deltaZ > 0) announcement.append("south ");
            else if (deltaZ < 0) announcement.append("north ");
            
            if (deltaY > 0) announcement.append("up ");
            else if (deltaY < 0) announcement.append("down ");
            
            announcement.append("from player. ");
        } else {
            announcement.append("at player position. ");
        }
        
        announcement.append(pos.getX()).append(" ").append(pos.getY()).append(" ").append(pos.getZ());
        
        client.player.sendMessage(Text.literal(announcement.toString()), true);
        playSound(client, SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), 0.5f, 1.2f);
    }
    
    private void announceBlock(MinecraftClient client, BlockPos pos) {
        World world = client.world;
        if (world == null || client.player == null) return;
        
        BlockState blockState = world.getBlockState(pos);
        
        // Skip if solid blocks only and this isn't solid (including air)
        if (solidBlocksOnly && !blockState.isSolid()) {
            // For solid blocks only mode, find the next solid block in the same direction
            BlockPos nextSolidPos = findNextSolidBlock(client, pos);
            if (nextSolidPos != null) {
                // Check if next solid block is within range
                BlockPos playerPos = client.player.getBlockPos();
                double distance = Math.sqrt(nextSolidPos.getSquaredDistance(playerPos));
                if (distance <= 16.0) {
                    cameraPosition = nextSolidPos;
                    announceBlock(client, nextSolidPos);
                } else {
                    if (client.player != null) {
                        client.player.sendMessage(Text.literal("Too far"), true);
                        playSound(client, SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), 0.5f, 0.8f);
                    }
                }
            }
            return;
        }
        
        Block block = blockState.getBlock();
        String blockName = block.getName().getString();
        
        // Remove "Block" suffix if it exists for conciseness
        if (blockName.toLowerCase().endsWith(" block")) {
            blockName = blockName.substring(0, blockName.length() - 6);
        }
        
        // Create concise announcement: "BlockName, distance direction, coords"
        StringBuilder announcement = new StringBuilder(blockName);
        
        // Calculate relative position to player
        BlockPos playerPos = client.player.getBlockPos();
        int deltaX = pos.getX() - playerPos.getX();
        int deltaY = pos.getY() - playerPos.getY();
        int deltaZ = pos.getZ() - playerPos.getZ();
        
        // Add relative direction if enabled and not at player position
        if (showDirections && (deltaX != 0 || deltaY != 0 || deltaZ != 0)) {
            announcement.append(", ");
            
            // Calculate horizontal and vertical distances separately for clearer announcements
            double horizontalDistance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
            int verticalDistance = Math.abs(deltaY);
            
            // Build direction description
            StringBuilder directionDesc = new StringBuilder();
            
            // Add direction (relative to player facing or absolute)
            if (relativeDirections && client.player != null) {
                // Relative directions based on player's facing
                Direction playerFacing = client.player.getHorizontalFacing();
                
                // Calculate relative directions
                switch (playerFacing) {
                    case NORTH: // Player facing north
                        if (deltaX > 0) directionDesc.append("right ");
                        else if (deltaX < 0) directionDesc.append("left ");
                        if (deltaZ > 0) directionDesc.append("behind");
                        else if (deltaZ < 0) directionDesc.append("ahead");
                        break;
                    case SOUTH: // Player facing south
                        if (deltaX > 0) directionDesc.append("left ");
                        else if (deltaX < 0) directionDesc.append("right ");
                        if (deltaZ > 0) directionDesc.append("ahead");
                        else if (deltaZ < 0) directionDesc.append("behind");
                        break;
                    case EAST: // Player facing east
                        if (deltaX > 0) directionDesc.append("ahead");
                        else if (deltaX < 0) directionDesc.append("behind");
                        if (deltaZ > 0) directionDesc.append(" right");
                        else if (deltaZ < 0) directionDesc.append(" left");
                        break;
                    case WEST: // Player facing west
                        if (deltaX > 0) directionDesc.append("behind");
                        else if (deltaX < 0) directionDesc.append("ahead");
                        if (deltaZ > 0) directionDesc.append(" left");
                        else if (deltaZ < 0) directionDesc.append(" right");
                        break;
                }
            } else {
                // Absolute directions (north/south/east/west)
                if (deltaX > 0) directionDesc.append("east ");
                else if (deltaX < 0) directionDesc.append("west ");
                
                if (deltaZ > 0) directionDesc.append("south");
                else if (deltaZ < 0) directionDesc.append("north");
            }
            
            // Add horizontal distance if there's horizontal movement
            if (horizontalDistance > 0.1) {
                announcement.append(String.format("%.1f blocks ", horizontalDistance));
            }
            
            // Add direction
            announcement.append(directionDesc);
            
            // Add vertical distance separately if there's vertical movement
            if (verticalDistance > 0) {
                if (horizontalDistance > 0.1) {
                    announcement.append(" and ");
                }
                announcement.append(verticalDistance).append(" block");
                if (verticalDistance > 1) announcement.append("s");
                if (deltaY > 0) announcement.append(" up");
                else announcement.append(" down");
            }
        }
        
        // Add light level if enabled
        if (showLightLevel) {
            int lightLevel = world.getLightLevel(pos);
            announcement.append(", light ").append(lightLevel);
        }
        
        // Add coordinates if enabled
        if (showCoordinates) {
            announcement.append(", ").append(pos.getX()).append(" ").append(pos.getY()).append(" ").append(pos.getZ());
        }
        
        Text message = Text.literal(announcement.toString());
        client.player.sendMessage(message, true);
        
        // Play positioned sound based on block type and Y level
        playPositionedSound(client, pos, blockState);
    }
    
    private void playPositionedSound(MinecraftClient client, BlockPos pos, BlockState blockState) {
        if (client.player == null || client.world == null) return;
        
        // Choose sound based on block type and material
        // Fixed dirt sound issues: using gravel step sound with enhanced volume and pitch for better spatial audio
        net.minecraft.sound.SoundEvent soundEvent;
        String blockName = blockState.getBlock().getName().getString().toLowerCase();
        
        if (blockState.isAir()) {
            // Air sound - using a soft pop sound that works with spatial audio
            soundEvent = SoundEvents.BLOCK_GLASS_PLACE;
        } else if (blockName.contains("water")) {
            // Water sound for water blocks
            soundEvent = SoundEvents.AMBIENT_UNDERWATER_ENTER;
        } else if (blockName.contains("lava")) {
            // Lava sound for lava blocks (lower pitched)
            soundEvent = SoundEvents.BLOCK_LAVA_POP;
        } else if (blockName.contains("stone") || blockName.contains("cobblestone") || blockName.contains("granite") || 
                   blockName.contains("diorite") || blockName.contains("andesite") || blockName.contains("deepslate")) {
            // Stone materials - use note block sound
            soundEvent = SoundEvents.BLOCK_NOTE_BLOCK_BASS.value();
        } else if (blockName.contains("glass") || blockName.contains("crystal")) {
            // Glass materials - glass ping
            soundEvent = SoundEvents.BLOCK_GLASS_BREAK;
        } else if (blockName.contains("sand")) {
            // Sand materials - sand step sound
            soundEvent = SoundEvents.BLOCK_SAND_STEP;
        } else if (blockName.contains("dirt")) {
            // Dirt materials - use gravel step sound for better spatial audio and volume
            soundEvent = SoundEvents.BLOCK_GRAVEL_STEP;
        } else {
            // Everything else (grass, wood, metal, etc.) - grass step sound
            soundEvent = SoundEvents.BLOCK_GRASS_STEP;
        }
        
        // Calculate pitch based on Y level using half-tone changes
        float basePitch = 1.0f;
        float yOffset = pos.getY() - 64.0f; // Center around sea level
        // Use half-tone changes for more audible pitch differences (each half-tone is ~1.059x)
        float pitch = Math.max(0.3f, Math.min(2.5f, basePitch * (float)Math.pow(1.059, yOffset / 4.0f)));
        
        // Special pitch adjustments for different block types
        if (blockState.isAir()) {
            pitch = 0.8f * (float)Math.pow(1.059, yOffset / 4.0f); // Air pitch changes with Y level using half-tones
        } else if (blockName.contains("lava")) {
            pitch = Math.max(0.2f, pitch * 0.5f); // Lower pitch for lava (danger sound)
        } else if (blockName.contains("water")) {
            pitch = Math.max(0.6f, pitch * 1.2f); // Slightly higher pitch for water
        } else if (blockName.contains("stone") || blockName.contains("cobblestone") || blockName.contains("granite") || 
                   blockName.contains("diorite") || blockName.contains("andesite") || blockName.contains("deepslate")) {
            // Stone blocks get more dramatic pitch changes using half-tones
            pitch = Math.max(0.4f, Math.min(2.0f, 0.8f * (float)Math.pow(1.059, yOffset / 3.0f)));
        } else if (blockName.contains("dirt")) {
            // Dirt blocks get more pronounced pitch changes for better audibility
            pitch = Math.max(0.5f, Math.min(2.2f, 0.9f * (float)Math.pow(1.059, yOffset / 3.5f)));
        }
        
        // Calculate volume based on distance from player position
        if (client.player == null) return; // Safety check before getting position
        BlockPos playerPos = client.player.getBlockPos();
        double distance = Math.sqrt(pos.getSquaredDistance(playerPos));
        
        // Don't play sounds beyond 16 block range
        if (distance > 16.0) {
            return; // Outside audible range
        }
        
        // Volume calculation with gradual falloff - improved for better distance perception
        float volume;
        if (distance <= 4.0) {
            volume = 1.0f; // Full volume very close
        } else if (distance <= 8.0) {
            // Gradual falloff from 1.0 to 0.85 over 4-8 blocks
            volume = 1.0f - (float)(distance - 4.0) * 0.15f / 4.0f;
        } else if (distance <= 16.0) {
            // Gradual falloff from 0.85 to 0.6 over 8-16 blocks
            volume = 0.85f - (float)(distance - 8.0) * 0.25f / 8.0f;
        } else {
            // Should not reach here due to range check above
            volume = 0.6f;
        }
        volume = Math.max(0.5f, Math.min(1.0f, volume)); // Clamp volume to valid range with lower minimum
        
        // Simplified volume multipliers for the new sound categories
        if (blockState.isAir()) {
            volume *= 0.6f; // Softer air volume for the new sound
        } else if (blockName.contains("lava")) {
            volume *= 1.2f; // Louder lava (danger warning)
        } else if (blockName.contains("stone") || blockName.contains("cobblestone") || blockName.contains("granite") || 
                   blockName.contains("diorite") || blockName.contains("andesite") || blockName.contains("deepslate")) {
            volume *= 1.0f; // Full stone volume
        } else if (blockName.contains("glass") || blockName.contains("crystal")) {
            volume *= 0.8f; // Louder glass volume
        } else if (blockName.contains("sand")) {
            volume *= 1.0f; // Full sand volume
        } else if (blockName.contains("dirt")) {
            volume *= 1.5f; // Boost dirt volume significantly for better audibility
            // Additional boost for dirt at longer distances to ensure audibility
            if (distance > 2.0) {
                volume *= 1.2f; // Extra 20% boost beyond 2 blocks
            }
        } else {
            // Everything else (grass, wood, metal, etc.) - grass volume
            volume *= 1.0f; // Full grass volume
        }
        
        // Play positioned sound for spatial audio
        if (client.world == null) return; // Micro-safety check before world access
        
        // Play sound from the actual block position for proper spatial audio
        Vec3d soundPos = Vec3d.ofCenter(pos);
        client.world.playSound(
            client.player,
            soundPos.x, soundPos.y, soundPos.z,
            soundEvent,
            SoundCategory.MASTER,
            volume,
            pitch
        );
    }
    
    private BlockPos findNextSolidBlock(MinecraftClient client, BlockPos startPos) {
        if (client.world == null || client.player == null) return null;
        
        // We need to determine the direction of movement
        // Since we don't have the previous position easily accessible, 
        // we'll search in the direction the player is facing first, then expand
        Direction playerFacing = client.player.getHorizontalFacing();
        
        // Search in the direction the player is facing first
        for (int distance = 1; distance <= 16; distance++) {
            BlockPos candidate = startPos.offset(playerFacing, distance);
            BlockState blockState = client.world.getBlockState(candidate);
            if (blockState.isSolid()) {
                return candidate;
            }
        }
        
        // If no solid block found in facing direction, search in all horizontal directions
        for (int distance = 1; distance <= 16; distance++) {
            for (Direction dir : Direction.Type.HORIZONTAL) {
                if (dir == playerFacing) continue; // Already checked
                BlockPos candidate = startPos.offset(dir, distance);
                BlockState blockState = client.world.getBlockState(candidate);
                if (blockState.isSolid()) {
                    return candidate;
                }
            }
        }
        
        // If still no solid block found, check vertical directions
        for (int distance = 1; distance <= 16; distance++) {
            BlockPos upCandidate = startPos.up(distance);
            BlockPos downCandidate = startPos.down(distance);
            
            if (client.world.getBlockState(upCandidate).isSolid()) {
                return upCandidate;
            }
            if (client.world.getBlockState(downCandidate).isSolid()) {
                return downCandidate;
            }
        }
        
        return null; // No solid block found within range
    }
    
    private void playSound(MinecraftClient client, net.minecraft.sound.SoundEvent sound, float volume, float pitch) {
        if (client.player != null) {
            client.player.playSound(sound, volume, pitch);
        }
    }
    
    // Page Up/Down system eliminates need for Alt key detection - much simpler and bulletproof!
    
    // Public method for the mixin
    public static boolean isCameraActive() {
        return cameraActive;
    }
}
