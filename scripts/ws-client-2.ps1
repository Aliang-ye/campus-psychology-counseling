$ErrorActionPreference = 'Stop'
$uri = 'ws://127.0.0.1:8080/ws/chat?userId=2'
$ws = [System.Net.WebSockets.ClientWebSocket]::new()
$ct = [Threading.CancellationToken]::None
Write-Host "Connecting to $uri ..."
try {
    $ws.ConnectAsync([Uri]$uri, $ct).Wait()
    Write-Host "Connected as user 2 (state: $($ws.State))"
} catch {
    Write-Host "Connect failed: $($_.Exception.Message)"
    if ($_.Exception.InnerException) { Write-Host "Inner: $($_.Exception.InnerException.ToString())" }
    Write-Host "Full exception:`n$($_.Exception.ToString())"
    exit 1
}
# send a message after a short delay
Start-Sleep -Milliseconds 500
$payload = '{"sessionId":2,"senderId":2,"content":"hello_from_2"}'
$bytes = [System.Text.Encoding]::UTF8.GetBytes($payload)
$seg = New-Object System.ArraySegment`1[System.Byte]($bytes)
$ws.SendAsync($seg, [System.Net.WebSockets.WebSocketMessageType]::Text, $true, $ct).Wait()
Write-Host "User2 sent: $payload"
# receive loop (10 seconds)
$end = (Get-Date).AddSeconds(10)
$buffer = New-Object byte[] 8192
while ((Get-Date) -lt $end -and $ws.State -eq 'Open') {
    try {
        $segR = New-Object System.ArraySegment`1[System.Byte]($buffer)
        $result = $ws.ReceiveAsync($segR, $ct).Result
        if ($result.Count -gt 0) {
            $msg = [System.Text.Encoding]::UTF8.GetString($buffer, 0, $result.Count)
            Write-Host "[recv user2] $msg"
        }
        if ($result.CloseStatus -ne $null) { break }
    } catch {
        Write-Host "Receive error: $_"
        break
    }
}
Write-Host "User2 closing"
$ws.CloseAsync([System.Net.WebSockets.WebSocketCloseStatus]::NormalClosure, 'bye', $ct).Wait()
