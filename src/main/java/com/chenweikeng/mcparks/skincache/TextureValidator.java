package com.chenweikeng.mcparks.skincache;

import com.chenweikeng.mcparks.MCParksExperienceClient;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 * Validates that a downloaded skin texture is a well-formed PNG with correct Minecraft skin
 * dimensions before it gets written to the cache.
 *
 * <p>Checks performed:
 * 1. Non-null, non-empty byte array
 * 2. PNG magic bytes (89 50 4E 47 0D 0A 1A 0A)
 * 3. Decodable as a valid image via ImageIO
 * 4. Dimensions are either 64x64 (modern) or 64x32 (legacy)
 */
public final class TextureValidator {

    private static final byte[] PNG_MAGIC = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    private static final int[][] VALID_DIMENSIONS = {
        {64, 64},
        {64, 32}
    };

    private TextureValidator() {}

    /**
     * Full validation pipeline. Returns true only if ALL checks pass.
     */
    public static boolean isValid(byte[] data) {
        if (data == null || data.length == 0) {
            MCParksExperienceClient.LOGGER.warn("[SkinCache] Validation failed: empty or null data");
            return false;
        }

        if (!hasPngMagic(data)) {
            MCParksExperienceClient.LOGGER.warn("[SkinCache] Validation failed: not a PNG (bad magic bytes)");
            return false;
        }

        BufferedImage img;
        try {
            img = ImageIO.read(new ByteArrayInputStream(data));
        } catch (IOException e) {
            MCParksExperienceClient.LOGGER.warn("[SkinCache] Validation failed: PNG could not be decoded");
            return false;
        }
        if (img == null) {
            MCParksExperienceClient.LOGGER.warn("[SkinCache] Validation failed: PNG could not be decoded");
            return false;
        }

        int w = img.getWidth();
        int h = img.getHeight();
        for (int[] dim : VALID_DIMENSIONS) {
            if (w == dim[0] && h == dim[1]) return true;
        }

        MCParksExperienceClient.LOGGER.warn(
                "[SkinCache] Validation failed: bad dimensions {}x{} (expected 64x64 or 64x32)", w, h);
        return false;
    }

    private static boolean hasPngMagic(byte[] data) {
        if (data.length < PNG_MAGIC.length) return false;
        for (int i = 0; i < PNG_MAGIC.length; i++) {
            if (data[i] != PNG_MAGIC[i]) return false;
        }
        return true;
    }
}
