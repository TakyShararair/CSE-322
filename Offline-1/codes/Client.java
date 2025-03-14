package main;

import Threads.ClientThreads;

import java.util.Scanner;

public class Client {

    private static final int Port = 5098;
    private static final String ClientFilesPath = "D:\\server\\Offline1";
    public static void main(String[] args) {
        System.out.println("Enter a filename for uploading : ");

        try(Scanner scanner = new Scanner(System.in))
        {
            while (scanner.hasNextLine())
            {
                String input = scanner.nextLine();
                new ClientThreads(input,ClientFilesPath,Port);
            }
        }catch (Exception e)
        {
            e.printStackTrace();
        }
    }
}
