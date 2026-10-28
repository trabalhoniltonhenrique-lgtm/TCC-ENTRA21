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
        FamiliaFotoService.Imagem imagem = FamiliaFotoService.normalizar(imagem(4000, 3000, BufferedImage.TYPE_INT_RGB, "png"));
        byte[] jpeg = imagem.conteudo();

        assertThat(imagem.tipo()).isEqualTo("image/jpeg");

        assertThat(jpeg[0] & 0xFF).isEqualTo(0xFF); // assinatura do JPEG
        assertThat(jpeg[1] & 0xFF).isEqualTo(0xD8);
        BufferedImage lida = ImageIO.read(new ByteArrayInputStream(jpeg));
        assertThat(lida.getWidth()).isEqualTo(1024);
        assertThat(lida.getHeight()).isEqualTo(768);
    }

    @Test
    void fotoPequenaNaoEhAumentada() throws IOException {
        BufferedImage lida = ImageIO.read(new ByteArrayInputStream(
                FamiliaFotoService.normalizar(imagem(300, 400, BufferedImage.TYPE_INT_RGB, "jpg")).conteudo()));
        assertThat(lida.getWidth()).isEqualTo(300);
        assertThat(lida.getHeight()).isEqualTo(400);
    }

    @Test
    void pngComTransparenciaContinuaPngTransparente() throws IOException {
        FamiliaFotoService.Imagem imagem = FamiliaFotoService.normalizar(brasaoSemFundo(2000, 3000));
        assertThat(imagem.tipo()).isEqualTo("image/png");
        BufferedImage lida = ImageIO.read(new ByteArrayInputStream(imagem.conteudo()));
        assertThat(lida.getHeight()).isEqualTo(1024); // reduzida também
        assertThat(lida.getRGB(5, 5) >>> 24).isZero(); // canto transparente
        assertThat(lida.getRGB(lida.getWidth() / 2, lida.getHeight() / 2) >>> 24).isEqualTo(255); // desenho opaco
    }

    @Test
    void pngComCanalAlfaMasTodoOpacoViraJpeg() throws IOException {
        // PNG que o navegador gera ao recortar: tem canal alfa, mas nenhum ponto transparente
        BufferedImage img = new BufferedImage(400, 300, BufferedImage.TYPE_INT_ARGB);
        var g = img.createGraphics();
        g.setColor(java.awt.Color.ORANGE);
        g.fillRect(0, 0, 400, 300);
        g.dispose();
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        ImageIO.write(img, "png", saida);
        assertThat(FamiliaFotoService.normalizar(saida.toByteArray()).tipo()).isEqualTo("image/jpeg");
    }

    @Test
    void arquivoQueNaoEhImagemEhRecusado() {
        assertThatThrownBy(() -> FamiliaFotoService.normalizar("%PDF-1.7 não é foto".getBytes()))
                .isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo("FOTO_INVALIDA");
    }

    @Test
    void imagemQueDeclaraResolucaoGiganteEhRecusadaSemAbrir() {
        // PNG de poucos bytes que diz ter 60.000 x 60.000 pixels (abrir de verdade esgotaria a memória)
        assertThatThrownBy(() -> FamiliaFotoService.normalizar(pngSoCabecalho(60_000, 60_000)))
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

    /** PNG com fundo transparente e um "brasão" opaco no meio. */
    private static byte[] brasaoSemFundo(int largura, int altura) throws IOException {
        BufferedImage img = new BufferedImage(largura, altura, BufferedImage.TYPE_INT_ARGB);
        var g = img.createGraphics();
        g.setColor(java.awt.Color.RED);
        g.fillOval(largura / 4, altura / 4, largura / 2, altura / 2);
        g.dispose();
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        ImageIO.write(img, "png", saida);
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
