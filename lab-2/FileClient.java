import java.io.*;
import java.net.*;
import java.util.Scanner;

public class FileClient {

    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("Usage: java FileClient <server_IP_address> <port>");
            return;
        }

        String hostname = args[0];
        int port = 0;
        try {
            port = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            System.err.println("The specified port is not a valid number.");
            return;
        }

        try (Socket socket = new Socket(hostname, port);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             Scanner scanner = new Scanner(System.in)) {

            System.out.println("Connected to server. Enter commands (dir, cd .., cd path, mkdir name, copy source dest, exit):");
            String userInput;
            while (true) {
                System.out.print("> ");
                userInput = scanner.nextLine();
                
                if (userInput.trim().isEmpty()) continue;

                out.println(userInput); // send command

                // read server response
                String responseLine;
                while ((responseLine = in.readLine()) != null) {
                    System.out.println(responseLine);
                    if (responseLine.startsWith("SUCCESS") || responseLine.startsWith("ERROR")) {
                        break; 
                    }
                }

                if (userInput.trim().equalsIgnoreCase("exit")) {
                    break;
                }
            }

        } catch (UnknownHostException e) {
            System.err.println("Unknown host: " + hostname);
        } catch (IOException e) {
            System.err.println("I/O Error: " + e.getMessage());
        }
    }
}