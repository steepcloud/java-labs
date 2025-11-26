import java.io.*;
import java.util.*;
import java.util.regex.*;

public class LogSplitter {
    private static final Pattern KEYWORD_PATTERN = Pattern.compile("\\]\\s*\\[([a-zA-Z]+)\\]");
    
    public static void main(String[] args) {
        String inputFile = "Apache_2k.log";
        Map<String, BufferedWriter> writers = new HashMap<>();
        
        try (BufferedReader reader = new BufferedReader(new FileReader(inputFile), 8192)) {
            String line;
            
            while ((line = reader.readLine()) != null) {
                String keyword = extractKeyword(line);
                
                if (keyword != null) {
                    BufferedWriter writer = writers.computeIfAbsent(keyword, k -> {
                        try {
                            String fileName = "Apache_2k-[" + k + "].log";
                            System.out.println("Created file: " + fileName);
                            return new BufferedWriter(new FileWriter(fileName), 8192);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
                    
                    writer.write(line);
                    writer.newLine();
                }
            }
            
            System.out.println("\nProcessing complete!");
            System.out.println("Files created: " + writers.size());
            writers.keySet().forEach(k -> System.out.println("  - Apache_2k-[" + k + "].log"));
            
        } catch (IOException | UncheckedIOException e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            writers.values().forEach(writer -> {
                try {
                    writer.close();
                } catch (IOException e) {
                    System.err.println("Error closing file: " + e.getMessage());
                }
            });
        }
    }
    
    private static String extractKeyword(String line) {
        Matcher matcher = KEYWORD_PATTERN.matcher(line);
        return matcher.find() ? matcher.group(1).toLowerCase() : null;
    }
}