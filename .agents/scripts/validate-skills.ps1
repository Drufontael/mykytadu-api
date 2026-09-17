$ErrorActionPreference = "Stop"

$repositoryRoot = if (-not [string]::IsNullOrWhiteSpace($env:GITHUB_WORKSPACE)) {
    if (-not (Test-Path -LiteralPath $env:GITHUB_WORKSPACE -PathType Container)) {
        throw "GitHub workspace directory not found: $env:GITHUB_WORKSPACE"
    }

    (Resolve-Path -LiteralPath $env:GITHUB_WORKSPACE).Path
} else {
    (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "../..")).Path
}

$agentsDirectory = Join-Path $repositoryRoot ".agents"
$skillsRoot = Join-Path $agentsDirectory "skills"
$errors = [System.Collections.Generic.List[string]]::new()

if (-not (Test-Path -LiteralPath $agentsDirectory -PathType Container)) {
    throw "Agent configuration directory not found: $agentsDirectory"
}

if (-not (Test-Path -LiteralPath $skillsRoot -PathType Container)) {
    throw "Required skills directory not found: $skillsRoot"
}

Get-ChildItem -LiteralPath $skillsRoot -Directory | ForEach-Object {
    $skillDirectory = $_
    $skillFile = Join-Path $skillDirectory.FullName "SKILL.md"

    if (-not (Test-Path -LiteralPath $skillFile -PathType Leaf)) {
        $errors.Add("SKILL.md ausente: $($skillDirectory.FullName)")
        return
    }

    $content = Get-Content -Raw -LiteralPath $skillFile
    $frontmatterMatch = [regex]::Match($content, "(?s)^---\r?\n(.*?)\r?\n---\r?\n")

    if (-not $frontmatterMatch.Success) {
        $errors.Add("Frontmatter YAML ausente ou inválido: $skillFile")
        return
    }

    $frontmatter = $frontmatterMatch.Groups[1].Value
    $nameMatch = [regex]::Match($frontmatter, "(?m)^name:\s*([^\r\n]+)\s*$")
    $descriptionMatch = [regex]::Match($frontmatter, "(?m)^description:\s*(.+)$")

    if (-not $nameMatch.Success) {
        $errors.Add("Campo name ausente: $skillFile")
    } elseif ($nameMatch.Groups[1].Value.Trim() -ne $skillDirectory.Name) {
        $errors.Add("name não corresponde ao diretório: $skillFile")
    }

    if (-not $descriptionMatch.Success -or [string]::IsNullOrWhiteSpace($descriptionMatch.Groups[1].Value)) {
        $errors.Add("Campo description ausente ou vazio: $skillFile")
    }

    if ($content -cmatch "\bTODO\b|\bFIXME\b|your skill here|replace this") {
        $errors.Add("Placeholder encontrado: $skillFile")
    }

    foreach ($link in [regex]::Matches($content, '\[[^\]]+\]\(([^)]+)\)')) {
        $target = $link.Groups[1].Value.Split('#')[0]
        if ($target -and -not $target.StartsWith("http") -and -not $target.StartsWith("#")) {
            $resolvedTarget = Join-Path $skillDirectory.FullName $target
            if (-not (Test-Path -LiteralPath $resolvedTarget)) {
                $errors.Add("Link relativo inexistente em $skillFile`: $target")
            }
        }
    }
}

$indexFile = Join-Path $agentsDirectory "README.md"
if (-not (Test-Path -LiteralPath $indexFile -PathType Leaf)) {
    $errors.Add("Índice de skills ausente: $indexFile")
} else {
    $indexContent = Get-Content -Raw -LiteralPath $indexFile
    foreach ($link in [regex]::Matches($indexContent, '\[[^\]]+\]\(([^)]+)\)')) {
        $target = $link.Groups[1].Value.Split('#')[0]
        if ($target -and -not $target.StartsWith("http") -and -not $target.StartsWith("#")) {
            $resolvedTarget = Join-Path (Split-Path -Parent $indexFile) $target
            if (-not (Test-Path -LiteralPath $resolvedTarget)) {
                $errors.Add("Link relativo inexistente em $indexFile`: $target")
            }
        }
    }
}

if ($errors.Count -gt 0) {
    $errors | ForEach-Object { Write-Error $_ }
    exit 1
}

Write-Output "Skills válidas: $((Get-ChildItem -LiteralPath $skillsRoot -Directory).Count)"
