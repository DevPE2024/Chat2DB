# Script para facilitar o desenvolvimento com hot-reload para o Chat2DB
param (
    [switch]$restart,
    [switch]$frontend,
    [switch]$backend
)

# Diretório do projeto
$projectDir = $PSScriptRoot

# Função para reiniciar o contêiner Docker
function Restart-Docker {
    Write-Host "Reiniciando o contêiner do Chat2DB..." -ForegroundColor Yellow
    docker stop chat2db
    docker start chat2db
    Write-Host "Contêiner reiniciado com sucesso!" -ForegroundColor Green
}

# Função para iniciar o frontend com hot-reload
function Start-Frontend {
    Write-Host "Iniciando o frontend com hot-reload..." -ForegroundColor Yellow
    Set-Location "$projectDir\chat2db-client"
    
    # Verificar se o Yarn está instalado
    $yarnInstalled = $null -ne (Get-Command yarn -ErrorAction SilentlyContinue)
    
    if ($yarnInstalled) {
        yarn
        yarn run start:web:hot
    } else {
        Write-Host "Yarn não está instalado. Instalando..." -ForegroundColor Yellow
        npm install -g yarn
        yarn
        yarn run start:web:hot
    }
}

# Função para aplicar alterações no backend
function Apply-Backend {
    Write-Host "Aplicando alterações no backend..." -ForegroundColor Yellow
    
    # Reiniciar o contêiner para aplicar as alterações do backend
    Restart-Docker
}

# Executar as ações com base nos parâmetros
if ($restart) {
    Restart-Docker
}

if ($frontend) {
    Start-Frontend
}

if ($backend) {
    Apply-Backend
}

# Se nenhum parâmetro for fornecido, mostrar ajuda
if (-not ($restart -or $frontend -or $backend)) {
    Write-Host "Uso: .\dev-reload.ps1 [-restart] [-frontend] [-backend]" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "Parâmetros:" -ForegroundColor Cyan
    Write-Host "  -restart   : Reinicia o contêiner Docker do Chat2DB" -ForegroundColor White
    Write-Host "  -frontend  : Inicia o frontend com hot-reload" -ForegroundColor White
    Write-Host "  -backend   : Aplica alterações no backend (reinicia o contêiner)" -ForegroundColor White
    Write-Host ""
    Write-Host "Exemplos:" -ForegroundColor Cyan
    Write-Host "  .\dev-reload.ps1 -frontend    # Inicia apenas o frontend com hot-reload" -ForegroundColor White
    Write-Host "  .\dev-reload.ps1 -restart     # Apenas reinicia o contêiner" -ForegroundColor White
    Write-Host "  .\dev-reload.ps1 -backend     # Aplica alterações no backend" -ForegroundColor White
}
