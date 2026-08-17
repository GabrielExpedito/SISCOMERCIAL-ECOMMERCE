import React, { useRef, useState } from 'react';
import { api } from './api';

/**
 * Painel de chat com o agente de IA administrativo (RF009).
 * Mantem um sessionId fixo durante a sessao do navegador para que o agente
 * preserve o contexto da conversa (necessario para o fluxo de confirmacao
 * de operacoes sensiveis - secao 9.4 da especificacao).
 */
export default function AgenteChat() {
  const sessionIdRef = useRef(crypto.randomUUID());
  const [mensagens, setMensagens] = useState([
    {
      autor: 'agente',
      texto:
        'Olá! Sou o assistente administrativo da Siscomercial. Posso consultar pedidos, produtos e estoque, ' +
        'e também cancelar pedidos, emitir notas fiscais ou enviar documentos pelo WhatsApp (pedindo sua ' +
        'confirmação antes de qualquer alteração). Experimente: "qual o status do pedido SIS-2026-000001?"',
    },
  ]);
  const [entrada, setEntrada] = useState('');
  const [enviando, setEnviando] = useState(false);

  async function enviarMensagem(e) {
    e.preventDefault();
    const texto = entrada.trim();
    if (!texto || enviando) return;

    setMensagens((atual) => [...atual, { autor: 'admin', texto }]);
    setEntrada('');
    setEnviando(true);

    try {
      const resposta = await api.chatAgente(sessionIdRef.current, texto);
      setMensagens((atual) => [...atual, { autor: 'agente', texto: resposta.resposta }]);
    } catch (err) {
      setMensagens((atual) => [
        ...atual,
        { autor: 'agente', texto: 'Não consegui processar seu comando: ' + err.message },
      ]);
    } finally {
      setEnviando(false);
    }
  }

  return (
    <div className="painel-agente">
      <div className="cabecalho-agente">Assistente IA — Siscomercial</div>
      <div className="mensagens-agente">
        {mensagens.map((m, i) => (
          <div key={i} className={`balao balao-${m.autor}`}>
            {m.texto}
          </div>
        ))}
        {enviando && <div className="balao balao-agente">Pensando...</div>}
      </div>
      <form className="entrada-agente" onSubmit={enviarMensagem}>
        <input
          type="text"
          value={entrada}
          onChange={(e) => setEntrada(e.target.value)}
          placeholder="Ex: cancele o pedido SIS-2026-000001"
        />
        <button type="submit" disabled={enviando}>Enviar</button>
      </form>
    </div>
  );
}
