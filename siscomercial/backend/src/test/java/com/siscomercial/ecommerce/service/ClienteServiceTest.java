package com.siscomercial.ecommerce.service;

import com.siscomercial.ecommerce.model.Cliente;
import com.siscomercial.ecommerce.model.PerfilUsuario;
import com.siscomercial.ecommerce.model.DTO.CadastroClienteRequest;
import com.siscomercial.ecommerce.repository.ClienteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClienteServiceTest {
    private ClienteRepository repository;
    private BCryptPasswordEncoder passwordEncoder;
    private ClienteService service;

    @BeforeEach
    void setUp() {
        repository = mock(ClienteRepository.class);
        passwordEncoder = mock(BCryptPasswordEncoder.class);
        service = new ClienteService(repository, passwordEncoder);
    }

    @Test
    void cadastroPublicoCriaNovaEntidadeComPerfilClienteEIdDoServidor() {
        CadastroClienteRequest request = new CadastroClienteRequest(
                new CadastroClienteRequest.DadosCadastroCliente(
                        "Maria Cliente", "12345678901", "maria@example.test", "11999990000",
                        "Maria MEI", "ISENTO"),
                "senha-ficticia");
        when(repository.existsByEmail("maria@example.test")).thenReturn(false);
        when(passwordEncoder.encode("senha-ficticia")).thenReturn("hash-ficticio");
        when(repository.save(any(Cliente.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Cliente cadastrado = service.cadastrar(request);

        ArgumentCaptor<Cliente> captor = ArgumentCaptor.forClass(Cliente.class);
        verify(repository).save(captor.capture());
        Cliente persistido = captor.getValue();
        assertNull(persistido.getId());
        assertEquals("Maria Cliente", persistido.getNomeRazaoSocial());
        assertEquals("maria@example.test", persistido.getEmail());
        assertEquals("hash-ficticio", persistido.getSenhaHash());
        assertEquals("ATIVO", persistido.getStatus());
        assertEquals(PerfilUsuario.CLIENTE, persistido.getPerfil());
        assertEquals(PerfilUsuario.CLIENTE, cadastrado.getPerfil());
    }

    @Test
    void novoCadastroGoogleRecebePerfilClienteDefinidoNoServidor() {
        when(repository.findByGoogleId("google-sub-ficticio")).thenReturn(Optional.empty());
        when(repository.findByEmail("google@example.test")).thenReturn(Optional.empty());
        when(repository.save(any(Cliente.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Cliente cadastrado = service.cadastrarOuBuscarGoogle(
                "google-sub-ficticio", "google@example.test", "Google Cliente");

        assertEquals(PerfilUsuario.CLIENTE, cadastrado.getPerfil());
        assertEquals("google@example.test", cadastrado.getEmail());
        verify(repository).save(any(Cliente.class));
    }

    @Test
    void associarGoogleAClienteExistentePreservaPerfilPersistido() {
        Cliente admin = new Cliente();
        admin.setEmail("admin@example.test");
        admin.setPerfil(PerfilUsuario.ADMIN);
        when(repository.findByGoogleId("google-sub-admin")).thenReturn(Optional.empty());
        when(repository.findByEmail("admin@example.test")).thenReturn(Optional.of(admin));
        when(repository.save(admin)).thenReturn(admin);

        Cliente resultado = service.cadastrarOuBuscarGoogle(
                "google-sub-admin", "admin@example.test", "Admin");

        assertEquals(PerfilUsuario.ADMIN, resultado.getPerfil());
        verify(repository).save(admin);
    }
}
