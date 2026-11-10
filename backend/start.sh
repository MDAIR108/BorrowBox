#!/usr/bin/env bash

# BorrowBox Backend & ngrok Remote Tunnel Startup Script
# Automatically frees port 5050, starts the Node server, and launches ngrok

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

NGROK_DOMAIN="judicial-antelope-untrained.ngrok-free.dev"
NGROK_URL="https://$NGROK_DOMAIN"

echo "======================================================="
echo "📦 BorrowBox Backend Server + ngrok Remote Access"
echo "🌐 Remote Server URL : $NGROK_URL"
echo "🔗 Local Server URL  : http://127.0.0.1:5050"
echo "🏥 Remote Health URL : $NGROK_URL/health"
echo "======================================================="

# Free port 5050 if already in use
PORT_PID=$(lsof -ti :5050 2>/dev/null)
if [ -n "$PORT_PID" ]; then
    echo "⚠️  Port 5050 in use by PID $PORT_PID. Stopping old process..."
    kill -9 $PORT_PID 2>/dev/null
    sleep 1
fi

# Kill any existing ngrok process bound to 5050
pkill -f "ngrok http 5050" 2>/dev/null

# Find ngrok binary
NGROK_BIN=$(which ngrok 2>/dev/null)
if [ -z "$NGROK_BIN" ]; then
    if [ -x "/opt/homebrew/bin/ngrok" ]; then
        NGROK_BIN="/opt/homebrew/bin/ngrok"
    elif [ -x "/usr/local/bin/ngrok" ]; then
        NGROK_BIN="/usr/local/bin/ngrok"
    else
        NGROK_BIN="ngrok"
    fi
fi

# Cleanup handler on exit (Ctrl+C)
cleanup() {
    echo ""
    echo "🛑 Shutting down BorrowBox backend server..."
    if [ -n "$NODE_PID" ]; then
        kill $NODE_PID 2>/dev/null
    fi
    exit 0
}
trap cleanup SIGINT SIGTERM EXIT

# Start Node server in background
echo "🚀 Starting Node server on 0.0.0.0:5050..."
node src/server.js &
NODE_PID=$!

# Wait 2 seconds for Node server to be ready
sleep 2

# Start ngrok tunnel
echo "🌍 Starting ngrok tunnel: $NGROK_URL -> http://localhost:5050 ..."
exec "$NGROK_BIN" http 5050 --url "$NGROK_URL"
