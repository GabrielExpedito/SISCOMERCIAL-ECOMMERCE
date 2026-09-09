package com.siscomercial.ecommerce.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/** Registro tecnico resumido e reprocessavel de operacoes externas. */
@Entity
@Table(name = "historico_integracao_marketplace")
@Data
public class HistoricoIntegracaoMarketplace {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "integracao_id", nullable = false)
    private IntegracaoMarketplace integracao;
    @Column(nullable = false) private String operacao;
    @Column(name = "referencia_externa") private String referenciaExterna;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40)
    private StatusHistoricoIntegracaoMarketplace status;
    @Column(name = "data_hora", nullable = false) private LocalDateTime dataHora = LocalDateTime.now();
    @Column(name = "mensagem_tecnica", columnDefinition = "TEXT") private String mensagemTecnica;
    @Column(nullable = false) private Integer tentativa = 1;
}
