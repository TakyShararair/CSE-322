package Threads;

import java.io.*;
import java.net.Socket;
import java.util.Date;

public class ServerThreads implements Runnable {
    private Socket threadedSocket;
    private String rootPath;
    private String logPath;
    private String uploadPath;

    private static int PORT;
    private static int requestNo = 0;
    private Thread thread;
    private static final String HTML_INTRO;

    static {
        StringBuilder htmlBuilder = new StringBuilder();
        htmlBuilder.append("<html>\n")
                .append("<head>\n")
                .append("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\">\n")
                .append("</head>\n")
                .append("<body>\n");
        HTML_INTRO = htmlBuilder.toString();
    }

    private static final String ERROR_MSG;
    private static final String HTML_END;

    static {
        StringBuilder errorMsgBuilder = new StringBuilder();
        errorMsgBuilder.append("<h1> 404: Page not found </h1>\n");
        ERROR_MSG = errorMsgBuilder.toString();

        StringBuilder htmlEndBuilder = new StringBuilder();
        htmlEndBuilder.append("</body>\n")
                .append("</html>");
        HTML_END = htmlEndBuilder.toString();
    }




    public ServerThreads(Socket socket, String root, String log, String upload, int port) {
        this.threadedSocket = socket;
        this.rootPath = root;
        this.logPath = log;
        this.uploadPath = upload;
        this.PORT = port;
        this.thread = new Thread(this);
        thread.start();
    }

