while ($true) {
    $changes = git status --porcelain

    if ($changes) {
        git add .
        git commit -m "Auto update $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
        git push origin master
    }

    Start-Sleep -Seconds 10
}