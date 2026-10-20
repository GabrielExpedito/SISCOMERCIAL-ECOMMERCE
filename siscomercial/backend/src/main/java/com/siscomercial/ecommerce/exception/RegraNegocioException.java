package com.siscomercial.ecommerce.exception;

/** Excecao generica para violacao de regra de negocio (RNxxx da especificacao). */
public class RegraNegocioException extends RuntimeException {
    public RegraNegocioException(String message) {
        super(message);
    }

    public RegraNegocioException(String message, Throwable cause) {
        super(message, cause);
    }
}
