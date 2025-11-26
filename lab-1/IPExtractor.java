import java.io.*;
import java.util.*;
import java.util.regex.*;

public class IPExtractor {
    private static final Pattern IPV4_PATTERN = Pattern.compile(
        "\\b(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\b"
    );
    
    public static void main(String[] args) {
        String inputFile = "Linux_2k.log";
        String outputFile = "Linux2k_IP_stat.txt";
        
        Map<String, Integer> ipCount = new HashMap<>();
        
        try (BufferedReader reader = new BufferedReader(new FileReader(inputFile), 8192)) {
            String line;
            
            while ((line = reader.readLine()) != null) {
                Matcher matcher = IPV4_PATTERN.matcher(line);
                
                while (matcher.find()) {
                    String ip = matcher.group();
                    ipCount.merge(ip, 1, Integer::sum);
                }
            }
            
            System.out.println("Total unique IPs found: " + ipCount.size());
            System.out.println("Total IP occurrences: " + ipCount.values().stream().mapToInt(Integer::intValue).sum());
            
        } catch (IOException e) {
            System.err.println("Error reading file: " + e.getMessage());
            e.printStackTrace();
            return;
        }
        
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile), 8192)) {
            ipCount.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .forEach(entry -> {
                    try {
                        writer.write(entry.getKey() + " " + entry.getValue());
                        writer.newLine();
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
            
            System.out.println("Statistics written to: " + outputFile);
            
        } catch (IOException | UncheckedIOException e) {
            System.err.println("Error writing file: " + e.getMessage());
            e.printStackTrace();
        }
    }
}