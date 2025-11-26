import java.io.IOException;
import java.net.*;
import java.util.Scanner;

public class FileClientUDP {

    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("Usage: java FileClientUDP <server_IP_address> <port>");
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

        try (DatagramSocket socket = new DatagramSocket()) {
            InetAddress address = InetAddress.getByName(hostname);
            Scanner scanner = new Scanner(System.in);
            byte[] receiveBuffer = new byte[65535]; // max UDP buffer size

            System.out.println("UDP Client started. Enter commands (dir, cd .., exit):");
            String userInput;

            while (true) {
                System.out.print("> ");
                userInput = scanner.nextLine();
                
                if (userInput.trim().isEmpty()) continue;
                
                byte[] sendData = userInput.getBytes();
                DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, address, port);
                socket.send(sendPacket);

                if (userInput.trim().equalsIgnoreCase("exit")) {
                    break;
                }

                DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
                socket.receive(receivePacket);

                String response = new String(receivePacket.getData(), 0, receivePacket.getLength());
                System.out.println(response);
            }

        } catch (UnknownHostException e) {
            System.err.println("Unknown host: " + hostname);
        } catch (IOException e) {
            System.err.println("I/O Error: " + e.getMessage());
        }
    }
}