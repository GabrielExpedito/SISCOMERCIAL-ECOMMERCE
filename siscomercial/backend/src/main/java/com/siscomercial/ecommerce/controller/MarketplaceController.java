package com.siscomercial.ecommerce.controller;

import com.siscomercial.ecommerce.marketplace.IntegracaoMarketplaceService;
import com.siscomercial.ecommerce.marketplace.MarketplaceOrchestratorService;
import com.siscomercial.ecommerce.marketplace.MercadoLivreCategoriaService;
import com.siscomercial.ecommerce.model.IntegracaoMarketplace;
import com.siscomercial.ecommerce.model.PublicacaoMarketplace;
import com.siscomercial.ecommerce.model.DTO.MarketplaceDTOs.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * API administrativa RF007. Retorna somente DTOs, nunca tokens ou credenciais.
 */
@RestController
@RequestMapping("/api/retaguarda/marketplaces")
@RequiredArgsConstructor
public class MarketplaceController {
    private final IntegracaoMarketplaceService integracaoService;
    private final MarketplaceOrchestratorService orchestratorService;
    private final MercadoLivreCategoriaService categoriaService;
    private final com.siscomercial.ecommerce.marketplace.MarketplaceOrderSyncOrchestratorService orderSyncService;

    @GetMapping
    public List<IntegracaoResponse> listar() {
        return integracaoService.listar().stream().map(this::resumo).toList();
    }

    @PostMapping("/mercado-livre")
    public IntegracaoResponse criarMercadoLivre(@RequestBody CriarIntegracaoRequest request) {
        return resumo(integracaoService.criarMercadoLivre(request.lojaProprietaria(), request.identificadorExterno()));
    }

    @PostMapping("/{id}/mercado-livre/autorizacao")
    public UrlAutorizacaoResponse iniciarAutorizacao(@PathVariable Long id) {
        return new UrlAutorizacaoResponse(integracaoService.iniciarAutorizacaoMercadoLivre(id));
    }

    /**
     * Deve corresponder exatamente ao redirect URI registrado na aplicacao Mercado Livre.
     */
    @GetMapping("/mercado-livre/callback")
    public IntegracaoResponse callbackMercadoLivre(@RequestParam String code, @RequestParam String state) {
        return resumo(integracaoService.concluirAutorizacaoMercadoLivre(code, state));
    }

    @PostMapping("/{id}/ativacao")
    public IntegracaoResponse alterarAtivacao(@PathVariable Long id, @RequestBody AlterarAtivacaoRequest request) {
        return resumo(integracaoService.alterarAtivacao(id, request.ativa()));
    }

    @PostMapping("/{id}/diagnostico")
    public IntegracaoResponse diagnosticar(@PathVariable Long id) {
        return resumo(integracaoService.diagnosticar(id));
    }

    /**
     * Sugere categorias reais MLB a partir do titulo/nome do produto.
     */
    @GetMapping("/{id}/mercado-livre/categorias")
    public List<MercadoLivreCategoriaService.CategoriaSugestao> preverCategorias(
            @PathVariable Long id,
            @RequestParam String q) {
        return categoriaService.preverCategorias(integracaoService.buscar(id), q);
    }

    /**
     * Retorna a ficha tecnica da categoria selecionada, incluindo atributos required.
     */
    @GetMapping("/{id}/mercado-livre/categorias/{categoryId}/atributos")
    public List<MercadoLivreCategoriaService.AtributoCategoria> listarAtributosCategoria(
            @PathVariable Long id,
            @PathVariable String categoryId) {
        return categoriaService.listarAtributos(integracaoService.buscar(id), categoryId);
    }

    @PostMapping("/publicacoes")
    public PublicacaoResponse publicar(@RequestBody PublicarProdutoRequest request) {
        return publicacao(orchestratorService.publicar(request.integracaoId(), request.produtoId()));
    }

    /**
     * Sincroniza as vendas do marketplace com o estoque local.
     */
    @PostMapping("/{id}/mercado-livre/sincronizar-pedidos")
    public SincronizacaoPedidosResponse sincronizarPedidos(@PathVariable Long id) {
        var resultado = orderSyncService.sincronizar(id);
        return new SincronizacaoPedidosResponse(
                resultado.consultados(),
                resultado.processados(),
                resultado.falhas(),
                resultado.sincronizadoEm()
        );
    }

    @GetMapping("/publicacoes")
    public List<PublicacaoResponse> listarPublicacoes(
            @RequestParam(required = false) Long integracaoId) {

        return orchestratorService.listarPublicacoes(integracaoId)
                .stream()
                .map(this::publicacao)
                .toList();
    }

    @PostMapping("/publicacoes/{publicacaoId}/encerrar")
    public PublicacaoResponse encerrar(
            @PathVariable Long publicacaoId) {

        return publicacao(
                orchestratorService.encerrar(publicacaoId)
        );
    }

    @PostMapping("/publicacoes/{publicacaoId}/sincronizar")
    public PublicacaoResponse sincronizar(
            @PathVariable Long publicacaoId) {

        return publicacao(
                orchestratorService.sincronizar(publicacaoId)
        );
    }

    @PostMapping("/publicacoes/sincronizar")
    public List<PublicacaoResponse> sincronizarTodas(
            @RequestParam(required = false) Long integracaoId) {

        return orchestratorService.sincronizarPublicacoes(integracaoId)
                .stream()
                .map(this::publicacao)
                .toList();
    }

    private IntegracaoResponse resumo(IntegracaoMarketplace i) {
        return new IntegracaoResponse(i.getId(), i.getLojaProprietaria(), i.getMarketplace(), i.getStatus(),
                i.getIdentificadorExterno(), i.getTokenExpiraEm(), i.getUltimaSincronizacao());
    }

    private PublicacaoResponse publicacao(PublicacaoMarketplace p) {
        return new PublicacaoResponse(
                p.getId(),
                p.getProduto().getId(),
                p.getIntegracao().getId(),
                p.getIdentificadorExterno(),
                p.getUrlPublicacao(),
                p.getStatus(),
                p.getQuantidadePublicada(),
                p.getUltimaSincronizacao(),
                p.getUltimoErro(),
                p.getProduto().getNome(),
                p.getProduto().getCodigoInterno(),
                p.getProduto().getImagemPrincipal()
        );
    }
}
