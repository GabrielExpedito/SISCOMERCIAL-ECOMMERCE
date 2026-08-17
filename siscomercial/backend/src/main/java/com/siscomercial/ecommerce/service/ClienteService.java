package com.siscomercial.ecommerce.service;

import com.siscomercial.ecommerce.exception.RecursoNaoEncontradoException;
import com.siscomercial.ecommerce.exception.RegraNegocioException;
import com.siscomercial.ecommerce.model.Cliente;
import com.siscomercial.ecommerce.model.Endereco;
import com.siscomercial.ecommerce.repository.ClienteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** RF003 - 7.2/7.3/7.4 Cadastro/login do cliente e enderecos. */
@Service
@RequiredArgsConstructor
public class ClienteService {

    private final ClienteRepository clienteRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Transactional
    public Cliente cadastrar(Cliente cliente, String senhaPura) {
        if (clienteRepository.existsByEmail(cliente.getEmail())) {
            throw new RegraNegocioException("Ja existe um cliente cadastrado com este e-mail.");
        }
        cliente.setSenhaHash(passwordEncoder.encode(senhaPura));
        return clienteRepository.save(cliente);
    }

    public Cliente buscarPorId(Long id) {
        return clienteRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cliente nao encontrado: id " + id));
    }

    public Cliente buscarPorEmail(String email) {
        return clienteRepository.findByEmail(email)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cliente nao encontrado: " + email));
    }

    @Transactional
    public Endereco adicionarEndereco(Long clienteId, Endereco endereco) {
        Cliente cliente = buscarPorId(clienteId);
        endereco.setCliente(cliente);
        if (cliente.getEnderecos().isEmpty()) {
            endereco.setPrincipal(true);
        }
        cliente.getEnderecos().add(endereco);
        clienteRepository.save(cliente);
        return endereco;
    }
}
