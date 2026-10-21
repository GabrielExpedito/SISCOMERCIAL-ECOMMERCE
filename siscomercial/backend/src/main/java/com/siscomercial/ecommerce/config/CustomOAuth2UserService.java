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
        OidcUser googleUser = super.loadUser(userRequest);

        String googleId = googleUser.getAttribute("sub");
        String email = googleUser.getAttribute("email");
        String nome = googleUser.getAttribute("name");

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
