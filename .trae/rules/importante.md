
## 🎯 Regras de Prioridade de Desenvolvimento

### 📋 **Ordem de Execução Obrigatória**

#### 🚀 **Sequência de Desenvolvimento:**
1. **Backend Fase 1** (Itens 1-10) - Infraestrutura base
2. **Frontend Fase 1** (Itens 36-45) - Interface base
3. **Backend Fase 2** (Itens 11-20) - APIs core
4. **Frontend Fase 2** (Itens 46-55) - Editor e consultas
5. **Backend Fase 3** (Itens 21-35) - Funcionalidades avançadas
6. **Frontend Fase 3** (Itens 56-75) - Funcionalidades avançadas

### ⏱️ **Cronograma de Implementação (10 horas):**
- **Fase 1:** 3-4 horas (MVP básico)
- **Fase 2:** 3-4 horas (Funcionalidades core)
- **Fase 3:** 3-4 horas (Funcionalidades avançadas)

**Meta:** Conclusão em dias, não semanas - Total: ~10 horas de desenvolvimento

### 🚀 **Checklist de Implementação Acelerada**

#### ⚡ **Prioridade MÁXIMA (Primeiras 4 horas):**
- [ ] Configurar OpenRouter API com chave fornecida
- [ ] Implementar conexão básica com banco de dados
- [ ] Criar interface de autenticação simples
- [ ] Desenvolver editor SQL básico com syntax highlighting
- [ ] Integrar IA para geração SQL via OpenRouter

#### 🔥 **Prioridade ALTA (Horas 5-7):**
- [ ] Dashboard básico com gráficos simples
- [ ] Sistema de execução de queries
- [ ] Interface de chat com IA
- [ ] Exportação de dados básica
- [ ] Gerenciamento de conexões

#### ⭐ **Prioridade MÉDIA (Horas 8-10):**
- [ ] Otimização de performance
- [ ] Testes básicos de funcionalidade
- [ ] Documentação mínima
- [ ] Deploy e configuração final
- [ ] Validação de todos os provedores de IA

### 🎯 **Meta Final do Sistema:**
Sistema funcional com IA integrada via OpenRouter, capaz de:
- Conectar a bancos de dados
- Gerar SQL via linguagem natural
- Executar queries e exibir resultados
- Dashboard básico para visualização
- Suporte a múltiplos provedores de IA

### 🔑 **Configuração OpenRouter API:**
```
OPENROUTER_API_KEY = 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
base_url = "https://openrouter.ai/api/v1"
api_key = "<OPENROUTER_API_KEY>"
```

### 🤖 **Provedores de IA Suportados (Chat2DB Pro):**
- [ ] **OpenAI GPT-4** - Modelo principal para geração SQL
- [ ] **OpenAI GPT-3.5-turbo** - Modelo alternativo rápido
- [ ] **Claude (Anthropic)** - Para análise complexa de dados
- [ ] **Gemini (Google)** - Processamento de linguagem natural
- [ ] **Llama 2/3** - Modelos open-source via OpenRouter
- [ ] **Mistral AI** - Modelos europeus especializados
- [ ] **Cohere** - Para embeddings e classificação
- [ ] **PaLM 2** - Modelo do Google para análise
- [ ] **Chat2DB AI** - Modelo proprietário otimizado para SQL
- [ ] **Zhipu AI** - Modelo chinês especializado

---

## 🛠️ Stack Tecnológico Detalhado

### 🎨 Frontend (chat2db-client)
- **Framework Base**: Umi v4 (React-based framework)
- **UI Components**: Ant Design v5
- **State Management**: 
  - Zustand v4.4.4 (primary)
  - DVA (legacy support)
- **Styling**: 
  - Styled-components v6.0.7
  - TailwindCSS v3
- **Code Editor**: Monaco Editor (SQL, MySQL, PostgreSQL)
- **Charts & Visualization**: ECharts v5.4.2
- **Build & Development**:
  - TypeScript v5.0.3
  - Webpack (via Umi)
  - ESLint + Prettier
- **Desktop Application**: Electron v22.3.0
- **Package Manager**: Yarn (obrigatório)
- **Node Version**: >=16

### ⚙️ Backend (chat2db-server)
- **Framework**: Spring Boot 3.1.0
- **Language**: Java 17
- **Architecture**: Domain-Driven Design (DDD)
- **Database**:
  - H2 Database v2.1.214 (embedded)
  - MyBatis Plus v3.5.3.1 (ORM)
  - HikariCP v5.0.1 (connection pool)
  - Druid v1.2.18 (monitoring)
  - Flyway v9.19.4 (migrations)
- **Security**:
  - Sa-Token v1.34.0 (session management)
  - Input validation and sanitization
- **HTTP & Communication**:
  - Forest v1.5.32 (HTTP client)
  - Server-Sent Events (SSE) para streaming
- **AI Integration**:
  - OpenAI GPT (chatgpt-java v1.0.8)
  - Azure OpenAI
  - Custom AI providers
