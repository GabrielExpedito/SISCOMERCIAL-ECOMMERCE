# 🛒 SISCOMERCIAL - Sistema de E-Commerce & Retaguarda

[![Java](https://img.shields.io/badge/Backend-Java%2017%20%2F%20Spring%20Boot-orange.svg)](https://spring.io/)
[![React](https://img.shields.io/badge/Frontend-React%20%2B%20Vite-blue.svg)](https://react.dev/)
[![Database](https://img.shields.io/badge/Database-PostgreSQL%20%2F%20Flyway-blue)](https://www.postgresql.org/)
[![License](https://img.shields.io/badge/License-Proprietary%20%2F%20Commercial-green.svg)](#licenca)

> **Solução completa de E-Commerce e Gestão de Retaguarda (ERP/PDV)** com integração nativa de **Inteligência Artificial** para otimização de vendas e atendimento comercial.

---

## 🌟 Visão Geral do Produto

O **SISCOMERCIAL** é uma plataforma robusta e escalável desenvolvida para empresas que buscam unificar a operação do e-commerce (loja virtual) com a gestão interna (retaguarda e controle financeiro/estoque). 

Projetado sob a arquitetura moderna de microsserviços/REST API, o sistema conta com uma **Assistente Virtual com IA** pronta para automatizar o atendimento a clientes, auxiliar no catálogo de produtos e agilizar o fluxo de pedidos.

Ideal para venda direta do produto final (White Label/SaaS) ou implantação em clientes corporativos.

---

## ✨ Principais Funcionalidades

### 🛒 1. Loja Virtual / Front-End (Cliente)
* **Catálogo de Produtos Dinâmico:** Busca, filtragem e exibição detalhada de itens com histórico de preços.
* **Carrinho e Checkout:** Experiência fluida de compra, integração com múltiplos métodos de pagamento e cálculo automático de frete.
* **Autenticação Flexível:** Suporte a Login tradicional (e-mail/senha) e **OAuth2 (Google Sign-In)**.
* **Área do Cliente:** Acompanhamento em tempo real do status dos pedidos, histórico e gestão do perfil.

### 🏢 2. Retaguarda Comercial & ERP (Backoffice)
* **Gestão de Estoque:** Controle rígido de movimentações (entradas/saídas) e alertas de estoque.
* **Gestão de Pedidos:** Mudança de status em tempo real (Pendente, Pago, Enviado, Entregue, Cancelado) com logs de histórico.
* **Emissão de Nota Fiscal & Frete:** Módulos integrados para simulação de frete e emissão fiscal.
* **Notificações via WhatsApp:** Envio automático do status do pedido e comprovantes direto para o WhatsApp do cliente.

### 🤖 3. Agente de IA Comercial Integrado
* **Assistente Virtual de Vendas:** Atendimento inteligente para tirar dúvidas de clientes no e-commerce.
* **Automação de Retaguarda por IA:** Interface por linguagem natural para auxílio no cadastro de produtos e confirmação de operações administrativas.

---

## 🛠️ Arquitetura e Tecnologias

### **Backend**
* **Linguagem & Framework:** Java 17 + Spring Boot 3
* **Segurança:** Spring Security + OAuth2
* **Banco de Dados:** PostgreSQL
* **Migrations:** Flyway (Versionamento automatizado do banco)
* **Build Tool:** Maven

### **Frontend**
* **Tecnologias:** React + Vite + JavaScript/TypeScript
* **Estilização:** CSS3 / Tailwind / Styled Components

---

## 🚀 Como Executar o Projeto

### Pré-requisitos
* **Java JDK 17+**
* **Node.js 18+** e **npm**
* **PostgreSQL** instalado e rodando

---

### 1️⃣ Configuração do Backend (Spring Boot)

1. Clone o repositório:
   ```bash
   git clone [https://github.com/seu-usuario/SISCOMERCIAL-ECOMMERCE.git](https://github.com/seu-usuario/SISCOMERCIAL-ECOMMERCE.git)
   cd SISCOMERCIAL-ECOMMERCE-main/siscomercial/backend