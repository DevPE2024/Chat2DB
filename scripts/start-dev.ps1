# Script para iniciar Chat2DB em modo DESENVOLVIMENTO
Write-Host "=== Chat2DB - Modo DESENVOLVIMENTO ===" -ForegroundColor Green
Write-Host "Iniciando ambiente de desenvolvimento..." -ForegroundColor Yellow

# Verificar se o Docker está rodando
try {
    docker version | Out-Null
} catch {
    Write-Host "ERRO: Docker não está rodando!" -ForegroundColor Red
    Write-Host "Por favor, inicie o Docker Desktop e tente novamente." -ForegroundColor Yellow
    exit 1
}

# Parar containers existentes se houver
Write-Host "Parando containers existentes..." -ForegroundColor Yellow
docker-compose -f docker-compose.dev.yml down 2>$null

# Criar diretório de logs se não existir
if (!(Test-Path "logs")) {
    New-Item -ItemType Directory -Path "logs" | Out-Null
    Write-Host "📁 Diretório de logs criado" -ForegroundColor Gray
}

# Iniciar em modo desenvolvimento
Write-Host "Iniciando Chat2DB em modo desenvolvimento..." -ForegroundColor Yellow
docker-compose -f docker-compose.dev.yml up -d

if ($LASTEXITCODE -eq 0) {
    Write-Host "✅ Chat2DB (DEV) iniciado com sucesso!" -ForegroundColor Green
    Write-Host "🌐 Aplicação: http://localhost:10824" -ForegroundColor Cyan
    Write-Host "🐛 Debug Java: localhost:5005" -ForegroundColor Magenta
    Write-Host "🔧 Frontend Dev: http://localhost:3000 (se configurado)" -ForegroundColor Cyan
    Write-Host "" 
    Write-Host "📋 Comandos úteis:" -ForegroundColor Yellow
    Write-Host "   Status: docker-compose -f docker-compose.dev.yml ps" -ForegroundColor Gray
    Write-Host "   Logs: docker-compose -f docker-compose.dev.yml logs -f" -ForegroundColor Gray
    Write-Host "   Parar: docker-compose -f docker-compose.dev.yml down" -ForegroundColor Gray
    Write-Host "   Restart: docker-compose -f docker-compose.dev.yml restart" -ForegroundColor Gray
} else {
    Write-Host "❌ Erro ao iniciar o Chat2DB!" -ForegroundColor Red
    Write-Host "Verifique os logs: docker-compose -f docker-compose.dev.yml logs" -ForegroundColor Yellow
}