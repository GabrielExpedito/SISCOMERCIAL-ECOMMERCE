import React, { useRef, useState } from 'react';
import { api } from './api';
import { useSpeechRecognition } from './hooks/useSpeechRecognition';

const mensagemInicial =
  'Olá! Sou o assistente administrativo da Siscomercial. Posso consultar pedidos, produtos e estoque, e também executar operações administrativas com confirmação quando necessário. Você pode digitar ou falar seu comando.';

export default function AgenteChat({ onCatalogoAtualizado }) {
  const sessionIdRef = useRef(crypto.randomUUID());
  const [mensagens, setMensagens] = useState([
    { autor: 'agente', texto: mensagemInicial },
  ]);
  const [entrada, setEntrada] = useState('');
  const [enviando, setEnviando] = useState(false);

  const { isListening, error: vozError, startListening, stopListening } =
    useSpeechRecognition({ onResult: setEntrada });

  async function enviarMensagem(e) {
    e?.preventDefault();
    const texto = entrada.trim();
    if (!texto || enviando) return;

    setMensagens((atual) => [...atual, { autor: 'admin', texto }]);
    setEntrada('');
    setEnviando(true);

    try {
      const resposta = await api.chatAgente(sessionIdRef.current, texto);
      setMensagens((atual) => [...atual, { autor: 'agente', texto: resposta.resposta }]);
      onCatalogoAtualizado?.();
    } catch (err) {
      setMensagens((atual) => [
        ...atual,
        { autor: 'agente', texto: `Não consegui processar seu comando: ${err.message}` },
      ]);
    } finally {
      setEnviando(false);
    }
  }

  function alternarMicrofone() {
    if (isListening) stopListening();
    else startListening();
  }

  return (
    <section className="painel-agente" aria-label="Assistente administrativo">
      <div className="cabecalho-agente">
        <div>
          <div className="agente-status"><span /> Online</div>
          <h2>Assistente Siscomercial</h2>
          <p>Comandos administrativos por texto ou voz</p>
        </div>
        <div className="agente-avatar">✦</div>
      </div>

      <div className="mensagens-agente">
        {mensagens.map((m, i) => (
          <div key={i} className={`mensagem ${m.autor === 'admin' ? 'mensagem-admin' : 'mensagem-agente'}`}>
            <div className="avatar-mensagem">{m.autor === 'admin' ? 'Você' : 'IA'}</div>
            <div className="balao">{m.texto}</div>
          </div>
        ))}
        {enviando && (
          <div className="mensagem mensagem-agente">
            <div className="avatar-mensagem">IA</div>
            <div className="balao digitando"><span /><span /><span /></div>
          </div>
        )}
      </div>

      {vozError && <div className="erro-voz">{vozError}</div>}

      <form className="entrada-agente" onSubmit={enviarMensagem}>
        <input
          type="text"
          value={entrada}
          onChange={(e) => setEntrada(e.target.value)}
          placeholder={isListening ? 'Estou ouvindo...' : 'Digite seu comando...'}
          disabled={enviando}
        />
        <button
          className={`botao-microfone ${isListening ? 'ouvindo' : ''}`}
          type="button"
          onClick={alternarMicrofone}
          aria-label={isListening ? 'Parar reconhecimento de voz' : 'Falar comando'}
          title={isListening ? 'Parar' : 'Falar comando'}
        >
          {isListening ? '■' : '🎙'}
        </button>
        <button className="botao-enviar" type="submit" disabled={enviando || !entrada.trim()}>
          ➤
        </button>
      </form>
      <div className="dica-voz">Dica: clique no microfone e fale naturalmente em português.</div>
    </section>
  );
}
