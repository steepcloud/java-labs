import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import javax.net.ssl.HttpsURLConnection;
import org.json.*;

public class StockServerWithAPI {
    private static final int PORT = 8888;
    private static final Map<String, Double> stockPrices = new ConcurrentHashMap<>();
    private static final Map<String, ClientHandler> clients = new ConcurrentHashMap<>();
    
    // Finnhub API configuration - LOADED FROM CONFIG FILE
    private static String API_KEY = ""; // Loaded from api_config.properties
    private static final String API_URL = "https://finnhub.io/api/v1/quote?symbol=%s&token=%s";
    private static boolean USE_REAL_API = false; // Auto-detected from config file
    
    private static final Random random = new Random();

    private static void loadAPIConfig() {
        try {
            Properties props = new Properties();
            try (FileInputStream fis = new FileInputStream("api_config.properties")) {
                props.load(fis);
            }
            
            API_KEY = props.getProperty("api.key", "");
            USE_REAL_API = Boolean.parseBoolean(props.getProperty("use.real.api", "true"));
            
            if (API_KEY.isEmpty()) {
                System.err.println("[WARNING] API key not found in api_config.properties, using simulated data");
                USE_REAL_API = false;
            }
        } catch (IOException e) {
            System.err.println("[WARNING] Could not load api_config.properties: " + e.getMessage());
            System.err.println("[WARNING] Using simulated data instead");
            USE_REAL_API = false;
        }
    }

    private static final String[] AVAILABLE_STOCKS = {
        "AMZN", "TSLA", "AAPL", "GOOGL", "MSFT",
        "META", "NFLX", "NVDA", "BABA", "INTC", "AMD",
        "IBM", "ORCL", "CSCO", "SAP", "ADBE",
        "SOFI", "PYPL", "UBER", "LYFT", "TWTR"
    };
    
    public static void main(String[] args) {
        System.out.println("/\\/\\/\\ Stock Monitoring Server (API Version) /\\/\\/\\");
        System.out.println("Starting server on port " + PORT + "...");
        
        // load API configuration from file
        loadAPIConfig();
        
        System.out.println("API Mode: " + (USE_REAL_API ? "REAL DATA" : "SIMULATED DATA"));
        
        initializeStocks();

        new Thread(new PriceUpdater()).start();
        
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
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
            if (USE_REAL_API) {
                try {
                    double price = fetchRealStockPrice(symbol);
                    stockPrices.put(symbol, price);
                    System.out.printf("[API] Loaded %s: $%.2f\n", symbol, price);
                } catch (IOException | JSONException e) {
                    System.err.println("[ERROR] Failed to load " + symbol + ": " + e.getMessage());
                    stockPrices.put(symbol, 100.0 + random.nextDouble() * 400.0);
                }
            } else {
                stockPrices.put(symbol, 100.0 + random.nextDouble() * 400.0);
            }
        }
        System.out.println("[SERVER] Initialized " + AVAILABLE_STOCKS.length + " stocks");
    }
    
    // Fetch real stock price from Finnhub API
    private static double fetchRealStockPrice(String symbol) throws IOException {
        String urlString = String.format(API_URL, symbol, API_KEY);
        URI uri = URI.create(urlString);
        URL url = uri.toURL();
        HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
        
        try {
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            
            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                throw new IOException("API returned code: " + responseCode);
            }
            
            StringBuilder response = new StringBuilder();
            String line;
            try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                while ((line = in.readLine()) != null) {
                    response.append(line);
                }
            }
            
            // parse JSON response
            JSONObject json = new JSONObject(response.toString());
            double currentPrice = json.getDouble("c"); // 'c' is current price
            
            if (currentPrice <= 0) {
                throw new IOException("Invalid price received");
            }
            
            return currentPrice;
            
        } finally {
            conn.disconnect();
        }
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
    
    // thread that updates prices
    static class PriceUpdater implements Runnable {
        @Override
        public void run() {
            System.out.println("[UPDATER] Price updater started\n");
            
            while (true) {
                try {
                    Thread.sleep(USE_REAL_API ? 10000 : 3000); // Real API: 10s, Simulated: 3s
                    
                    String symbol = AVAILABLE_STOCKS[random.nextInt(AVAILABLE_STOCKS.length)];
                    double oldPrice = stockPrices.get(symbol);
                    double newPrice;
                    
                    if (USE_REAL_API) {
                        try {
                            newPrice = fetchRealStockPrice(symbol);
                            double change = newPrice - oldPrice;
                            String arrow = change > 0 ? "↑" : (change < 0 ? "↓" : "→");
                            System.out.printf("[API] %s: %.2f %s %.2f (%.2f%%)\n", 
                                symbol, oldPrice, arrow, newPrice, (change / oldPrice) * 100);
                        } catch (IOException e) {
                            System.err.println("[ERROR] Failed to fetch " + symbol + ", using simulation");
                            double change = (random.nextDouble() - 0.5) * oldPrice * 0.1;
                            newPrice = Math.max(1.0, oldPrice + change);
                        }
                    } else {
                        // simulated price update
                        double change = (random.nextDouble() - 0.5) * oldPrice * 0.1;
                        newPrice = Math.max(1.0, oldPrice + change);
                        String arrow = change > 0 ? "↑" : (change < 0 ? "↓" : "→");
                        System.out.printf("[UPDATER] %s: %.2f %s %.2f (%.2f%%)\n", 
                            symbol, oldPrice, arrow, newPrice, (change / oldPrice) * 100);
                    }
                    
                    stockPrices.put(symbol, newPrice);
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
                
                String dataSource = USE_REAL_API ? "REAL-TIME" : "SIMULATED";
                sendMessage("WELCOME|Connected to Stock Server (" + dataSource + " data). Commands: ADD, DEL, QUIT");
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