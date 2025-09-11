# Script para parar todos os ambientes Chat2DB
Write-Host "=== Chat2DB - Parar Todos os Ambientes ===" -ForegroundColor Red

# Verificar se o Docker está rodando
try {
    docker version | Out-Null
} catch {
    Write-Host "ERRO: Docker não está rodando!" -ForegroundColor Red
    exit 1
}

# Parar ambiente de produção
Write-Host "Parando ambiente de PRODUÇÃO..." -ForegroundColor Yellow
docker-compose -f docker-compose.prod.yml down 2>$null
if ($LASTEXITCODE -eq 0) {
    Write-Host "✅ Ambiente de produção parado" -ForegroundColor Green
} else {
    Write-Host "⚠️  Ambiente de produção não estava rodando" -ForegroundColor Gray
}

# Parar ambiente de desenvolvimento
Write-Host "Parando ambiente de DESENVOLVIMENTO..." -ForegroundColor Yellow
docker-compose -f docker-compose.dev.yml down 2>$null
if ($LASTEXITCODE -eq 0) {
    Write-Host "✅ Ambiente de desenvolvimento parado" -ForegroundColor Green
} else {
    Write-Host "⚠️  Ambiente de desenvolvimento não estava rodando" -ForegroundColor Gray
}

# Parar ambiente padrão (se houver)
Write-Host "Parando ambiente padrão..." -ForegroundColor Yellow
docker-compose -f docker/docker-compose.yml down 2>$null

# Limpar containers órfãos
Write-Host "Limpando containers órfãos..." -ForegroundColor Yellow
docker container prune -f 2>$null

Write-Host "" 
Write-Host "🛑 Todos os ambientes Chat2DB foram parados!" -ForegroundColor Red
Write-Host "📊 Verificar status: .\scripts\status.ps1" -ForegroundColor Gray