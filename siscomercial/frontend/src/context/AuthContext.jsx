import { createContext, useContext, useEffect, useState } from "react";
import { api } from "../api";

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [usuario, setUsuario] = useState(null);
  const [carregando, setCarregando] = useState(true);

  useEffect(() => {
    carregarUsuario();
  }, []);

  async function carregarUsuario() {
    try {
      const usuarioAtual = await api.buscarUsuarioAtual();

      console.log("USUARIO AUTENTICADO:", usuarioAtual);

      setUsuario(usuarioAtual);
    } catch (error) {
      console.log("Nenhum usuário autenticado.");

      setUsuario(null);
    } finally {
      setCarregando(false);
    }
  }

function entrarComGoogle() {
  window.location.href = "http://localhost:8080/oauth2/authorization/google";
}

function sair() {
  window.location.href = "http://localhost:8080/logout";
}

  const autenticado = usuario !== null;
  const ehAdmin = usuario?.perfil === "ADMIN";
  const ehCliente = usuario?.perfil === "CLIENTE";

  return (
    <AuthContext.Provider
      value={{
        usuario,
        carregando,
        autenticado,
        ehAdmin,
        ehCliente,
        entrarComGoogle,
        sair,
        carregarUsuario,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const contexto = useContext(AuthContext);

  if (!contexto) {
    throw new Error("useAuth deve ser utilizado dentro de um AuthProvider.");
  }

  return contexto;
}
