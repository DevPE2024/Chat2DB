# Script para iniciar Chat2DB em modo PRODUÇÃO
Write-Host "=== Chat2DB - Modo PRODUÇÃO ===" -ForegroundColor Green
Write-Host "Iniciando ambiente de produção..." -ForegroundColor Yellow

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
docker-compose -f docker-compose.prod.yml down 2>$null

# Iniciar em modo produção
Write-Host "Iniciando Chat2DB em modo produção..." -ForegroundColor Yellow
docker-compose -f docker-compose.prod.yml up -d

if ($LASTEXITCODE -eq 0) {
    Write-Host "✅ Chat2DB iniciado com sucesso!" -ForegroundColor Green
    Write-Host "🌐 Acesse: http://localhost:10824" -ForegroundColor Cyan
    Write-Host "📊 Status: docker-compose -f docker-compose.prod.yml ps" -ForegroundColor Gray
    Write-Host "📋 Logs: docker-compose -f docker-compose.prod.yml logs -f" -ForegroundColor Gray
    Write-Host "🛑 Parar: docker-compose -f docker-compose.prod.yml down" -ForegroundColor Gray
} else {
    Write-Host "❌ Erro ao iniciar o Chat2DB!" -ForegroundColor Red
    Write-Host "Verifique os logs: docker-compose -f docker-compose.prod.yml logs" -ForegroundColor Yellow
}