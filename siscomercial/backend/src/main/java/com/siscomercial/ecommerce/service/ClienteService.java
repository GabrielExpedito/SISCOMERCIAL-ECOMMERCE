package com.siscomercial.ecommerce.service;

import com.siscomercial.ecommerce.exception.RecursoNaoEncontradoException;
import com.siscomercial.ecommerce.exception.RegraNegocioException;
import com.siscomercial.ecommerce.model.Cliente;
import com.siscomercial.ecommerce.model.Endereco;
import com.siscomercial.ecommerce.model.PerfilUsuario;
import com.siscomercial.ecommerce.model.DTO.CadastroClienteRequest;
import com.siscomercial.ecommerce.repository.ClienteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

/**
 * RF003 - 7.2/7.3/7.4 Cadastro/login do cliente e enderecos.
 */
@Service
@RequiredArgsConstructor
public class ClienteService {

    private final ClienteRepository clienteRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    @Transactional
    public Cliente cadastrar(CadastroClienteRequest request) {
        if (request == null || request.cliente() == null) {
            throw new RegraNegocioException("Informe os dados do cliente para cadastro.");
        }
        CadastroClienteRequest.DadosCadastroCliente dados = request.cliente();
        Cliente cliente = new Cliente();
        cliente.setNomeRazaoSocial(dados.nomeRazaoSocial());
        cliente.setCpfCnpj(dados.cpfCnpj());
        cliente.setEmail(dados.email());
        cliente.setTelefone(dados.telefone());
        cliente.setNomeFantasia(dados.nomeFantasia());
        cliente.setInscricaoEstadual(dados.inscricaoEstadual());
        // Identidade, status, autenticação e perfil são definidos pelo servidor.
        cliente.setPerfil(PerfilUsuario.CLIENTE);
        if (clienteRepository.existsByEmail(cliente.getEmail())) {
            throw new RegraNegocioException("Ja existe um cliente cadastrado com este e-mail.");
        }
        cliente.setSenhaHash(passwordEncoder.encode(request.senha()));
        return clienteRepository.save(cliente);
    }

    public Cliente buscarPorId(Long id) {
        return clienteRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cliente nao encontrado: id " + id));
    }

    public Cliente buscarPorEmail(String email) {
        return clienteRepository.findByEmail(email)
                .orElseThrow(() ->
                        new RecursoNaoEncontradoException(
                                "Cliente nao encontrado: " + email
                        )
                );
    }

    /** Resolve a conta somente pelo identificador imutavel validado pelo provedor OIDC. */
    public Cliente buscarAutenticado(OidcUser usuario) {
        if (usuario == null || usuario.getSubject() == null || usuario.getSubject().isBlank()) {
            throw new RegraNegocioException("Nao foi possivel identificar a conta autenticada.");
        }
        return clienteRepository.findByGoogleId(usuario.getSubject())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cadastro de cliente nao encontrado."));
    }

    public Cliente buscarAutorizado(Long id, OidcUser usuario) {
        Cliente autenticado = buscarAutenticado(usuario);
        if (autenticado.getPerfil() != PerfilUsuario.ADMIN && !autenticado.getId().equals(id)) {
            throw new RecursoNaoEncontradoException("Cliente nao encontrado.");
        }
        return buscarPorId(id);
    }

    @Transactional
    private Endereco adicionarEnderecoAoCliente(Long clienteId, Endereco endereco) {
        Cliente cliente = buscarPorId(clienteId);
        endereco.setCliente(cliente);
        if (cliente.getEnderecos().isEmpty()) {
            endereco.setPrincipal(true);
        }
        cliente.getEnderecos().add(endereco);
        clienteRepository.save(cliente);
        return endereco;
    }

    @Transactional
    public Endereco adicionarEndereco(Long clienteId, Endereco endereco, OidcUser usuario) {
        Cliente autenticado = buscarAutenticado(usuario);
        if (autenticado.getPerfil() != PerfilUsuario.ADMIN && !autenticado.getId().equals(clienteId)) {
            throw new RecursoNaoEncontradoException("Cliente nao encontrado.");
        }
        if (endereco == null) throw new RegraNegocioException("Informe os dados do endereco.");
        endereco.setId(null);
        endereco.setCliente(null);
        return adicionarEnderecoAoCliente(clienteId, endereco);
    }

    @Transactional
    public Cliente cadastrarOuBuscarGoogle(
            String googleId,
            String email,
            String nome) {
        return clienteRepository.findByGoogleId(googleId)
                .orElseGet(() -> {
                    Cliente clienteExistente = clienteRepository
                            .findByEmail(email)
                            .orElse(null);

                    if (clienteExistente != null) {
                        clienteExistente.setGoogleId(googleId);

                        return clienteRepository.save(clienteExistente);
                    }

                    Cliente novoCliente = new Cliente();

                    novoCliente.setGoogleId(googleId);
                    novoCliente.setEmail(email);
                    novoCliente.setNomeRazaoSocial(nome);
                    novoCliente.setPerfil(PerfilUsuario.CLIENTE);
                    novoCliente.setStatus("ATIVO");

                    return clienteRepository.save(novoCliente);
                });
    }
}
