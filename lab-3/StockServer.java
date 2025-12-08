import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class StockServer {
    private static final int PORT = 8888;
    private static final Map<String, Double> stockPrices = new ConcurrentHashMap<>();
    private static final Map<String, ClientHandler> clients = new ConcurrentHashMap<>();
    private static final Random random = new Random();

    private static final String[] AVAILABLE_STOCKS = {
        "AMZN", "TSLA", "AAPL", "GOOGL", "MSFT",
        "META", "NFLX", "NVDA", "BABA", "INTC", "AMD",
        "IBM", "ORCL", "CSCO", "SAP", "ADBE",
        "SOFI", "PYPL", "UBER", "LYFT", "TWTR"
    };
    
    public static void main(String[] args) {
        System.out.println("/\\/\\/\\ Stock Monitoring Server /\\/\\/\\");
        System.out.println("Starting server on port " + PORT + "...");
        
        initializeStocks();

        new Thread(new PriceUpdater()).start(); // price update thread
        
        try (ServerSocket serverSocket = new ServerSocket(PORT)) { // client conns
            System.out.println("Server started successfully!");
            System.out.println("Waiting for clients...\n");
            
            while (true) {
                Socket clientSocket = serverSocket.accept();
                String clientId = "Client-" + System.currentTimeMillis();
                System.out.println("[SERVER] New connection: " + clientId);
                
                ClientHandler handler = new ClientHandler(clientSocket, clientId);
                clients.put(clientId, handler);
                new Thread(handler).start();
            }
        } catch (IOException e) {
            System.err.println("[ERROR] Server error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void initializeStocks() {
        for (String symbol : AVAILABLE_STOCKS) {
            stockPrices.put(symbol, 100.0 + random.nextDouble() * 400.0);
        }
        System.out.println("[SERVER] Initialized " + AVAILABLE_STOCKS.length + " stocks");
    }
    
    static void broadcastPriceUpdate(String symbol, double oldPrice, double newPrice) {
        double change = newPrice - oldPrice;
        double percentChange = (change / oldPrice) * 100;
        
        String message = String.format("UPDATE|%s|%.2f|%.2f|%.2f", 
            symbol, newPrice, change, percentChange);
        
        for (ClientHandler client : clients.values()) {
            if (client.isMonitoring(symbol)) {
                client.sendMessage(message);
            }
        }
    }
    
    static void removeClient(String clientId) {
        clients.remove(clientId);
        System.out.println("[SERVER] Client disconnected: " + clientId);
        System.out.println("[SERVER] Active clients: " + clients.size());
    }
    
    static double getStockPrice(String symbol) {
        return stockPrices.getOrDefault(symbol, -1.0);
    }
    
    static boolean isValidSymbol(String symbol) {
        return stockPrices.containsKey(symbol);
    }
    
    // thread that simulates price updates
    static class PriceUpdater implements Runnable {
        @Override
        public void run() {
            System.out.println("[UPDATER] Price updater started\n");
            
            while (true) {
                try {
                    Thread.sleep(3000 + random.nextInt(2000)); // 3-5 secs
                    
                    // pick random stock
                    String symbol = AVAILABLE_STOCKS[random.nextInt(AVAILABLE_STOCKS.length)];
                    double oldPrice = stockPrices.get(symbol);
                    
                    // gen. new price (+/- 10%)
                    double change = (random.nextDouble() - 0.5) * oldPrice * 0.1;
                    double newPrice = Math.max(1.0, oldPrice + change);
                    
                    stockPrices.put(symbol, newPrice);
                    
                    System.out.printf("[UPDATER] %s: %.2f -> %.2f (%.2f%%)\n", 
                        symbol, oldPrice, newPrice, (change/oldPrice)*100);
                    
                    broadcastPriceUpdate(symbol, oldPrice, newPrice);
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }
    
    // handles individual client
    static class ClientHandler implements Runnable {
        private final Socket socket;
        private final String clientId;
        private final Set<String> monitoredSymbols = ConcurrentHashMap.newKeySet();
        private PrintWriter out;
        private BufferedReader in;
        
        ClientHandler(Socket socket, String clientId) {
            this.socket = socket;
            this.clientId = clientId;
        }
        
        @Override
        public void run() {
            try {
                out = new PrintWriter(socket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                
                sendMessage("WELCOME|Connected to Stock Server. Available commands: ADD, DEL, QUIT");
                sendMessage("INFO|Available stocks: " + String.join(", ", AVAILABLE_STOCKS));
                
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    System.out.println("[" + clientId + "] Received: " + inputLine);
                    handleCommand(inputLine.trim());
                }
                
            } catch (IOException e) {
                System.out.println("[" + clientId + "] Connection error: " + e.getMessage());
            } finally {
                cleanup();
            }
        }
        
        private void handleCommand(String command) {
            String[] parts = command.split("\\s+");
            if (parts.length == 0) return;
            
            String cmd = parts[0].toUpperCase();
            
            switch (cmd) {
                case "ADD" -> {
                    if (parts.length < 2) {
                        sendMessage("ERROR|Usage: ADD <SYMBOL>");
                        return;
                    }
                    handleAdd(parts[1].toUpperCase());
                }
                    
                case "DEL" -> {
                    if (parts.length < 2) {
                        sendMessage("ERROR|Usage: DEL <SYMBOL>");
                        return;
                    }
                    handleDelete(parts[1].toUpperCase());
                }
                    
                case "QUIT" -> {
                    sendMessage("BYE|Disconnecting...");
                    cleanup();
                }
                    
                default -> sendMessage("ERROR|Unknown command: " + cmd);
            }
        }
        
        private void handleAdd(String symbol) {
            if (!isValidSymbol(symbol)) {
                sendMessage("ERROR|Invalid symbol: " + symbol);
                return;
            }
            
            if (monitoredSymbols.size() >= 5) {
                sendMessage("ERROR|Maximum 5 symbols allowed");
                return;
            }
            
            if (monitoredSymbols.contains(symbol)) {
                sendMessage("ERROR|Already monitoring: " + symbol);
                return;
            }
            
            monitoredSymbols.add(symbol);
            double price = getStockPrice(symbol);
            sendMessage(String.format("ADDED|%s|%.2f", symbol, price));
            System.out.println("[" + clientId + "] Added: " + symbol);
        }
        
        private void handleDelete(String symbol) {
            if (monitoredSymbols.remove(symbol)) {
                sendMessage("DELETED|" + symbol);
                System.out.println("[" + clientId + "] Deleted: " + symbol);
            } else {
                sendMessage("ERROR|Not monitoring: " + symbol);
            }
        }
        
        boolean isMonitoring(String symbol) {
            return monitoredSymbols.contains(symbol);
        }
        
        void sendMessage(String message) {
            if (out != null) {
                out.println(message);
            }
        }
        
        private void cleanup() {
            try {
                if (in != null) in.close();
                if (out != null) out.close();
                if (socket != null) socket.close();
            } catch (IOException e) {
                // ignore
            }
            removeClient(clientId);
        }
    }
}