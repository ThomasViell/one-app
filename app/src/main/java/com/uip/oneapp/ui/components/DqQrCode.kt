package com.uip.oneapp.ui.components

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Rendert [content] als QR-Code (ZXing-Core). Immer schwarz-auf-weiß mit weißem Rand auf
 * weißer Karte — auch im Dark-Theme scanbar (Dual-Modus, Welle 3a: WIFI-QR der Tablet-Kopplung).
 */
@Composable
fun DqQrCode(
    content: String,
    modifier: Modifier = Modifier,
    size: Dp = 240.dp,
    quietZone: Dp = 12.dp,
) {
    val bitmap = remember(content) { runCatching { encodeQr(content) }.getOrNull() }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = modifier
                .clip(RoundedCornerShape(8.dp))
                .background(androidx.compose.ui.graphics.Color.White)
                .padding(quietZone)
                .size(size),
        )
    }
}

/** ZXing-Encode (rein, ohne Compose): BitMatrix → ARGB-Bitmap. */
private fun encodeQr(content: String, pixels: Int = 600): Bitmap {
    val hints = mapOf(
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
        EncodeHintType.MARGIN to 1,
    )
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, pixels, pixels, hints)
    val w = matrix.width
    val h = matrix.height
    val pix = IntArray(w * h)
    for (y in 0 until h) {
        val row = y * w
        for (x in 0 until w) {
            pix[row + x] = if (matrix.get(x, y)) Color.BLACK else Color.WHITE
        }
    }
    return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
        setPixels(pix, 0, w, 0, 0, w, h)
    }
}
