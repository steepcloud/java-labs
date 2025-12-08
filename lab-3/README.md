# Stock Monitoring Client-Server Application

A real-time stock price monitoring system built with Java sockets and multithreading.

## Quick Start

### In your IDE (or w/e you're using):

```bash
# Compile
javac StockServer.java
javac StockClient.java

# Run Server (Terminal 1)
java StockServer

# Run Client (Terminal 2)
java StockClient
```

## Features

- Real-time stock price updates
- Multiple concurrent clients
- Monitor up to 5 stocks per client
- Thread-safe operations
- Simple command-line interface
- Automatic price generation (3-5 second intervals)

## Commands

| Command | Description | Example |
|---------|-------------|---------|
| `ADD <SYMBOL>` | Add stock to watchlist | `ADD MSFT` |
| `DEL <SYMBOL>` | Remove stock from watchlist | `DEL AAPL` |
| `QUIT` | Disconnect from server | `QUIT` |
| `HELP` | Show available commands | `HELP` |

## Available Stocks

MSFT, AAPL, GOOGL, AMZN, TSLA, META, NVDA, NFLX, BABA, INTC, AMD, IBM, ORCL, CSCO, SAP, ADBE, SOFI, PYPL, UBER, LYFT, TWTR

## Example Output

```
> ADD MSFT
[SUCCESS] Added MSFT to monitoring list (Current price: $345.67)

[UPDATE] MSFT  $352.10  +6.43 (+1.86%) ↑
[UPDATE] MSFT  $348.75  -3.35 (-0.95%) ↓

> DEL MSFT
[SUCCESS] Removed MSFT from monitoring list

> QUIT
Disconnecting...
```

## Architecture

```
┌─────────────┐         ┌─────────────┐
│   Client 1  │◄───────►│             │
├─────────────┤         │             │
│   Client 2  │◄───────►│   Server    │
├─────────────┤         │   :8888     │
│   Client 3  │◄───────►│             │
└─────────────┘         └─────────────┘
                              │
                              ▼
                        PriceUpdater
                        (Background)
```

### Server Components:
- **Main Thread**: Accepts client connections
- **PriceUpdater Thread**: Generates price updates
- **ClientHandler Threads**: One per connected client

### Client Components:
- **Main Thread**: Handles user input
- **ServerListener Thread**: Receives server messages

## Technical Details

- **Port**: 8888
- **Protocol**: TCP/IP (Java Sockets)
- **Thread Safety**: ConcurrentHashMap
- **Update Frequency**: 3-5 seconds
- **Price Variation**: ±10% random
- **Max Stocks/Client**: 5

## Project Structure

```
.
├── StockServer.java         # Server implementation
├── StockClient.java         # Client implementation
└── README.md               # This file
```

## Requirements

- Java JDK 8 or higher
- No external libraries required

## Testing Multiple Clients

Open multiple terminals and run:
```bash
# Terminal 1
java StockServer

# Terminal 2
java StockClient

# Terminal 3
java StockClient

# Terminal 4
java StockClient
```

Each client can monitor different stocks independently!

## Communication Protocol

### Client → Server
```
ADD MSFT
DEL AAPL
QUIT
```

### Server → Client
```
WELCOME|<message>
ADDED|<symbol>|<price>
DELETED|<symbol>
UPDATE|<symbol>|<new_price>|<change>|<percent>
ERROR|<message>
BYE|<message>
```