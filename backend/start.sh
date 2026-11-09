#!/usr/bin/env bash

# BorrowBox Backend Startup Script
# Automatically detects current Mac Wi-Fi IP and starts the server

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Detect current Wi-Fi IP
IP=$(ipconfig getifaddr en0 2>/dev/null)
if [ -z "$IP" ]; then
    IP=$(ipconfig getifaddr en1 2>/dev/null)
fi
if [ -z "$IP" ]; then
    IP="127.0.0.1"
fi

echo "======================================================="
echo "📦 BorrowBox Backend Server"
echo "🌐 Your Mac's Current IP      : $IP"
echo "📲 Use this URL in the app    : http://$IP:5050"
echo "🔗 Server Base URL (USB cable): http://127.0.0.1:5050"
echo "🏥 Health check URL           : http://$IP:5050/health"
echo "======================================================="

# Free port 5050 if already in use
PORT_PID=$(lsof -ti :5050 2>/dev/null)
if [ -n "$PORT_PID" ]; then
    echo "⚠️  Port 5050 in use by PID $PORT_PID. Stopping old process..."
    kill -9 $PORT_PID 2>/dev/null
    sleep 1
fi

# Automatically setup adb reverse if an Android device is connected via USB
ADB_BIN="$HOME/Library/Android/sdk/platform-tools/adb"
if [ ! -x "$ADB_BIN" ]; then
    ADB_BIN=$(which adb 2>/dev/null)
fi

if [ -x "$ADB_BIN" ]; then
    DEVICES=$("$ADB_BIN" devices 2>/dev/null | grep -w "device")
    if [ -n "$DEVICES" ]; then
        echo "📱 Android device detected via USB. Setting up port forwarding..."
        "$ADB_BIN" reverse tcp:5050 tcp:5050 2>/dev/null
        echo "✅ Port 5050 forwarded over USB! App can reach server at http://127.0.0.1:5050"
    fi
fi

echo "🚀 Starting Node server on 0.0.0.0:5050..."
exec node src/server.js
