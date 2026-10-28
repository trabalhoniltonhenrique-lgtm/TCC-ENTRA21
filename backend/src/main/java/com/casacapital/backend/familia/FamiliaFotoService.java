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
 * Foto da família. A imagem recebida é aberta, reduzida para no máximo {@value #LADO_MAXIMO} px e regravada
 * (JPEG, ou PNG quando tem partes transparentes, como um brasão com o fundo removido): assim só imagens de verdade
 * entram no banco, cada foto ocupa poucas centenas de KB e saem os metadados da câmera (inclusive a localização
 * GPS de onde a foto foi tirada).
 */
@Service
public class FamiliaFotoService {

    static final int LADO_MAXIMO = 1024;
    static final int TAMANHO_MAXIMO = 15 * 1024 * 1024;
    static final long PIXELS_MAXIMO = 50_000_000L;

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
        Imagem imagem = normalizar(arquivo);

        Familia familia = familiaService.buscar(familiaId);
        FamiliaFoto foto = fotos.findById(familiaId).orElseGet(() -> new FamiliaFoto(familiaId));
        foto.setTipo(imagem.tipo());
        foto.setConteudo(imagem.conteudo());
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

    /** Imagem já pronta para guardar: o conteúdo e o tipo (image/jpeg ou image/png). */
    record Imagem(byte[] conteudo, String tipo) {
    }

    /**
     * Abre a imagem (JPEG, PNG, GIF ou BMP) e reduz se for grande. Com partes transparentes, devolve PNG (mantém a
     * transparência); sem, devolve JPEG, bem menor para fotos.
     */
    static Imagem normalizar(byte[] arquivo) {
        BufferedImage original = ler(arquivo);

        double escala = Math.min(1.0, (double) LADO_MAXIMO / Math.max(original.getWidth(), original.getHeight()));
        int largura = Math.max(1, (int) Math.round(original.getWidth() * escala));
        int altura = Math.max(1, (int) Math.round(original.getHeight() * escala));

        BufferedImage reduzida = redesenhar(original, largura, altura, BufferedImage.TYPE_INT_ARGB, null);
        if (temTransparencia(reduzida)) {
            ByteArrayOutputStream saida = new ByteArrayOutputStream();
            try {
                ImageIO.write(reduzida, "png", saida);
            } catch (IOException e) {
                throw new IllegalStateException("Falha ao gravar a foto em PNG", e);
            }
            return new Imagem(saida.toByteArray(), "image/png");
        }
        return new Imagem(paraJpeg(redesenhar(original, largura, altura, BufferedImage.TYPE_INT_RGB, Color.WHITE)),
                "image/jpeg");
    }

    private static BufferedImage redesenhar(BufferedImage original, int largura, int altura, int tipo, Color fundo) {
        BufferedImage destino = new BufferedImage(largura, altura, tipo);
        Graphics2D g = destino.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            if (fundo != null) {
                g.setColor(fundo);
                g.fillRect(0, 0, largura, altura);
            }
            g.drawImage(original, 0, 0, largura, altura, null);
        } finally {
            g.dispose();
        }
        return destino;
    }

    private static boolean temTransparencia(BufferedImage argb) {
        for (int y = 0; y < argb.getHeight(); y++) {
            for (int x = 0; x < argb.getWidth(); x++) {
                if ((argb.getRGB(x, y) >>> 24) < 250) return true;
            }
        }
        return false;
    }

    private static byte[] paraJpeg(BufferedImage rgb) {
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
