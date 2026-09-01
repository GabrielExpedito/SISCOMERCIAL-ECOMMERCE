package com.siscomercial.ecommerce.model;

/** Origem auditavel de uma mudanca no ciclo do pedido (RF004, secao 8.8). */
public enum OrigemAlteracaoStatusPedido {
    SISTEMA,
    ADMINISTRADOR,
    CLIENTE,
    INTEGRACAO
}
