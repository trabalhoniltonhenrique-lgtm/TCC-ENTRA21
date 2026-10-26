package com.casacapital.backend.nfce;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.GlobalHistogramBinarizer;
import com.google.zxing.common.HybridBinarizer;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Lê o QR code de uma foto (o da nota fiscal do mercado). Tenta alguns tamanhos, porque foto de celular varia muito. */
final class LeitorQrCode {

    private static final Map<DecodeHintType, Object> DICAS = Map.of(
            DecodeHintType.POSSIBLE_FORMATS, List.of(BarcodeFormat.QR_CODE),
            DecodeHintType.TRY_HARDER, Boolean.TRUE,
            DecodeHintType.CHARACTER_SET, "UTF-8");

    private LeitorQrCode() {
    }

    /** Texto do QR code, ou vazio se não achar nenhum. IOException se o arquivo não for uma imagem que dá para abrir. */
    static Optional<String> ler(byte[] imagem) throws IOException {
        BufferedImage original = ImageIO.read(new ByteArrayInputStream(imagem));
        if (original == null) throw new IOException("formato de imagem não suportado");

        int maior = Math.max(original.getWidth(), original.getHeight());
        for (int alvo : new int[]{1600, 2400, 1000, 700}) {
            BufferedImage img = maior > alvo ? reduzir(original, alvo) : original;
            Optional<String> texto = decodificar(img);
            if (texto.isPresent()) return texto;
            if (maior <= alvo) break; // já tentou no tamanho original
        }
        return Optional.empty();
    }

    private static Optional<String> decodificar(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        int[] pixels = img.getRGB(0, 0, w, h, null, 0, w);
        LuminanceSource fonte = new RGBLuminanceSource(w, h, pixels);
        for (BinaryBitmap bitmap : List.of(new BinaryBitmap(new HybridBinarizer(fonte)),
                new BinaryBitmap(new GlobalHistogramBinarizer(fonte)))) {
            try {
                Result r = new MultiFormatReader().decode(bitmap, DICAS);
                return Optional.of(r.getText());
            } catch (NotFoundException e) {
                // tenta o próximo
            }
        }
        return Optional.empty();
    }

    private static BufferedImage reduzir(BufferedImage img, int maior) {
        double escala = (double) maior / Math.max(img.getWidth(), img.getHeight());
        int w = Math.max(1, (int) Math.round(img.getWidth() * escala));
        int h = Math.max(1, (int) Math.round(img.getHeight() * escala));
        BufferedImage nova = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = nova.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(img, 0, 0, w, h, null);
        g.dispose();
        return nova;
    }
}
