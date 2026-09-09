package com.siscomercial.ecommerce.marketplace;

import com.siscomercial.ecommerce.exception.RegraNegocioException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/** Criptografa segredos em repouso; a chave fica somente no ambiente do servidor. */
@Service
public class CredencialMarketplaceService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final String chaveBase64;

    public CredencialMarketplaceService(@Value("${siscomercial.marketplace.crypto-key:}") String chaveBase64) {
        this.chaveBase64 = chaveBase64;
    }

    public String proteger(String valor) {
        try {
            byte[] iv = new byte[12]; RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, chave(), new GCMParameterSpec(128, iv));
            byte[] cifrado = cipher.doFinal(valor.getBytes(StandardCharsets.UTF_8));
            byte[] combinado = new byte[iv.length + cifrado.length];
            System.arraycopy(iv, 0, combinado, 0, iv.length);
            System.arraycopy(cifrado, 0, combinado, iv.length, cifrado.length);
            return Base64.getEncoder().encodeToString(combinado);
        } catch (Exception e) { throw new RegraNegocioException("Nao foi possivel proteger a credencial do marketplace."); }
    }

    public String revelar(String valorProtegido) {
        try {
            byte[] combinado = Base64.getDecoder().decode(valorProtegido);
            byte[] iv = java.util.Arrays.copyOfRange(combinado, 0, 12);
            byte[] cifrado = java.util.Arrays.copyOfRange(combinado, 12, combinado.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, chave(), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(cifrado), StandardCharsets.UTF_8);
        } catch (Exception e) { throw new RegraNegocioException("Credencial do marketplace indisponivel ou invalida."); }
    }

    private SecretKeySpec chave() {
        try {
            byte[] bytes = Base64.getDecoder().decode(chaveBase64);
            if (bytes.length != 32) throw new IllegalArgumentException();
            return new SecretKeySpec(bytes, "AES");
        } catch (Exception e) {
            throw new RegraNegocioException("A chave siscomercial.marketplace.crypto-key deve conter 32 bytes em Base64.");
        }
    }
}
