package com.casacapital.backend.familia;

import com.casacapital.backend.common.ApiException;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.zip.CRC32;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FamiliaFotoServiceTest {

    @Test
    void fotoGrandeEhReduzidaEGravadaEmJpeg() throws IOException {
        byte[] jpeg = FamiliaFotoService.paraJpeg(imagem(4000, 3000, BufferedImage.TYPE_INT_RGB, "png"));

        assertThat(jpeg[0] & 0xFF).isEqualTo(0xFF); // assinatura do JPEG
        assertThat(jpeg[1] & 0xFF).isEqualTo(0xD8);
        BufferedImage lida = ImageIO.read(new ByteArrayInputStream(jpeg));
        assertThat(lida.getWidth()).isEqualTo(1024);
        assertThat(lida.getHeight()).isEqualTo(768);
    }

    @Test
    void fotoPequenaNaoEhAumentada() throws IOException {
        BufferedImage lida = ImageIO.read(new ByteArrayInputStream(
                FamiliaFotoService.paraJpeg(imagem(300, 400, BufferedImage.TYPE_INT_RGB, "jpg"))));
        assertThat(lida.getWidth()).isEqualTo(300);
        assertThat(lida.getHeight()).isEqualTo(400);
    }

    @Test
    void pngTransparenteFicaComFundoBranco() throws IOException {
        BufferedImage lida = ImageIO.read(new ByteArrayInputStream(
                FamiliaFotoService.paraJpeg(imagem(50, 50, BufferedImage.TYPE_INT_ARGB, "png"))));
        int rgb = lida.getRGB(25, 25);
        assertThat((rgb >> 16) & 0xFF).isGreaterThan(245);
        assertThat(rgb & 0xFF).isGreaterThan(245);
    }

    @Test
    void arquivoQueNaoEhImagemEhRecusado() {
        assertThatThrownBy(() -> FamiliaFotoService.paraJpeg("%PDF-1.7 não é foto".getBytes()))
                .isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo("FOTO_INVALIDA");
    }

    @Test
    void imagemQueDeclaraResolucaoGiganteEhRecusadaSemAbrir() {
        // PNG de poucos bytes que diz ter 60.000 x 60.000 pixels (abrir de verdade esgotaria a memória)
        assertThatThrownBy(() -> FamiliaFotoService.paraJpeg(pngSoCabecalho(60_000, 60_000)))
                .isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo("FOTO_GRANDE");
    }

    @Test
    void salvarGuardaAFotoEMarcaANovaVersao() throws IOException {
        FamiliaFotoRepository repo = mock(FamiliaFotoRepository.class);
        FamiliaService familias = mock(FamiliaService.class);
        Familia familia = new Familia("Família Teste");
        when(familias.buscar(7L)).thenReturn(familia);
        when(repo.findById(7L)).thenReturn(Optional.empty());

        new FamiliaFotoService(repo, familias).salvar(7L, imagem(100, 80, BufferedImage.TYPE_INT_RGB, "png"));

        verify(repo).save(any(FamiliaFoto.class));
        assertThat(familia.getFotoAtualizadaEm()).isNotNull();
    }

    @Test
    void arquivoVazioNaoMexeNaFoto() {
        FamiliaFotoRepository repo = mock(FamiliaFotoRepository.class);
        assertThatThrownBy(() -> new FamiliaFotoService(repo, mock(FamiliaService.class)).salvar(7L, new byte[0]))
                .isInstanceOf(ApiException.class);
        verify(repo, never()).save(any());
    }

    @Test
    void removerApagaEZeraAVersao() {
        FamiliaFotoRepository repo = mock(FamiliaFotoRepository.class);
        FamiliaService familias = mock(FamiliaService.class);
        Familia familia = new Familia("Família Teste");
        familia.setFotoAtualizadaEm(java.time.LocalDateTime.now());
        FamiliaFoto foto = new FamiliaFoto(7L);
        when(familias.buscar(7L)).thenReturn(familia);
        when(repo.findById(7L)).thenReturn(Optional.of(foto));

        new FamiliaFotoService(repo, familias).remover(7L);

        verify(repo).delete(foto);
        assertThat(familia.getFotoAtualizadaEm()).isNull();
    }

    private static byte[] imagem(int largura, int altura, int tipo, String formato) throws IOException {
        BufferedImage img = new BufferedImage(largura, altura, tipo);
        if (tipo == BufferedImage.TYPE_INT_RGB) {
            var g = img.createGraphics();
            g.setColor(java.awt.Color.ORANGE);
            g.fillRect(0, 0, largura, altura);
            g.dispose();
        } // ARGB fica todo transparente
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        ImageIO.write(img, formato, saida);
        return saida.toByteArray();
    }

    /** Assinatura PNG + bloco IHDR com as dimensões pedidas, sem os pixels. */
    private static byte[] pngSoCabecalho(int largura, int altura) {
        ByteBuffer ihdr = ByteBuffer.allocate(17);
        ihdr.put("IHDR".getBytes()).putInt(largura).putInt(altura)
                .put((byte) 8).put((byte) 2).put((byte) 0).put((byte) 0).put((byte) 0);
        CRC32 crc = new CRC32();
        crc.update(ihdr.array());
        return ByteBuffer.allocate(8 + 4 + 17 + 4)
                .put(new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'})
                .putInt(13).put(ihdr.array()).putInt((int) crc.getValue())
                .array();
    }
}
