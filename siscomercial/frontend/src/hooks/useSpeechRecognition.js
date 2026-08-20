import { useRef, useState } from 'react';

export function useSpeechRecognition({ language = 'pt-BR', onResult } = {}) {
  const recognitionRef = useRef(null);
  const [isListening, setIsListening] = useState(false);
  const [error, setError] = useState(null);

  function startListening() {
    const SpeechRecognition =
      window.SpeechRecognition || window.webkitSpeechRecognition;

    if (!SpeechRecognition) {
      setError('Seu navegador não oferece reconhecimento de voz. Tente usar o Google Chrome.');
      return;
    }

    if (isListening) return;

    const recognition = new SpeechRecognition();
    recognition.lang = language;
    recognition.continuous = false;
    recognition.interimResults = true;
    recognition.maxAlternatives = 1;

    recognition.onstart = () => {
      setError(null);
      setIsListening(true);
    };

    recognition.onresult = (event) => {
      let texto = '';
      for (let i = event.resultIndex; i < event.results.length; i += 1) {
        texto += event.results[i][0].transcript;
      }
      if (texto.trim() && onResult) onResult(texto.trim());
    };

    recognition.onerror = (event) => {
      const mensagens = {
        'not-allowed': 'Permissão do microfone negada. Libere o acesso ao microfone no navegador.',
        'no-speech': 'Nenhuma fala foi detectada. Tente novamente.',
        'audio-capture': 'Não foi possível acessar o microfone.',
        network: 'Não foi possível utilizar o serviço de reconhecimento de voz.',
      };
      setError(mensagens[event.error] || 'Não foi possível reconhecer sua voz.');
      setIsListening(false);
    };

    recognition.onend = () => {
      setIsListening(false);
    };

    recognitionRef.current = recognition;
    recognition.start();
  }

  function stopListening() {
    recognitionRef.current?.stop();
    setIsListening(false);
  }

  return { isListening, error, startListening, stopListening };
}
