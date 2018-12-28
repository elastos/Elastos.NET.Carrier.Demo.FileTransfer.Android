package org.elastos.app.FileDemo;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.text.TextUtils;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.util.Hashtable;

class QRCodeUtils {
    static Bitmap createQRCodeBitmap(String content)
    {
        return createQRCodeBitmap(content, 480);
    }

    private static Bitmap createQRCodeBitmap(String content, int width)
    {
        return createQRCodeBitmap(content, width, width, "UTF-8", "H", "2", Color.BLACK, Color.WHITE);
    }

    private static Bitmap createQRCodeBitmap(String content, int width, int height, String character_set, String error_correction, String margin, int color_black, int color_white)
    {
        if(TextUtils.isEmpty(content)) {
            return null;
        }

        if(width < 0 || height < 0){
            return null;
        }

        try {
            Hashtable<EncodeHintType, String> hints = new Hashtable<>();
            if(!TextUtils.isEmpty(character_set)) {
                hints.put(EncodeHintType.CHARACTER_SET, character_set);
            }

            if(!TextUtils.isEmpty(error_correction)) {
                hints.put(EncodeHintType.ERROR_CORRECTION, error_correction);
            }

            if(!TextUtils.isEmpty(margin)) {
                hints.put(EncodeHintType.MARGIN, margin);
            }

            BitMatrix bitMatrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, width, height, hints);

            int[] pixels = new int[width * height];
            for(int y = 0; y < height; y++) {
                for(int x = 0; x < width; x++) {
                    if(bitMatrix.get(x, y)) {
                        pixels[y * width + x] = color_black;
                    }
                    else { pixels[y * width + x] = color_white;
                    }
                }
            }

            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
            return bitmap;
        }
        catch (WriterException e) {
            e.printStackTrace();
        }

        return null;
    }
}
