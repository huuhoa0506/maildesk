$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location $projectRoot
try {
    & .\gradlew.bat bootJar
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle bootJar failed with exit code $LASTEXITCODE."
    }

    $jar = Get-ChildItem .\build\libs\reminder-*.jar |
        Where-Object { $_.Name -notmatch "-plain\.jar$" } |
        Select-Object -First 1
    if ($null -eq $jar) {
        throw "Could not find the Spring Boot executable JAR in build\libs."
    }

    $inputDirectory = Join-Path $projectRoot "build\windows-input"
    $imageDirectory = Join-Path $projectRoot "build\windows-package"
    $distDirectory = Join-Path $projectRoot "dist"
    $appImage = Join-Path $imageDirectory "Maildesk"
    $zipFile = Join-Path $distDirectory "Maildesk-Windows-x64.zip"

    foreach ($path in @($inputDirectory, $appImage)) {
        if (Test-Path -LiteralPath $path) {
            Remove-Item -LiteralPath $path -Recurse -Force
        }
    }
    New-Item -ItemType Directory -Force -Path $inputDirectory, $imageDirectory, $distDirectory | Out-Null
    Copy-Item -LiteralPath $jar.FullName -Destination (Join-Path $inputDirectory "Maildesk.jar")
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot "README.txt") `
        -Destination (Join-Path $inputDirectory "README.txt")

    & jpackage `
        --type app-image `
        --input $inputDirectory `
        --dest $imageDirectory `
        --name Maildesk `
        --main-jar Maildesk.jar `
        --main-class org.springframework.boot.loader.launch.JarLauncher `
        --app-version 1.0.0 `
        --vendor Maildesk `
        --java-options "-Dmaildesk.packaged=true" `
        --win-console
    if ($LASTEXITCODE -ne 0) {
        throw "jpackage failed with exit code $LASTEXITCODE."
    }

    if (Test-Path -LiteralPath $zipFile) {
        Remove-Item -LiteralPath $zipFile -Force
    }
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    [System.IO.Compression.ZipFile]::CreateFromDirectory($appImage, $zipFile)
    Write-Host "Windows package created: $zipFile"
}
finally {
    Pop-Location
}
