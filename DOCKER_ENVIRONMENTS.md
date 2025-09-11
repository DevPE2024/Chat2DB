# Chat2DB - Ambientes Docker

Este documento descreve como usar os dois ambientes Docker configurados para o Chat2DB: **Produção** e **Desenvolvimento**.

## 📋 Visão Geral

O projeto agora possui dois ambientes Docker completamente separados:

- **🏭 Produção**: Otimizado para performance, segurança e estabilidade
- **🔧 Desenvolvimento**: Configurado para debug, hot-reload e desenvolvimento ágil

## 🚀 Início Rápido

### Produção
```powershell
# Iniciar ambiente de produção
.\scripts\start-prod.ps1

# Acessar aplicação
# http://localhost:10824
```

### Desenvolvimento
```powershell
# Iniciar ambiente de desenvolvimento
.\scripts\start-dev.ps1

# Acessar aplicação: http://localhost:10824
# Debug Java: localhost:5005
```

### Verificar Status
```powershell
# Ver status de todos os ambientes
.\scripts\status.ps1

# Parar todos os ambientes
.\scripts\stop-all.ps1
```

## 🏭 Ambiente de Produção

### Características
- ✅ **Restart automático** (`unless-stopped`)
- ✅ **Limites de recursos** (CPU: 2 cores, RAM: 2GB)
- ✅ **Health checks** automáticos
- ✅ **Logs rotativos** (10MB, 3 arquivos)
- ✅ **Configurações de segurança**
- ✅ **Volumes persistentes**
- ✅ **Otimizações JVM** para produção

### Arquivos
- `docker-compose.prod.yml` - Configuração do ambiente
- `docker/Dockerfile.prod` - Build otimizado com multi-stage
- `scripts/start-prod.ps1` - Script de inicialização

### Comandos
```powershell
# Iniciar
docker-compose -f docker-compose.prod.yml up -d

# Parar
docker-compose -f docker-compose.prod.yml down

# Ver logs
docker-compose -f docker-compose.prod.yml logs -f

# Restart
docker-compose -f docker-compose.prod.yml restart

# Status
docker-compose -f docker-compose.prod.yml ps
```

### Configurações de Produção
- **Container**: `chat2db-production`
- **Porta**: `10824`
- **Profile Spring**: `prod`
- **JVM**: `-Xmx1536m -Xms512m -XX:+UseG1GC`
- **Timezone**: `America/Sao_Paulo`
- **Volumes**: `chat2db_data`, `chat2db_logs`

## 🔧 Ambiente de Desenvolvimento

### Características
- ✅ **Debug Java** habilitado (porta 5005)
- ✅ **Hot-reload** via volumes bind
- ✅ **Logs verbosos** para debugging
- ✅ **TTY/STDIN** para interação
- ✅ **Sem restart automático**
- ✅ **Ferramentas de desenvolvimento**

### Arquivos
- `docker-compose.dev.yml` - Configuração do ambiente
- `docker/Dockerfile.dev` - Build com ferramentas de dev
- `scripts/start-dev.ps1` - Script de inicialização

### Comandos
```powershell
# Iniciar
docker-compose -f docker-compose.dev.yml up -d

# Parar
docker-compose -f docker-compose.dev.yml down

# Ver logs
docker-compose -f docker-compose.dev.yml logs -f

# Restart
docker-compose -f docker-compose.dev.yml restart

# Entrar no container
docker exec -it chat2db-development bash
```

### Configurações de Desenvolvimento
- **Container**: `chat2db-development`
- **Portas**: `10824` (app), `5005` (debug), `3000` (frontend)
- **Profile Spring**: `dev`
- **JVM**: `-Xmx1024m -Xms256m -XX:+UseG1GC` + debug
- **Debug**: `-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005`
- **Volumes**: Bind mounts para hot-reload

## 🛠️ Scripts de Gerenciamento

### `scripts/start-prod.ps1`
- Inicia ambiente de produção
- Verifica se Docker está rodando
- Para containers existentes
- Mostra URLs e comandos úteis

### `scripts/start-dev.ps1`
- Inicia ambiente de desenvolvimento
- Cria diretório de logs
- Mostra portas de debug
- Lista comandos de desenvolvimento

### `scripts/status.ps1`
- Mostra status de todos os ambientes
- Exibe uso de recursos
- Lista volumes Docker
- Fornece comandos úteis

### `scripts/stop-all.ps1`
- Para todos os ambientes
- Limpa containers órfãos
- Verifica status final

## 🔍 Debug e Desenvolvimento

### Conectar Debugger Java
1. Configure sua IDE para conectar em `localhost:5005`
2. Use o protocolo JDWP
3. O debug está sempre ativo no ambiente de desenvolvimento

### Hot-Reload
Os volumes bind permitem:
- Alterações no código servidor são refletidas automaticamente
- Logs são salvos localmente em `./logs/`
- Configurações podem ser modificadas sem rebuild

### Logs
```powershell
# Logs em tempo real - Produção
docker-compose -f docker-compose.prod.yml logs -f

# Logs em tempo real - Desenvolvimento
docker-compose -f docker-compose.dev.yml logs -f

# Logs específicos do container
docker logs chat2db-production -f
docker logs chat2db-development -f
```

## 🔧 Troubleshooting

### Container não inicia
```powershell
# Verificar logs
docker-compose -f docker-compose.prod.yml logs

# Verificar status
docker ps -a

# Rebuild se necessário
docker-compose -f docker-compose.prod.yml up --build
```

### Porta já em uso
```powershell
# Verificar o que está usando a porta
netstat -ano | findstr :10824

# Parar todos os containers Chat2DB
.\scripts\stop-all.ps1
```

### Problemas de permissão
```powershell
# Limpar volumes (CUIDADO: apaga dados)
docker volume prune

# Recriar containers
docker-compose -f docker-compose.prod.yml down -v
docker-compose -f docker-compose.prod.yml up -d
```

## 📊 Monitoramento

### Health Checks
O ambiente de produção possui health checks automáticos:
- **Intervalo**: 30 segundos
- **Timeout**: 10 segundos
- **Retries**: 3 tentativas
- **Start Period**: 60 segundos

### Recursos
```powershell
# Ver uso de recursos
docker stats

# Ver uso específico
docker stats chat2db-production chat2db-development
```

## 🔐 Segurança

### Produção
- Usuário não-root no container
- `no-new-privileges` habilitado
- Limites de recursos definidos
- Health checks para disponibilidade

### Desenvolvimento
- Portas de debug expostas (apenas para dev)
- Volumes bind para facilitar desenvolvimento
- Ferramentas de debug instaladas

## 📝 Próximos Passos

1. **CI/CD**: Integrar com pipelines de build
2. **Monitoring**: Adicionar Prometheus/Grafana
3. **Backup**: Automatizar backup dos volumes
4. **SSL**: Configurar HTTPS para produção
5. **Load Balancer**: Para múltiplas instâncias

---

**💡 Dica**: Use sempre os scripts em `./scripts/` para gerenciar os ambientes. Eles incluem verificações de segurança e feedback útil!