- **Utilities**:
  - Hutool v5.8.20 (Java utils)
  - Guava v32.0.1-jre
  - FastJSON2 v2.0.37
  - MapStruct v1.5.5.Final
  - Lombok
- **Cache**: EhCache v3.10.8
- **Build Tool**: Maven

---

## 🧪 Procedimentos de Teste Completos

### 🎨 Testes Frontend
```bash
# Navegar para o diretório do frontend
cd chat2db-client

# Instalar dependências (obrigatório usar Yarn)
yarn install

# Verificar scripts disponíveis
yarn run

# Executar linting (verificar erros de código)
yarn run lint

# Build para desenvolvimento
yarn run build

# Build para produção web
yarn run build:web:prod

# Build para desktop (Electron)
yarn run build:desktop

# Iniciar servidor de desenvolvimento
yarn run start:web

# Iniciar aplicação desktop
yarn run start
```

### ⚙️ Testes Backend
```bash
# Navegar para o diretório do backend
cd chat2db-server

# Limpar e compilar projeto
mvn clean compile

# Executar testes unitários
mvn test

# Build completo com testes
mvn clean package

# Executar aplicação (porta padrão: 10821)
java -jar chat2db-server-start/target/chat2db-server-start-*.jar

# Verificar saúde da aplicação
curl http://localhost:10821/api/system/get-version-a
```

### 🔗 Validação de Integração
1. **Conectividade Database**: 
   - Testar H2 embedded
   - Validar conexões externas (MySQL, PostgreSQL, etc.)
2. **API Endpoints**: 
   - Verificar todas as rotas REST
   - Testar proxy configuration (porta 10821)
3. **AI Integration**: 
   - Validar OpenRouter API
   - Testar múltiplos provedores de IA
4. **IPC Communication**: 
   - Testar comunicação Electron ↔ Backend
5. **Performance**: 
   - Monitorar tempo de resposta
   - Verificar uso de memória

### ✅ Checklist de Qualidade Final
- [ ] **Frontend**: Todos os componentes renderizam sem erros
- [ ] **Backend**: Todas as APIs respondem corretamente
- [ ] **Linting**: ESLint + Prettier sem warnings
- [ ] **Build**: Compilação sem erros (TypeScript + Java)
- [ ] **Testes**: Funcionalidades core testadas manualmente
- [ ] **Cross-platform**: Windows, macOS, Linux compatíveis
- [ ] **Performance**: Tempo de resposta < 2s para queries simples
- [ ] **AI Integration**: Pelo menos 2 provedores funcionando
- [ ] **Database**: Conexões e queries executando corretamente

---

## 🛣️ Rotas e Padrões de Projeto

### 🎨 Rotas Frontend (Umi v4)
```typescript
// Configuração em .umirc.ts
routes: [
  {
    path: '/',
    component: '@/layouts/GlobalLayout',
    routes: [
      { path: '/demo', component: '@/pages/demo' },
      { path: '/connections', component: 'main' },
      { path: '/dashboard', component: 'main' },
      { path: '/team', component: 'main' },
      { path: '/workspace', component: 'main' },
      { path: '/', component: 'main' }
    ]
  }
]
```

**Estrutura de Diretórios Frontend:**
```
src/
├── components/     # Componentes reutilizáveis
├── pages/         # Páginas da aplicação
├── layouts/       # Layouts globais
├── models/        # Estados DVA
├── utils/         # Utilitários
├── assets/        # Recursos estáticos
├── locales/       # Internacionalização
└── config/        # Configurações
```

### ⚙️ Rotas Backend (Spring Boot)
```java
// Padrão de Controllers
@RestController
@RequestMapping("/api/{module}")
@ConnectionInfoAspect  // Interceptor de conexão
public class {Module}Controller {
    // Implementação
}
```

**Principais Endpoints:**
- `/api/rdb/*` - Operações de banco de dados
- `/api/ai/*` - Integração com IA
- `/api/system/*` - Informações do sistema
- `/api/user/*` - Gerenciamento de usuários
- `/api/config/*` - Configurações
- `/api/admin/*` - Administração

**Estrutura Backend (DDD):**
```
chat2db-server/
├── chat2db-server-domain/     # Domínio (entidades, services)
├── chat2db-server-web/        # Controllers e APIs
├── chat2db-server-start/      # Configuração de inicialização
├── chat2db-server-tools/      # Utilitários compartilhados
├── chat2db-spi/              # Service Provider Interface
└── chat2db-plugins/          # Plugins de banco de dados
```

### 🔄 Padrões de Comunicação
1. **Frontend ↔ Backend**: HTTP REST + Proxy (porta 10821)
2. **Desktop ↔ Backend**: IPC + HTTP
3. **AI Streaming**: Server-Sent Events (SSE)
4. **Database**: Connection pooling + transaction management

### 🎯 Convenções de Código
- **Frontend**: TypeScript strict, ESLint Airbnb, Prettier
- **Backend**: Java 17, Spring conventions, Lombok
- **API**: RESTful design, consistent error handling
- **Database**: Flyway migrations, MyBatis Plus annotations

---