package Threads;

import java.io.*;
import java.net.Socket;

public class ClientThreads implements Runnable {
    private static final int BUFFER_SIZE = 512; // Buffer size for file transfer
    private final String filename;
    private final String locationPath;
    private final int port;
    private Thread thread;

    public ClientThreads(String filename, String location, int port) {
        this.filename = filename;
        this.locationPath = location;
        this.port = port;
        this.thread = new Thread(this);
        this.thread.start();
    }

    @Override
    public void run() {
        File inputFile = new File(locationPath, filename);

        if (!inputFile.exists()) {
            System.out.println(">>> Given file doesn't exist in the directory");
            return;
        }

        try (Socket clientSocket = new Socket("localhost", port);
             PrintWriter printWriter = new PrintWriter(clientSocket.getOutputStream(), true);
             BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()))) {

            // Send upload request to server
            printWriter.println("UPLOAD " + filename);

            // Check server response
            String response = bufferedReader.readLine();
            if ("invalid".equalsIgnoreCase(response)) {
                System.out.println(">>> Given file name is invalid");
                return;
            }

            // Upload the file
            try (BufferedInputStream fileInput = new BufferedInputStream(new FileInputStream(inputFile));
                 OutputStream out = clientSocket.getOutputStream()) {

                byte[] buffer = new byte[BUFFER_SIZE];
                int bytesRead;
                while ((bytesRead = fileInput.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                    out.flush();
                }
            } catch (IOException e) {
                System.err.println("Error during file upload: " + e.getMessage());
                e.printStackTrace();
            }
        } catch (IOException e) {
            System.err.println("Error during client-server communication: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
