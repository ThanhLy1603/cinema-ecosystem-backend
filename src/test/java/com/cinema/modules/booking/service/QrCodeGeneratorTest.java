package com.cinema.modules.booking.service;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QrCodeGeneratorTest {

    private final QrCodeGenerator qrCodeGenerator = new QrCodeGenerator();

    private String decode(byte[] pngBytes) throws Exception {
        BufferedImage bufferedImage = ImageIO.read(new ByteArrayInputStream(pngBytes));
        BinaryBitmap binaryBitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(bufferedImage)));
        return new MultiFormatReader().decode(binaryBitmap).getText();
    }

    @Test
    void theQrImageDecodesBackToTheBookingCode() throws Exception {
        byte[] pngBytes = qrCodeGenerator.toPngBytes("BK40B6B97C5B29", QrCodeGenerator.DEFAULT_SIZE);

        assertEquals("BK40B6B97C5B29", decode(pngBytes));
    }

    @Test
    void theDataUrlWrapsTheSameBase64Png() throws Exception {
        String dataUrl = qrCodeGenerator.toPngDataUrl("BK1", QrCodeGenerator.DEFAULT_SIZE);

        assertTrue(dataUrl.startsWith("data:image/png;base64,"));
        assertEquals("BK1", decode(Base64.getDecoder().decode(dataUrl.substring("data:image/png;base64,".length()))));
    }
}