    @Override
    public void run() {

        // creating buffered reader

        BufferedReader bufferedReader = null;
        try {
            bufferedReader = new BufferedReader(new InputStreamReader(threadedSocket.getInputStream()));
        } catch (IOException e) {
            System.out.println("Input stream creation error!");
            e.printStackTrace();

        }

        // reading http request
        String httpRequestInput = null;
        try {
            httpRequestInput = bufferedReader.readLine();
        } catch (IOException e) {
            System.out.println("Input stream reading error!");
            e.printStackTrace();

        }

        // checking and handling GET requests

        if(httpRequestInput == null) {
            // handling null requests first
                try {
                    if(bufferedReader != null)
                    {
                        bufferedReader.close();
                    }
                    if(threadedSocket != null)
                    {
                        threadedSocket.close();
                    }

                } catch (IOException e) {
                    System.out.println("Null HTTP request handling error");
                    e.printStackTrace();

                }
                return;

        }

        else if ( httpRequestInput.startsWith("GET")){

            // handling GET request
            PrintWriter printWriter = null;
            PrintWriter fileWriter=null;
            try {
                printWriter = new PrintWriter(threadedSocket.getOutputStream());
                fileWriter = new PrintWriter(logPath + "\\Log " + (++requestNo) + ".txt");
            } catch (IOException e) {
                System.out.println("Print writer stream creation error!");
                e.printStackTrace();

            }

            // printing valid HTTP request on the server console
            System.out.println("HTTP request received from the client----");
            System.out.println(">>> " + httpRequestInput);

            // printing request log into the log file
            fileWriter.println("HTTP request received from the client ...");
            fileWriter.println(">>> " + httpRequestInput + "\n");


            StringBuilder pathExtract = new StringBuilder();
            String[] inputs = httpRequestInput.split("/");

            // ignoring the request prefix at the 0th index and the version 1.1 at the last index
            for(int i=1; i<inputs.length-1; i++){
                // handling the last entry
                if(i == inputs.length-2){
                    pathExtract.append(inputs[i].replace(" HTTP",""));
                } else{
                    pathExtract.append(inputs[i]).append("\\");
                }
            }

            String extractedPath = pathExtract.toString();

            File file;

            // file content creation
            if(extractedPath.isEmpty()){
                file = new File(rootPath);
            } else {
                // space representation handling
                extractedPath = extractedPath.replace("%20", " ") + "\\";
                file = new File(rootPath + "\\" + extractedPath);

            }
            // start building the response
            StringBuilder stringBuilder = new StringBuilder();
            File[] insideDirectory;

            if (file.exists() && file.isDirectory()) {
                insideDirectory = file.listFiles();
                stringBuilder.append(HTML_INTRO);

                for (File f : insideDirectory) {
                    String name = f.getName();
                    String filePath = "http://localhost:" + PORT + "/" + extractedPath.replace("\\", "/") + name;

                    if (f.isDirectory()) {
                        stringBuilder.append(String.format("<b><i><a href=\"%s\"> %s </a></i></b><br>\n", filePath, name));
                    } else if (f.isFile()) {
                        stringBuilder.append(String.format("<a href=\"%s\"> %s </a><br>\n", filePath, name));
                    }
                }

                stringBuilder.append(HTML_END);
            }

            else{

                // request content doesn't exist or not found
                stringBuilder.append(HTML_INTRO);
                stringBuilder.append("<h1> 404: Page not found </h1>\n");
                stringBuilder.append(HTML_END);
            }

            System.out.println("HTTP response sent to the client ...");
            fileWriter.println("HTTP response sent to the client ...");
            String httpResponseOutput = "";


            if(httpRequestInput.length()>0){
                if(httpRequestInput.startsWith("GET")){
                    if(file.exists() && file.isDirectory()){

                        StringBuilder httpResponse = new StringBuilder();
                        httpResponse.append("HTTP/1.0 200 OK\r\n")
                                .append("Server: Java HTTP Server: 1.0\r\n")
                                .append("Date: ").append(new Date()).append("\r\n")
                                .append("Content-Type: text/html\r\n")
                                .append("Content-Length: ").append(stringBuilder.toString().length()).append("\r\n\r\n");
                        httpResponseOutput = httpResponse.toString();
                        System.out.println(">>> " + httpResponseOutput);
                        fileWriter.println(">>> " + httpResponseOutput);

                        printWriter.write(httpResponseOutput);
                        printWriter.write("\r\n");
                        // actual message body to be displayed
                        printWriter.write(stringBuilder.toString());
                        printWriter.flush();

                    } else if(file.exists() && file.isFile()){

                        if(file.getName().endsWith("txt")){
                            httpResponseOutput += "HTTP/1.0 200 OK" + "\r\n";
                            httpResponseOutput += "Server: Java HTTP Server: 1.0" + "\r\n";
                            httpResponseOutput += "Date: " + new Date() + "\r\n";
                            httpResponseOutput += "Content-Type: " + "text/html" + "\r\n";
                            httpResponseOutput += "Content-Length: " + file.length() + "\r\n";

                        } else if(file.getName().endsWith("jpg") || file.getName().endsWith("jpeg")){

                            httpResponseOutput += "HTTP/1.0 200 OK" + "\r\n";
                            httpResponseOutput += "Server: Java HTTP Server: 1.0" + "\r\n";
                            httpResponseOutput += "Date: " + new Date() + "\r\n";
                            httpResponseOutput += "Content-Type: " + "image/jpeg" + "\r\n";
                            httpResponseOutput += "Content-Length: " + file.length() + "\r\n";
                        }
                        else if(file.getName().endsWith("PNG"))
                        {
                            httpResponseOutput += "HTTP/1.0 200 OK" + "\r\n";
                            httpResponseOutput += "Server: Java HTTP Server: 1.0" + "\r\n";
                            httpResponseOutput += "Date: " + new Date() + "\r\n";
                            httpResponseOutput += "Content-Type: " + "image/png" + "\r\n";
                            httpResponseOutput += "Content-Length: " + file.length() + "\r\n";
                        }
                        else{

                            httpResponseOutput += "HTTP/1.0 200 OK" + "\r\n";
                            httpResponseOutput += "Server: Java HTTP Server: 1.0"+ "\r\n";
                            httpResponseOutput += "Date: " + new Date() + "\r\n";
                            httpResponseOutput += "Content-Type: " + "application/x-force-download" + "\r\n";
                            httpResponseOutput += "Content-Length: " + file.length() + "\r\n";
                        }

                        System.out.println(">>> " + httpResponseOutput);
                        fileWriter.println(">>> " + httpResponseOutput);

                        printWriter.write(httpResponseOutput);
                        printWriter.write("\r\n");
                        printWriter.flush();
                        // binary style byte by byte data sending in chunk
                        int bytesRead;
                        byte[] buffer = new byte[2048];

                        try {
                            // Obtain the output stream from the socket
                            OutputStream outputStream = threadedSocket.getOutputStream();

                            // Create a buffered input stream to read from the file
                            BufferedInputStream inputStream = new BufferedInputStream(new FileInputStream(file));

                            // Read from the file and write to the output stream in chunks
                            while ((bytesRead = inputStream.read(buffer)) > 0) {
                                outputStream.write(buffer, 0, bytesRead);
                                outputStream.flush();
                            }

                            // Close the streams
                            inputStream.close();
                            outputStream.close();
                        } catch (IOException e) {
                            // Handle potential I/O errors
                            e.printStackTrace();
                            System.out.println("Error occurred while processing the stream.");
                        }

                    }
                    else if(!file.exists()){

                        StringBuilder httpResponseHeader = new StringBuilder();
                        httpResponseHeader.append("HTTP/1.0 404 NOT FOUND\r\n")
                                .append("Server: Java HTTP Server/1.0\r\n")
                                .append("Date: ").append(new Date()).append("\r\n")
                                .append("Content-Type: text/html\r\n")
                                .append("Content-Length: ").append(stringBuilder.toString().length()).append("\r\n")
                                .append("\r\n");
                         // Convert StringBuilder to String if needed
                         httpResponseOutput = httpResponseHeader.toString();

                        System.out.println(">>> " + httpResponseOutput);
                        fileWriter.println(">>> " + httpResponseOutput);

                        printWriter.write(httpResponseOutput);
                        printWriter.write("\r\n");
                        printWriter.write(stringBuilder.toString());
                        printWriter.flush();
                    }
                }
            }

            try {
                bufferedReader.close();
            } catch (IOException e) {
                System.out.println("Buffered reader closing error");
                e.printStackTrace();

            }
            printWriter.close();
            fileWriter.close();
        }

        else if(httpRequestInput.startsWith("UPLOAD")){
            PrintWriter printWriter = null;
            String filename = httpRequestInput.split(" ")[1];
            if(filename.endsWith("txt") || filename.endsWith("jpg") || filename.endsWith("png") || filename.endsWith("jpeg") || filename.endsWith("mp4")){
                try {
                    printWriter = new PrintWriter(threadedSocket.getOutputStream());
                    printWriter.write("valid" + "\r\n");
                    printWriter.flush();
                    System.out.println(">>> Given file name is valid");
//                    printWriter.close();
                } catch (IOException e) {
                    e.printStackTrace();
                    System.out.println("Server print writer creation error");
                }

                int len;
                byte[] buffer = new byte[2048];

                try {
                    // 7 for ignoring the UPLOAD part
                    FileOutputStream fileOutputStream = new FileOutputStream(new File(
                            uploadPath + "\\" + httpRequestInput.substring(7)));
                    InputStream in = threadedSocket.getInputStream();

                    while((len = in.read(buffer))>0){
                        fileOutputStream.write(buffer, 0, len);
                    }

                    in.close();
                    fileOutputStream.close();
                } catch (FileNotFoundException e) {
                    System.out.println("Uploaded file receive stream error");
                    e.printStackTrace();

                } catch (IOException e) {
                    System.out.println("Input stream creation error");
                    e.printStackTrace();

                }

            } else{
                try {
                    printWriter = new PrintWriter(threadedSocket.getOutputStream());
                    printWriter.write("invalid" + "\r\n");
                    printWriter.flush();
                    System.out.println(">>> Given file name is invalid");
                    printWriter.close();
                    bufferedReader.close();

                } catch (IOException e) {
                    System.out.println("Server print writer creation error");
                    e.printStackTrace();

                }
            }
        }
        else
        {
            System.out.println(">>> Given fileName is Invalid !");
        }

        try {
            bufferedReader.close();
            threadedSocket.close();
        } catch (IOException e) {
            System.out.println("Buffered reader or socket closing error");
            e.printStackTrace();
        }
        return;
    }
}