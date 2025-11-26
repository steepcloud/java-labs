import java.io.IOException;
import java.net.*;
import java.nio.file.*;
import java.util.stream.Collectors;

public class FileServerUDP {
    private static Path rootDirectory;
    private Path currentDirectory;

    public FileServerUDP(String rootDir) throws IOException {
        this.rootDirectory = Paths.get(rootDir).toAbsolutePath().normalize();
        if (!Files.isDirectory(rootDirectory)) {
            throw new IOException("The specified root directory is not valid.");
        }
        this.currentDirectory = this.rootDirectory;
    }

    public void start(int port) {
        try (DatagramSocket socket = new DatagramSocket(port)) {
            System.out.println("UDP Server started on port " + port + ". Waiting for packets...");
            byte[] buffer = new byte[1024];

            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                String commandLine = new String(packet.getData(), 0, packet.getLength()).trim();
                System.out.println("Command received from " + packet.getAddress() + ":" + packet.getPort() + ": " + commandLine);

                String response = executeCommand(commandLine);
                
                byte[] responseData = response.getBytes();
                DatagramPacket responsePacket = new DatagramPacket(responseData, responseData.length, packet.getAddress(), packet.getPort());
                socket.send(responsePacket);

                if (commandLine.equalsIgnoreCase("exit")) {
                    System.out.println("'exit' command received. Resetting current directory.");
                    this.currentDirectory = this.rootDirectory; 
                }
            }
        } catch (IOException e) {
            System.err.println("Error starting or running UDP server: " + e.getMessage());
        }
    }

	private String executeCommand(String commandLine) {
        String[] parts = commandLine.trim().split("\\s+");
        String command = parts[0].toLowerCase();
        try {
            switch (command) {
                case "dir":
                    return handleDir();
                case "cd":
                    if (parts.length < 2) return "ERROR: Invalid 'cd' command format.";
                    String target = parts[1];
                    if (target.equals("..")) {
                        return handleCdParent();
                    } else {
                        return handleCdPath(target);
                    }
                case "mkdir":
                    if (parts.length < 2) return "ERROR: Invalid 'mkdir' command format.";
                    return handleMkdir(parts[1]);
                case "copy":
                    if (parts.length < 3) return "ERROR: Invalid 'copy' command format. Usage: copy source/file destination";
                    return handleCopy(parts[1], parts[2]);
                case "exit":
                    return "SUCCESS: Disconnecting.";
                default:
                    return "ERROR: Unknown command.";
            }
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }
    
	private String handleDir() throws IOException {
        String relativePath = rootDirectory.relativize(currentDirectory).toString();

        String fileList = Files.list(currentDirectory)
                .map(path -> {
                    String name = path.getFileName().toString();
                    return Files.isDirectory(path) ? "[DIR] " + name : "[FILE] " + name;
                })
                .collect(Collectors.joining("\n"));

        return "SUCCESS: Current directory: /" + (relativePath.isEmpty() ? "" : relativePath + "/") + "\n" + fileList;
    }
    
	private String handleCdParent() {
        if (currentDirectory.equals(rootDirectory)) {
            return "ERROR: Cannot navigate above the virtual root directory.";
        }
        Path parent = currentDirectory.getParent();
        if (parent != null) {
            currentDirectory = parent;
            return "SUCCESS: Current directory changed to parent.";
        }
        return "ERROR: Cannot change to parent directory.";
    }
    
	private String handleMkdir(String folderName) throws IOException {
        Path newPath = currentDirectory.resolve(folderName).normalize();
        
        if (!newPath.startsWith(rootDirectory)) {
            return "ERROR: Operation attempted outside the virtual root directory.";
        }

        if (Files.exists(newPath)) {
            return "ERROR: Directory '" + folderName + "' already exists.";
        }

        Files.createDirectory(newPath);
        return "SUCCESS: Directory '" + folderName + "' created.";
    }
    
	private String handleCdPath(String relativeOrAbsolutePath) throws IOException {
        Path newPath;

        if (relativeOrAbsolutePath.startsWith("/")) {
            String pathStr = relativeOrAbsolutePath.substring(1); 
            newPath = rootDirectory.resolve(pathStr).normalize();
        } else {
            newPath = currentDirectory.resolve(relativeOrAbsolutePath).normalize();
        }

        if (!newPath.startsWith(rootDirectory)) {
            return "ERROR: Specified path '" + relativeOrAbsolutePath + "' is outside the virtual root directory.";
        }

        if (Files.isDirectory(newPath)) {
            currentDirectory = newPath;
            return "SUCCESS: Current directory changed to: " + rootDirectory.relativize(currentDirectory).toString();
        } else {
            return "ERROR: Specified path '" + relativeOrAbsolutePath + "' is not a valid directory or does not exist.";
        }
    }
	
	private String handleCopy(String sourcePathStr, String destPathStr) throws IOException {
        Path sourcePath = currentDirectory.resolve(sourcePathStr).normalize();
        Path destDir = currentDirectory.resolve(destPathStr).normalize();

        if (!sourcePath.startsWith(rootDirectory) || !destDir.startsWith(rootDirectory)) {
            return "ERROR: Operation attempted outside the virtual root directory.";
        }
        
        if (!Files.isRegularFile(sourcePath)) {
            return "ERROR: Source is not a valid file or does not exist: " + sourcePathStr;
        }

        if (!Files.isDirectory(destDir)) {
            return "ERROR: Destination is not a valid directory or does not exist: " + destPathStr;
        }

        String fileName = sourcePath.getFileName().toString();
        Path finalDestPath = destDir.resolve(fileName);

        Files.copy(sourcePath, finalDestPath, StandardCopyOption.REPLACE_EXISTING);
        
        return "SUCCESS: File '" + fileName + "' copied from " + sourcePathStr + " to " + destPathStr;
    }

    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("Usage: java FileServerUDP <port> <root_folder>");
            return;
        }

        try {
            int port = Integer.parseInt(args[0]);
            String rootDir = args[1];
            FileServerUDP server = new FileServerUDP(rootDir);
            server.start(port);
        } catch (NumberFormatException e) {
            System.err.println("The specified port is not a valid number.");
        } catch (IOException e) {
            System.err.println("Error initializing the server: " + e.getMessage());
        }
    }
}