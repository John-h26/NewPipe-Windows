# SPDX-License-Identifier: GPL-3.0-or-later
#
# Installe ou met a jour NewPipe Windows depuis la derniere version publiee sur GitHub :
#
#   irm https://raw.githubusercontent.com/John-h26/NewPipe-Windows/main/install.ps1 | iex
#
# Installation par utilisateur, sans droits administrateur, dans
# %LOCALAPPDATA%\Programs\NewPipe Windows, avec un raccourci dans le menu Demarrer.
# Java et VLC sont inclus dans l'archive.
#
# Messages sans accents : Windows PowerShell 5.1 lit un script sans BOM en ANSI.
#
# Variables d'environnement facultatives :
#   NPW_INSTALL_DIR  dossier d'installation
#   NPW_INSTALL_ZIP  archive locale a installer au lieu de la telecharger (essais hors ligne)

& {
    $ErrorActionPreference = 'Stop'
    # Sans cela, Invoke-WebRequest de PowerShell 5.1 est dix fois plus lent
    $ProgressPreference = 'SilentlyContinue'
    [Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor
        [Net.SecurityProtocolType]::Tls12

    $appName = 'NewPipe Windows'
    $asset = 'NewPipe-Windows-win64.zip'
    $release = 'https://github.com/John-h26/NewPipe-Windows/releases/latest/download'
    $installDir = if ($env:NPW_INSTALL_DIR) { $env:NPW_INSTALL_DIR }
        else { Join-Path $env:LOCALAPPDATA "Programs\$appName" }

    if (-not [Environment]::Is64BitOperatingSystem) { throw "$appName demande Windows 64 bits." }

    $work = Join-Path ([IO.Path]::GetTempPath()) ("npw-install-" + [Guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path $work | Out-Null
    try {
        if ($env:NPW_INSTALL_ZIP) {
            $zip = (Resolve-Path $env:NPW_INSTALL_ZIP).Path
            Write-Host "Archive locale : $zip"
        } else {
            $zip = Join-Path $work $asset
            Write-Host "Telechargement de $appName (environ 150 Mo)..."
            Invoke-WebRequest "$release/$asset" -OutFile $zip -UseBasicParsing
            Invoke-WebRequest "$release/$asset.sha256" -OutFile "$zip.sha256" -UseBasicParsing

            $expected = ((Get-Content "$zip.sha256" -Raw) -split '\s+')[0]
            $actual = (Get-FileHash $zip -Algorithm SHA256).Hash
            if ($actual -ne $expected) { throw "Empreinte SHA-256 invalide : $actual (attendue : $expected)" }
            Write-Host 'Empreinte SHA-256 verifiee.'
        }

        # Extraction a part d'abord : l'ancienne version reste intacte si l'archive est mauvaise.
        # A cote du dossier final, pour que le renommage reste sur le meme disque.
        $staging = "$installDir.nouveau"
        if (Test-Path $staging) { Remove-Item $staging -Recurse -Force }
        New-Item -ItemType Directory -Path (Split-Path $installDir -Parent) -Force | Out-Null
        Expand-Archive -Path $zip -DestinationPath $staging
        if (-not (Test-Path (Join-Path $staging "$appName.exe"))) {
            throw "Archive inattendue : $appName.exe est absent."
        }

        # Une version en cours d'execution verrouille ses fichiers
        Get-Process | Where-Object { $_.Path -and $_.Path.StartsWith($installDir + '\', 'OrdinalIgnoreCase') } |
            ForEach-Object {
                Write-Host "Fermeture de $($_.ProcessName)..."
                $_ | Stop-Process -Force
                $_.WaitForExit(10000) | Out-Null
            }

        if (Test-Path $installDir) {
            Write-Host 'Remplacement de la version installee...'
            Remove-Item $installDir -Recurse -Force
        }
        Move-Item $staging $installDir

        $exe = Join-Path $installDir "$appName.exe"
        $shortcut = Join-Path ([Environment]::GetFolderPath('Programs')) "$appName.lnk"
        $link = (New-Object -ComObject WScript.Shell).CreateShortcut($shortcut)
        $link.TargetPath = $exe
        $link.WorkingDirectory = $installDir
        $link.Description = 'Client libre YouTube, PeerTube, SoundCloud pour Windows'
        $link.Save()

        Write-Host ''
        Write-Host "$appName est installe dans $installDir" -ForegroundColor Green
        Write-Host "Lancement : menu Demarrer > $appName"
        Write-Host "Desinstallation : supprimer ce dossier et le raccourci $shortcut"
    } finally {
        Remove-Item $work -Recurse -Force -ErrorAction SilentlyContinue
    }
}
