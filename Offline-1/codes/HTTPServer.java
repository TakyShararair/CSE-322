package main;

import Threads.ServerThreads;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

public class HTTPServer {
    private static final int PORT = 5098;  // Port number
    private static final String ROOT_PATH = "D:\\server\\Offline1";
    private static final String LOG_PATH = "D:\\server\\Offline1\\logs";
    private static final String UPLOAD_PATH = "D:\\server\\Offline1\\Upload";

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("------Server Started-------");
            System.out.println("Server Port number is : " + PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Connected Successfully");

                // Start a new thread to handle the client connection
                new ServerThreads(clientSocket, ROOT_PATH, LOG_PATH, UPLOAD_PATH, PORT);
            }
        } catch (IOException e) {
            System.err.println("Error starting server: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
