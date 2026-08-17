package com.siscomercial.ecommerce;

import com.siscomercial.ecommerce.model.*;
import com.siscomercial.ecommerce.repository.ClienteRepository;
import com.siscomercial.ecommerce.service.PedidoService;
import com.siscomercial.ecommerce.service.ProdutoService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.math.BigDecimal;
import java.util.List;

@SpringBootApplication
public class SiscomercialApplication {

    public static void main(String[] args) {
        SpringApplication.run(SiscomercialApplication.class, args);
    }

    /**
     * Popula dados de exemplo no perfil "local" (H2 em memoria), para permitir
     * testar o agente de IA imediatamente (ex: "consulte o produto PROD-001",
     * "qual o status do pedido SIS-2026-000001?").
     */
    @Bean
    CommandLineRunner dadosDeExemplo(ProdutoService produtoService, ClienteRepository clienteRepository,
                                      PedidoService pedidoService, @Value("${spring.profiles.active:local}") String perfil) {
        return args -> {
            if (!"local".equals(perfil)) {
                return;
            }

            Produto p1 = new Produto();
            p1.setCodigoInterno("PROD-001");
            p1.setNome("Cadeira Gamer Siscomercial X1");
            p1.setDescricao("Cadeira ergonomica com apoio lombar ajustavel.");
            p1.setPrecoVenda(new BigDecimal("1299.90"));
            p1.setPrecoPromocional(new BigDecimal("1099.90"));
            p1.setQuantidadeEstoque(25);
            p1.setCategoria("Moveis");
            produtoService.criar(p1);

            Produto p2 = new Produto();
            p2.setCodigoInterno("PROD-002");
            p2.setNome("Monitor 27\" 144Hz");
            p2.setDescricao("Monitor gamer Full HD, 144Hz, 1ms.");
            p2.setPrecoVenda(new BigDecimal("1899.00"));
            p2.setQuantidadeEstoque(10);
            p2.setCategoria("Eletronicos");
            produtoService.criar(p2);

            Cliente cliente = new Cliente();
            cliente.setNomeRazaoSocial("Cliente de Teste");
            cliente.setCpfCnpj("000.000.000-00");
            cliente.setEmail("teste@siscomercial.com.br");
            cliente.setSenhaHash(new BCryptPasswordEncoder().encode("123456"));
            clienteRepository.save(cliente);

            Endereco endereco = new Endereco();
            endereco.setCliente(cliente);
            endereco.setCep("13480-000");
            endereco.setLogradouro("Rua Exemplo");
            endereco.setNumero("100");
            endereco.setBairro("Centro");
            endereco.setCidade("Limeira");
            endereco.setEstado("SP");
            endereco.setPrincipal(true);
            cliente.getEnderecos().add(endereco);
            clienteRepository.save(cliente);

            pedidoService.criarPedido(
                    cliente,
                    List.of(new PedidoService.ItemCarrinho(1L, 2)),
                    endereco,
                    FormaPagamento.PIX
            );

            System.out.println("=== Dados de exemplo carregados ===");
            System.out.println("Produtos: PROD-001, PROD-002");
            System.out.println("Cliente: teste@siscomercial.com.br");
            System.out.println("Pedido de exemplo: SIS-" + java.time.Year.now() + "-000001 (aguardando pagamento)");
            System.out.println("Teste o agente em POST /api/ia/chat com: {\"mensagem\": \"qual o status do pedido SIS-" + java.time.Year.now() + "-000001?\"}");
        };
    }
}
