package com.chenweikeng.mcparks;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import org.lwjgl.system.JNI;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.macosx.ObjCRuntime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Replaces the macOS Dock icon with the MCParks castle logo while connected
 * to a MCParks server, and restores the original icon on disconnect.
 *
 * <p>Uses LWJGL's Objective-C runtime bindings to call Cocoa's
 * {@code NSApplication.setApplicationIconImage:} directly. This avoids any
 * dependency on {@code java.awt} (which Minecraft doesn't initialise).
 * No-ops on non-macOS platforms.
 */
public final class MacosDockIconHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger("MCParksDockIcon");
    private static final String ICON_RESOURCE =
        "/assets/my-mcparks-experience/dock-icon.png";

    private static final boolean IS_MAC;
    static {
        String os = System.getProperty("os.name", "").toLowerCase();
        IS_MAC = os.contains("mac") || os.contains("darwin");
    }

    /** Saved pointer to the original NSImage so we can restore on disconnect. */
    private static long originalIcon = 0;
    private static boolean applied = false;

    private MacosDockIconHandler() {}

    /** Set the MCParks Dock icon. Called when joining a MCParks server. */
    public static void apply() {
        if (!IS_MAC || applied) return;

        try {
            // 1. Read the PNG bytes from the classpath resource
            byte[] pngBytes;
            try (InputStream in = MacosDockIconHandler.class.getResourceAsStream(ICON_RESOURCE)) {
                if (in == null) {
                    LOGGER.warn("Dock icon resource missing: {}", ICON_RESOURCE);
                    return;
                }
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) != -1) bos.write(buf, 0, n);
                pngBytes = bos.toByteArray();
            }

            // 2. Get the objc_msgSend function pointer
            long msgSend = ObjCRuntime.getLibrary().getFunctionAddress("objc_msgSend");
            if (msgSend == 0) {
                LOGGER.warn("objc_msgSend not found; cannot set Dock icon");
                return;
            }

            // 3. Get NSApplication and save the original icon
            long nsAppClass = ObjCRuntime.objc_getClass("NSApplication");
            long selSharedApp = ObjCRuntime.sel_registerName("sharedApplication");
            long app = JNI.invokePPP(nsAppClass, selSharedApp, msgSend);

            long selGetIcon = ObjCRuntime.sel_registerName("applicationIconImage");
            originalIcon = JNI.invokePPP(app, selGetIcon, msgSend);

            // 4. Build NSData from the PNG bytes
            long nsDataClass = ObjCRuntime.objc_getClass("NSData");
            long selDataWithBytesLength = ObjCRuntime.sel_registerName("dataWithBytes:length:");

            long pngPtr = MemoryUtil.nmemAlloc(pngBytes.length);
            try {
                for (int i = 0; i < pngBytes.length; i++) {
                    MemoryUtil.memPutByte(pngPtr + i, pngBytes[i]);
                }

                // [NSData dataWithBytes:pngPtr length:pngBytes.length]
                long nsData = JNI.invokePPPP(nsDataClass, selDataWithBytesLength,
                    pngPtr, pngBytes.length, msgSend);
                if (nsData == 0) {
                    LOGGER.warn("Failed to create NSData from PNG bytes");
                    return;
                }

                // 5. Build NSImage from the NSData
                long nsImageClass = ObjCRuntime.objc_getClass("NSImage");
                long selAlloc = ObjCRuntime.sel_registerName("alloc");
                long selInitWithData = ObjCRuntime.sel_registerName("initWithData:");

                long nsImageRaw = JNI.invokePPP(nsImageClass, selAlloc, msgSend);
                long nsImage = JNI.invokePPPP(nsImageRaw, selInitWithData, nsData, msgSend);
                if (nsImage == 0) {
                    LOGGER.warn("Failed to create NSImage from PNG data");
                    return;
                }

                // 6. Set the Dock icon
                long selSetIcon = ObjCRuntime.sel_registerName("setApplicationIconImage:");
                JNI.invokePPPV(app, selSetIcon, nsImage, msgSend);

                applied = true;
                LOGGER.info("Replaced macOS Dock icon with MCParks castle logo");
            } finally {
                MemoryUtil.nmemFree(pngPtr);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to set macOS Dock icon", e);
        }
    }

    /** Restore the original Dock icon. Called on server disconnect. */
    public static void reset() {
        if (!IS_MAC || !applied) return;

        try {
            long msgSend = ObjCRuntime.getLibrary().getFunctionAddress("objc_msgSend");
            if (msgSend == 0) return;

            long nsAppClass = ObjCRuntime.objc_getClass("NSApplication");
            long selSharedApp = ObjCRuntime.sel_registerName("sharedApplication");
            long app = JNI.invokePPP(nsAppClass, selSharedApp, msgSend);

            long selSetIcon = ObjCRuntime.sel_registerName("setApplicationIconImage:");
            JNI.invokePPPV(app, selSetIcon, originalIcon, msgSend);

            applied = false;
            LOGGER.info("Restored original macOS Dock icon");
        } catch (Exception e) {
            LOGGER.warn("Failed to restore macOS Dock icon", e);
        }
    }
}
