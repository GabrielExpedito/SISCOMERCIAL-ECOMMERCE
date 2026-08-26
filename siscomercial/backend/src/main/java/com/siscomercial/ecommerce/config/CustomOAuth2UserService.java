package com.siscomercial.ecommerce.config;

import com.siscomercial.ecommerce.model.Cliente;
import com.siscomercial.ecommerce.service.ClienteService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends OidcUserService {

    private final ClienteService clienteService;

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest)
            throws OAuth2AuthenticationException {

        System.out.println("======================================");
        System.out.println("=== CUSTOM OIDC USER SERVICE ===");
        System.out.println("======================================");

        OidcUser googleUser = super.loadUser(userRequest);

        String googleId = googleUser.getAttribute("sub");
        String email = googleUser.getAttribute("email");
        String nome = googleUser.getAttribute("name");

        System.out.println("Google ID: " + googleId);
        System.out.println("Email: " + email);
        System.out.println("Nome: " + nome);

        if (googleId == null || email == null) {
            throw new OAuth2AuthenticationException(
                    new org.springframework.security.oauth2.core.OAuth2Error(
                            "invalid_user_info"
                    ),
                    "Nao foi possivel obter os dados necessarios da conta Google."
            );
        }

        Cliente cliente = clienteService.cadastrarOuBuscarGoogle(
                googleId,
                email,
                nome
        );

        System.out.println("Cliente encontrado/criado:");
        System.out.println("ID: " + cliente.getId());
        System.out.println("Email: " + cliente.getEmail());
        System.out.println("Perfil: " + cliente.getPerfil());

        return new DefaultOidcUser(
                List.of(
                        new SimpleGrantedAuthority(
                                "ROLE_" + cliente.getPerfil().name()
                        )
                ),
                googleUser.getIdToken(),
                googleUser.getUserInfo(),
                "sub"
        );
    }
}