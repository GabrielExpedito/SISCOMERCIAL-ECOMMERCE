package com.siscomercial.ecommerce.service;

import com.siscomercial.ecommerce.exception.RecursoNaoEncontradoException;
import com.siscomercial.ecommerce.exception.RegraNegocioException;
import com.siscomercial.ecommerce.model.Produto;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ImagemProdutoService {

    private static final long TAMANHO_MAXIMO_IMAGEM = 10 * 1024 * 1024L;

    private static final Set<String> CONTENT_TYPES_PERMITIDOS = Set.of(
            "image/jpeg",
            "image/png"
    );

    private final ProdutoService produtoService;

    @Value("${siscomercial.upload.diretorio:uploads}")
    private String diretorioUpload;

    @Value("${siscomercial.upload.public-base-url:http://localhost:8080}")
    private String publicBaseUrl;

    /**
     * Adiciona uma imagem ao produto.
     */
    @Transactional
    public ImagemResponse adicionar(
            Long produtoId,
            MultipartFile arquivo
    ) {
        validarProdutoId(produtoId);
        validarArquivo(arquivo);

        Produto produto = produtoService.buscarPorId(produtoId);

        String extensao = obterExtensao(arquivo.getContentType());

        String nomeArquivo =
                UUID.randomUUID() + extensao;

        Path diretorioProduto =
                obterDiretorioProduto(produtoId);

        try {

            /*
             * Garante que o diretório exista.
             */
            Files.createDirectories(diretorioProduto);

            Path destino =
                    diretorioProduto
                            .resolve(nomeArquivo)
                            .normalize();

            /*
             * Segurança para garantir que o arquivo
             * permaneça dentro do diretório do produto.
             */
            if (!destino.startsWith(diretorioProduto)) {
                throw new RegraNegocioException(
                        "Nao foi possivel determinar um caminho seguro para a imagem."
                );
            }

            /*
             * Salva o arquivo fisicamente.
             */
            try (InputStream inputStream =
                         arquivo.getInputStream()) {

                Files.copy(
                        inputStream,
                        destino,
                        StandardCopyOption.REPLACE_EXISTING
                );
            }

            /*
             * Monta a URL pública.
             */
            String url =
                    montarUrlPublica(
                            produtoId,
                            nomeArquivo
                    );

            /*
             * Atualiza a lista de imagens.
             */
            List<String> imagens =
                    produto.getImagens() == null
                            ? new ArrayList<>()
                            : new ArrayList<>(produto.getImagens());

            imagens.add(url);

            produto.setImagens(imagens);

            /*
             * Se ainda não existir imagem principal,
             * a primeira imagem será automaticamente
             * definida como principal.
             */
            boolean principal =
                    produto.getImagemPrincipal() == null
                            || produto.getImagemPrincipal().isBlank();

            if (principal) {
                produto.setImagemPrincipal(url);
            }

            produtoService.salvarAlteracoesDeImagem(produto);

            return new ImagemResponse(
                    url,
                    principal
            );

        } catch (IOException e) {

            throw new RegraNegocioException(
                    "Nao foi possivel armazenar a imagem: "
                            + e.getMessage()
            );
        }
    }

    /**
     * Define uma imagem existente como principal.
     *
     * O Controller atual envia um Map contendo a URL.
     */
    /**
     * Define uma imagem existente como principal.
     */
    @Transactional
    public ImagemResponse principal(
            Long produtoId,
            String url
    ) {
        validarProdutoId(produtoId);

        Produto produto =
                produtoService.buscarPorId(produtoId);

        if (url == null || url.isBlank()) {
            throw new RegraNegocioException(
                    "A URL da imagem deve ser informada."
            );
        }

        if (produto.getImagens() == null
                || !produto.getImagens().contains(url)) {

            throw new RegraNegocioException(
                    "A imagem informada nao pertence ao produto."
            );
        }

        produto.setImagemPrincipal(url);

        produtoService.salvarAlteracoesDeImagem(produto);

        return new ImagemResponse(
                url,
                true
        );
    }

    /**
     * Remove uma imagem do produto.
     */
    @Transactional
    public void remover(
            Long produtoId,
            String url
    ) {
        validarProdutoId(produtoId);

        Produto produto =
                produtoService.buscarPorId(produtoId);

        if (url == null || url.isBlank()) {
            throw new RecursoNaoEncontradoException(
                    "Imagem nao informada para o produto: "
                            + produtoId
            );
        }

        if (produto.getImagens() == null
                || !produto.getImagens().contains(url)) {

            throw new RecursoNaoEncontradoException(
                    "Imagem nao encontrada para o produto: "
                            + produtoId
            );
        }

        /*
         * Cria uma nova lista para evitar problemas
         * com a coleção gerenciada pelo Hibernate.
         */
        List<String> imagens =
                new ArrayList<>(produto.getImagens());

        imagens.remove(url);

        produto.setImagens(imagens);

        /*
         * Se a imagem removida era a principal,
         * seleciona automaticamente a primeira
         * imagem restante.
         */
        if (url.equals(produto.getImagemPrincipal())) {

            String novaPrincipal =
                    imagens.isEmpty()
                            ? null
                            : imagens.get(0);

            produto.setImagemPrincipal(
                    novaPrincipal
            );
        }

        produtoService.salvarAlteracoesDeImagem(produto);

        /*
         * Remove também o arquivo físico.
         */
        removerArquivoFisico(
                produtoId,
                url
        );
    }

    /**
     * Valida o ID do produto.
     */
    private void validarProdutoId(Long produtoId) {

        if (produtoId == null || produtoId <= 0) {
            throw new RegraNegocioException(
                    "O identificador do produto deve ser informado."
            );
        }
    }

    /**
     * Valida o arquivo enviado.
     */
    private void validarArquivo(
            MultipartFile arquivo
    ) {

        if (arquivo == null || arquivo.isEmpty()) {
            throw new RegraNegocioException(
                    "Selecione uma imagem para enviar."
            );
        }

        if (arquivo.getSize() > TAMANHO_MAXIMO_IMAGEM) {
            throw new RegraNegocioException(
                    "A imagem deve possuir no maximo 10 MB."
            );
        }

        String contentType =
                arquivo.getContentType();

        if (contentType == null
                || !CONTENT_TYPES_PERMITIDOS.contains(
                contentType.toLowerCase(Locale.ROOT)
        )) {

            throw new RegraNegocioException(
                    "Formato de imagem nao permitido. "
                            + "Utilize JPG, JPEG ou PNG."
            );
        }
    }

    /**
     * Obtém a extensão do arquivo.
     */
    private String obterExtensao(
            String contentType
    ) {

        if ("image/png".equalsIgnoreCase(contentType)) {
            return ".png";
        }

        return ".jpg";
    }

    /**
     * Retorna o diretório físico do produto.
     *
     * Exemplo:
     *
     * uploads/produtos/2
     */
    private Path obterDiretorioProduto(
            Long produtoId
    ) {

        Path raizUpload =
                Paths.get(diretorioUpload)
                        .toAbsolutePath()
                        .normalize();

        Path diretorioProdutos =
                raizUpload
                        .resolve("produtos")
                        .normalize();

        Path diretorioProduto =
                diretorioProdutos
                        .resolve(String.valueOf(produtoId))
                        .normalize();

        /*
         * Garante que o caminho permaneça
         * dentro de uploads/produtos.
         */
        if (!diretorioProduto.startsWith(
                diretorioProdutos
        )) {
            throw new RegraNegocioException(
                    "Caminho de armazenamento invalido."
            );
        }

        return diretorioProduto;
    }

    /**
     * Monta a URL pública da imagem.
     *
     * Exemplo:
     *
     * http://localhost:8080/uploads/produtos/2/imagem.png
     */
    private String montarUrlPublica(
            Long produtoId,
            String nomeArquivo
    ) {

        String base =
                publicBaseUrl == null
                        ? ""
                        : publicBaseUrl.trim();

        while (base.endsWith("/")) {
            base = base.substring(
                    0,
                    base.length() - 1
            );
        }

        return base
                + "/uploads/produtos/"
                + produtoId
                + "/"
                + nomeArquivo;
    }

    /**
     * Remove o arquivo físico.
     */
    private void removerArquivoFisico(
            Long produtoId,
            String url
    ) {

        String prefixo =
                montarUrlPublica(
                        produtoId,
                        ""
                );

        if (!url.startsWith(prefixo)) {
            return;
        }

        String nomeArquivo =
                url.substring(prefixo.length());

        /*
         * Impede acesso a caminhos externos.
         */
        if (nomeArquivo.isBlank()
                || nomeArquivo.contains("/")
                || nomeArquivo.contains("\\")
                || nomeArquivo.contains("..")) {

            return;
        }

        Path diretorioProduto =
                obterDiretorioProduto(produtoId);

        Path arquivo =
                diretorioProduto
                        .resolve(nomeArquivo)
                        .normalize();

        if (!arquivo.startsWith(diretorioProduto)) {
            return;
        }

        try {

            Files.deleteIfExists(arquivo);

        } catch (IOException ignored) {

            /*
             * O banco já foi atualizado.
             * A falha na exclusão física não deve
             * desfazer a operação do banco.
             */
        }
    }

    /**
     * Resposta retornada pelas operações de imagem.
     */
    public record ImagemResponse(
            String url,
            boolean principal
    ) {
    }
}