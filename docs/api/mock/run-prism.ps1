[CmdletBinding()]
param(
    [int]$Port = 8082
)

$ErrorActionPreference = 'Stop'
$openApi = Join-Path $PSScriptRoot '..\openapi.yaml'

if (-not (Test-Path -LiteralPath $openApi -PathType Leaf)) {
    throw "Contrato OpenAPI não encontrado: $openApi"
}

& npx --yes '@stoplight/prism-cli@5.16.0' mock $openApi -h 127.0.0.1 -p $Port --errors
exit $LASTEXITCODE
