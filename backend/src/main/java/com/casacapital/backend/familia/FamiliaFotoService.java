package com.casacapital.backend.familia;

import com.casacapital.backend.common.ApiException;
import com.casacapital.backend.common.ResourceNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Iterator;

/**
 * Foto da família. A imagem recebida é aberta, reduzida para no máximo {@value #LADO_MAXIMO} px e regravada em JPEG:
 * assim só imagens de verdade entram no banco, cada foto ocupa poucas centenas de KB e saem os metadados
 * da câmera (inclusive a localização GPS de onde a foto foi tirada).
 */
@Service
public class FamiliaFotoService {

    static final int LADO_MAXIMO = 1024;
    static final int TAMANHO_MAXIMO = 15 * 1024 * 1024;
    static final long PIXELS_MAXIMO = 50_000_000L;
    private static final String TIPO = "image/jpeg";

    private final FamiliaFotoRepository fotos;
    private final FamiliaService familiaService;

    public FamiliaFotoService(FamiliaFotoRepository fotos, FamiliaService familiaService) {
        this.fotos = fotos;
        this.familiaService = familiaService;
    }

    public FamiliaFoto buscar(Long familiaId) {
        return fotos.findById(familiaId)
                .orElseThrow(() -> new ResourceNotFoundException("A família ainda não tem foto."));
    }

    @Transactional
    public Familia salvar(Long familiaId, byte[] arquivo) {
        if (arquivo == null || arquivo.length == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FOTO_VAZIA", "Escolha uma foto.");
        }
        if (arquivo.length > TAMANHO_MAXIMO) {
            throw new ApiException(HttpStatus.CONTENT_TOO_LARGE, "ARQUIVO_GRANDE", "Foto grande demais (máximo 15 MB).");
        }
        byte[] jpeg = paraJpeg(arquivo);

        Familia familia = familiaService.buscar(familiaId);
        FamiliaFoto foto = fotos.findById(familiaId).orElseGet(() -> new FamiliaFoto(familiaId));
        foto.setTipo(TIPO);
        foto.setConteudo(jpeg);
        fotos.save(foto);
        familia.setFotoAtualizadaEm(LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS));
        return familia;
    }

    @Transactional
    public Familia remover(Long familiaId) {
        Familia familia = familiaService.buscar(familiaId);
        fotos.findById(familiaId).ifPresent(fotos::delete);
        familia.setFotoAtualizadaEm(null);
        return familia;
    }

    /** Abre a imagem (JPEG, PNG, GIF ou BMP), reduz se for grande e devolve em JPEG. */
    static byte[] paraJpeg(byte[] arquivo) {
        BufferedImage original = ler(arquivo);

        double escala = Math.min(1.0, (double) LADO_MAXIMO / Math.max(original.getWidth(), original.getHeight()));
        int largura = Math.max(1, (int) Math.round(original.getWidth() * escala));
        int altura = Math.max(1, (int) Math.round(original.getHeight() * escala));

        // JPEG não tem transparência: o fundo transparente de um PNG fica branco
        BufferedImage rgb = new BufferedImage(largura, altura, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, largura, altura);
            g.drawImage(original, 0, 0, largura, altura, null);
        } finally {
            g.dispose();
        }

        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(saida)) {
            writer.setOutput(ios);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(0.85f);
            writer.write(null, new IIOImage(rgb, null, null), param);
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao gravar a foto em JPEG", e);
        } finally {
            writer.dispose();
        }
        return saida.toByteArray();
    }

    /**
     * Confere as dimensões antes de abrir: um arquivo pequeno pode declarar uma imagem gigante
     * (dezenas de milhares de pixels de lado) e esgotar a memória do servidor ao ser aberto.
     */
    private static BufferedImage ler(byte[] arquivo) {
        try (ImageInputStream entrada = ImageIO.createImageInputStream(new ByteArrayInputStream(arquivo))) {
            Iterator<ImageReader> leitores = entrada == null ? null : ImageIO.getImageReaders(entrada);
            if (leitores != null && leitores.hasNext()) {
                ImageReader leitor = leitores.next();
                try {
                    leitor.setInput(entrada, true, true);
                    long largura = leitor.getWidth(0), altura = leitor.getHeight(0);
                    if (largura < 1 || altura < 1) throw invalida();
                    if (largura * altura > PIXELS_MAXIMO) {
                        throw new ApiException(HttpStatus.BAD_REQUEST, "FOTO_GRANDE",
                                "Foto com resolução grande demais. Use uma foto de até 50 megapixels.");
                    }
                    BufferedImage imagem = leitor.read(0);
                    if (imagem != null) return imagem;
                } finally {
                    leitor.dispose();
                }
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof ApiException api) throw api;
        }
        throw invalida();
    }

    private static ApiException invalida() {
        return new ApiException(HttpStatus.BAD_REQUEST, "FOTO_INVALIDA",
                "Não foi possível abrir a imagem. Use uma foto JPG ou PNG.");
    }
}
