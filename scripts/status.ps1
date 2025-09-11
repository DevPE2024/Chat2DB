# Script para verificar status de todos os ambientes Chat2DB
Write-Host "=== Chat2DB - Status dos Ambientes ===" -ForegroundColor Cyan

# Verificar se o Docker está rodando
try {
    docker version | Out-Null
} catch {
    Write-Host "ERRO: Docker não está rodando!" -ForegroundColor Red
    exit 1
}

# Função para verificar se um container está rodando
function Test-ContainerRunning($containerName) {
    $container = docker ps --filter "name=$containerName" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}" 2>$null
    return $container -ne $null -and $container.Count -gt 1
}

# Verificar ambiente de PRODUÇÃO
Write-Host "\n🏭 AMBIENTE DE PRODUÇÃO:" -ForegroundColor Green
if (Test-ContainerRunning "chat2db-production") {
    docker ps --filter "name=chat2db-production" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
    Write-Host "✅ Status: RODANDO" -ForegroundColor Green
    Write-Host "🌐 URL: http://localhost:10824" -ForegroundColor Cyan
} else {
    Write-Host "❌ Status: PARADO" -ForegroundColor Red
    Write-Host "▶️  Iniciar: .\scripts\start-prod.ps1" -ForegroundColor Gray
}

# Verificar ambiente de DESENVOLVIMENTO
Write-Host "\n🔧 AMBIENTE DE DESENVOLVIMENTO:" -ForegroundColor Yellow
if (Test-ContainerRunning "chat2db-development") {
    docker ps --filter "name=chat2db-development" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
    Write-Host "✅ Status: RODANDO" -ForegroundColor Green
    Write-Host "🌐 URL: http://localhost:10824" -ForegroundColor Cyan
    Write-Host "🐛 Debug: localhost:5005" -ForegroundColor Magenta
} else {
    Write-Host "❌ Status: PARADO" -ForegroundColor Red
    Write-Host "▶️  Iniciar: .\scripts\start-dev.ps1" -ForegroundColor Gray
}

# Verificar outros containers Chat2DB
Write-Host "\n📦 OUTROS CONTAINERS CHAT2DB:" -ForegroundColor Gray
$otherContainers = docker ps --filter "name=chat2db" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}" | Where-Object { $_ -notmatch "chat2db-production|chat2db-development" -and $_ -notmatch "NAMES" }
if ($otherContainers) {
    $otherContainers
} else {
    Write-Host "Nenhum outro container Chat2DB encontrado" -ForegroundColor Gray
}

# Verificar uso de recursos
Write-Host "\n💾 USO DE RECURSOS:" -ForegroundColor Cyan
try {
    docker stats --no-stream --format "table {{.Container}}\t{{.CPUPerc}}\t{{.MemUsage}}" $(docker ps --filter "name=chat2db" -q) 2>$null
} catch {
    Write-Host "Nenhum container Chat2DB rodando" -ForegroundColor Gray
}

# Verificar volumes
Write-Host "\n📁 VOLUMES:" -ForegroundColor Cyan
docker volume ls --filter "name=chat2db" --format "table {{.Name}}\t{{.Driver}}" 2>$null

Write-Host "\n📋 COMANDOS ÚTEIS:" -ForegroundColor Yellow
Write-Host "   Iniciar Produção: .\scripts\start-prod.ps1" -ForegroundColor Gray
Write-Host "   Iniciar Desenvolvimento: .\scripts\start-dev.ps1" -ForegroundColor Gray
Write-Host "   Parar Todos: .\scripts\stop-all.ps1" -ForegroundColor Gray
Write-Host "   Ver Logs Prod: docker-compose -f docker-compose.prod.yml logs -f" -ForegroundColor Gray
Write-Host "   Ver Logs Dev: docker-compose -f docker-compose.dev.yml logs -f" -ForegroundColor Gray