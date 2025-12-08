import java.io.*;
import java.net.*;
import java.util.*;

public class StockClient {
    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 8888;
    private static Socket socket;
    private static PrintWriter out;
    private static BufferedReader in;
    private static final Map<String, Double> lastPrices = new HashMap<>();
    
    public static void main(String[] args) {
        String host = DEFAULT_HOST;
        int port = DEFAULT_PORT;
        
        // parsing cmd arguments
        if (args.length >= 1) host = args[0];
        if (args.length >= 2) {
            try {
                port = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number. Using default: " + DEFAULT_PORT);
            }
        }
        
        System.out.println("/\\/\\/\\ Stock Monitoring Client /\\/\\/\\");
        System.out.println("Connecting to " + host + ":" + port + "...");
        
        try {
            socket = new Socket(host, port);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            
            System.out.println("Connected successfully!\n");
            
            // starting thread to receive server messages
            new Thread(new ServerListener()).start();
            
            // reading commands from console
            handleUserInput();
            
        } catch (UnknownHostException e) {
            System.err.println("Unknown host: " + host);
        } catch (IOException e) {
            System.err.println("Connection error: " + e.getMessage());
        } finally {
            cleanup();
        }
    }
    
    private static void handleUserInput() {
        try (BufferedReader console = new BufferedReader(new InputStreamReader(System.in))) {
            printHelp();
            
            while (true) {
                System.out.print("\n> ");
                String input = console.readLine();
                
                if (input == null || input.trim().isEmpty()) {
                    continue;
                }
                
                input = input.trim().toUpperCase();
                
                if (input.equals("HELP")) {
                    printHelp();
                    continue;
                }
                
                if (input.equals("LIST")) {
                    displayPortfolio();
                    continue;
                }
                
                if (input.equals("QUIT")) {
                    out.println("QUIT");
                    System.out.println("Disconnecting...");
                    break;
                }
                
                // send command to server
                out.println(input);
            }
        } catch (IOException e) {
            System.err.println("Input error: " + e.getMessage());
        }
    }
    
    private static void printHelp() {
        System.out.println("\n--- Available Commands ---");
        System.out.println("ADD <SYMBOL>  - Add stock to monitoring list (max 5)");
        System.out.println("DEL <SYMBOL>  - Remove stock from monitoring list");
        System.out.println("LIST          - Show your current monitored stocks");
        System.out.println("QUIT          - Disconnect from server");
        System.out.println("HELP          - Show this help message");
        System.out.println("\nExample: ADD MSFT");
    }
    
    private static void displayPortfolio() {
        if (lastPrices.isEmpty()) {
            System.out.println("\n[INFO] No stocks in monitoring list");
            System.out.println("Use 'ADD <SYMBOL>' to add stocks");
            return;
        }
        
        System.out.println("\n--- Your Monitored Stocks ---");
        System.out.println("Symbol    Current Price");
        System.out.println("------------------------");
        
        lastPrices.forEach((symbol, price) -> 
            System.out.printf("%-8s  $%.2f\n", symbol, price)
        );
        
        System.out.println("------------------------");
        System.out.printf("Total: %d stock(s)\n", lastPrices.size());
    }
    
    private static void cleanup() {
        try {
            if (out != null) out.close();
            if (in != null) in.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            // ignore
        }
        System.out.println("\nDisconnected from server.");
    }
    
    // thread that listens for server messages
    static class ServerListener implements Runnable {
        @Override
        public void run() {
            try {
                String message;
                while ((message = in.readLine()) != null) {
                    handleServerMessage(message);
                }
            } catch (IOException e) {
                System.err.println("\nConnection to server lost.");
                System.exit(0);
            }
        }
        
        private void handleServerMessage(String message) {
            String[] parts = message.split("\\|");
            if (parts.length == 0) return;
            
            String type = parts[0];
            
            switch (type) {
                case "WELCOME" -> System.out.println("\n[SERVER] " + parts[1]);
                case "INFO" -> System.out.println("[INFO] " + parts[1]);
                case "ADDED" -> {
                    if (parts.length >= 3) {
                        String symbol = parts[1];
                        double price = Double.parseDouble(parts[2]);
                        lastPrices.put(symbol, price);
                        System.out.printf("\n[SUCCESS] Added %s to monitoring list (Current price: $%.2f)\n", 
                            symbol, price);
                    }
                }
                case "DELETED" -> {
                    if (parts.length >= 2) {
                        String symbol = parts[1];
                        lastPrices.remove(symbol);
                        System.out.println("\n[SUCCESS] Removed " + symbol + " from monitoring list");
                    }
                }
                case "UPDATE" -> {
                    if (parts.length >= 4) {
                        handlePriceUpdate(parts[1], parts[2], parts[3], parts[4]);
                    }
                }
                case "ERROR" -> {
                    if (parts.length >= 2) {
                        System.out.println("\n[ERROR] " + parts[1]);
                    }
                }
                case "BYE" -> {
                    System.out.println("\n[SERVER] " + parts[1]);
                    System.exit(0);
                }
                default -> System.out.println("\n[UNKNOWN] " + message);
            }
        }
        
        private void handlePriceUpdate(String symbol, String priceStr, 
                                       String changeStr, String percentStr) {
            try {
                double price = Double.parseDouble(priceStr);
                double change = Double.parseDouble(changeStr);
                double percent = Double.parseDouble(percentStr);
                
                String changeSign = change >= 0 ? "+" : "";
                String colorIndicator = change >= 0 ? "↑" : "↓";
                
                System.out.printf("\n[UPDATE] %s  $%.2f  %s%.2f (%s%.2f%%) %s\n",
                    symbol, price, changeSign, change, changeSign, percent, colorIndicator);
                
                lastPrices.put(symbol, price);
                
            } catch (NumberFormatException e) {
                System.err.println("Invalid price update format");
            }
        }
    }
}