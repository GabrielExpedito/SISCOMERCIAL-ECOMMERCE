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
import java.util.ArrayList;
import com.siscomercial.ecommerce.model.Endereco;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
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

    @Test
    void clienteNaoConsultaCadastroAlheioEConsultaEhNegadaSemBuscarDadosDoAlvo() {
        Cliente clienteA = cliente("google-a", 1L);
        when(repository.findByGoogleId("google-a")).thenReturn(Optional.of(clienteA));
        OidcUser usuarioA = usuario("google-a");

        assertThrows(com.siscomercial.ecommerce.exception.RecursoNaoEncontradoException.class,
                () -> service.buscarAutorizado(2L, usuarioA));
        verify(repository, never()).findById(2L);
    }

    @Test
    void clienteNaoAdicionaEnderecoAoCadastroAlheioNemPersiste() {
        when(repository.findByGoogleId("google-a")).thenReturn(Optional.of(cliente("google-a", 1L)));

        assertThrows(com.siscomercial.ecommerce.exception.RecursoNaoEncontradoException.class,
                () -> service.adicionarEndereco(2L, new Endereco(), usuario("google-a")));
        verify(repository, never()).save(any(Cliente.class));
    }

    @Test
    void administradorMantemConsultaDeCadastroAlheio() {
        Cliente admin = cliente("google-admin", 9L);
        admin.setPerfil(PerfilUsuario.ADMIN);
        Cliente alvo = cliente("google-b", 2L);
        when(repository.findByGoogleId("google-admin")).thenReturn(Optional.of(admin));
        when(repository.findById(2L)).thenReturn(Optional.of(alvo));

        assertEquals(alvo, service.buscarAutorizado(2L, usuario("google-admin")));
    }

    @Test
    void clienteConsultaProprioCadastroEAdicionaEnderecoComProprietarioDefinidoPeloServidor() {
        Cliente clienteA = cliente("google-a", 1L);
        when(repository.findByGoogleId("google-a")).thenReturn(Optional.of(clienteA));
        when(repository.findById(1L)).thenReturn(Optional.of(clienteA));
        when(repository.save(clienteA)).thenReturn(clienteA);

        assertEquals(clienteA, service.buscarAutorizado(1L, usuario("google-a")));
        Endereco recebido = new Endereco();
        recebido.setId(88L);
        recebido.setCliente(cliente("google-b", 2L));
        recebido.setCep("01001-000");

        Endereco salvo = service.adicionarEndereco(1L, recebido, usuario("google-a"));

        assertNull(salvo.getId());
        assertEquals(1L, salvo.getCliente().getId());
        assertEquals(1, clienteA.getEnderecos().size());
        verify(repository).save(clienteA);
    }

    private OidcUser usuario(String subject) {
        OidcUser user = mock(OidcUser.class);
        when(user.getSubject()).thenReturn(subject);
        return user;
    }

    private Cliente cliente(String googleId, Long id) {
        Cliente cliente = new Cliente();
        cliente.setGoogleId(googleId);
        cliente.setId(id);
        cliente.setEmail(googleId + "@example.test");
        cliente.setPerfil(PerfilUsuario.CLIENTE);
        cliente.setEnderecos(new ArrayList<>());
        return cliente;
    }
}
