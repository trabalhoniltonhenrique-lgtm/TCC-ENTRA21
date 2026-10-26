package com.casacapital.backend.rede;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Endereços para abrir o sistema no celular pela rede de casa (Wi-Fi), com o QR code de cada um.
 * O acesso pela rede depende da regra de firewall criada pelo instalador (ACESSO_REDE_LOCAL=SIM).
 */
@RestController
@RequestMapping("/api/rede")
public class RedeController {

    // Adaptadores virtuais (máquinas virtuais, WSL, Docker, VPN) não são a rede de casa
    private static final List<String> VIRTUAIS = List.of("virtual", "vmware", "vbox", "hyper-v", "vethernet", "wsl",
            "docker", "loopback", "bluetooth", "tap-", "tun", "vpn", "zerotier", "tailscale", "hamachi");

    private final int porta;

    public RedeController(@Value("${server.port:8080}") int porta) {
        this.porta = porta;
    }

    public record Endereco(String url, String adaptador, List<String> qr) {
    }

    @GetMapping
    public Map<String, Object> enderecos() throws SocketException {
        List<Endereco> lista = new ArrayList<>();
        for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
            if (!ni.isUp() || ni.isLoopback() || ni.isVirtual() || ehVirtual(ni)) continue;
            for (InetAddress ip : Collections.list(ni.getInetAddresses())) {
                if (ip instanceof Inet4Address && ip.isSiteLocalAddress()) {
                    String url = "http://" + ip.getHostAddress() + ":" + porta;
                    lista.add(new Endereco(url, ni.getDisplayName(), qr(url)));
                }
            }
        }
        return Map.of("porta", porta, "enderecos", lista);
    }

    private static boolean ehVirtual(NetworkInterface ni) {
        String nome = (ni.getDisplayName() + " " + ni.getName()).toLowerCase(Locale.ROOT);
        return VIRTUAIS.stream().anyMatch(nome::contains);
    }

    /** QR code como linhas de "0"/"1" (a tela desenha os quadradinhos). */
    static List<String> qr(String texto) {
        try {
            BitMatrix m = new QRCodeWriter().encode(texto, BarcodeFormat.QR_CODE, 0, 0,
                    Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M, EncodeHintType.MARGIN, 0));
            List<String> linhas = new ArrayList<>();
            for (int y = 0; y < m.getHeight(); y++) {
                StringBuilder sb = new StringBuilder();
                for (int x = 0; x < m.getWidth(); x++) sb.append(m.get(x, y) ? '1' : '0');
                linhas.add(sb.toString());
            }
            return linhas;
        } catch (WriterException e) {
            return List.of();
        }
    }
}
