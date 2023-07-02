package ActivityServer;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.stream.Collectors;

class SocketHandler implements Runnable{
    Socket client;

    public SocketHandler(Socket client) {
        this.client = client;
    }
    @Override
    public void run() {
        try{
            // Read a line of input from the client socket
            BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream()));

            String line = reader.readLine();

            // Determine the type of request made using the getRequestType() method
            switch (getRequestType(line)){
                case RequestType.add -> {
                    // Extract the name of the activity from the request
                    String activityName = getNameOfActivity(line);
                    if(!activityExistInDB(activityName)){  // Check if the activity already exists in the database
                        addActivityToDB(activityName); // If the activity does not exist, add it to the database
                        Send200Message(client,activityName+" is added successfully");
                    }else{
                        // If the activity already exists, send a 403 response message to the client
                        System.out.println("Already added.");
                        Send403Message(client,"Activity with name "+activityName+" is already added");
                    }
                }
                case RequestType.remove -> {
                    String activityName = getNameOfActivity(line);
                    if(!activityExistInDB(activityName)){
                        Send403Message(client,"Activity with name "+activityName+" is not found");
                    }else{
                        removeActivityFromDB(activityName);
                        // If the activity exists, remove it from the database.
                        Send200Message(client,activityName+" is removed successfully");
                    }
                }
                case RequestType.check -> {
                    String activityName = getNameOfActivity(line);
                    if(!activityExistInDB(activityName)){
                        Send404Message(client, activityName);
                    }else{
                        // If the activity exists, send a 200 response message to the client indicating that the activity exists
                        Send200Message(client,activityName+" exists");
                    }
                }


            }
        }catch (Exception exception){
            System.out.println(exception.getMessage());
        }
    }

    // Method to add an activity to the database
    void addActivityToDB(String activityName) throws IOException {

        // Synchronize access to the database file to avoid race conditions
        synchronized (ActivityServer.ACTIVITY_FILE){
            BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(ActivityServer.ACTIVITY_FILE,true));
            bufferedWriter.write(activityName);
            bufferedWriter.write("\n");
            bufferedWriter.close();
        }
    }

    boolean activityExistInDB(String activityName){
        try {
            synchronized (ActivityServer.ACTIVITY_FILE){
                BufferedReader reader = new BufferedReader(new FileReader(ActivityServer.ACTIVITY_FILE));

                String line;

                while ((line = reader.readLine()) != null) {
                    if (line.equalsIgnoreCase(activityName)){
                        reader.close();
                        return true;
                    }
                }
                reader.close();
            }
            return false;

        } catch (IOException e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
        }
        return false;
    }
    void removeActivityFromDB(String activityName) throws IOException {
        synchronized (ActivityServer.ACTIVITY_FILE){
            List<String> out = Files.lines(ActivityServer.ACTIVITY_FILE.toPath())
                    .filter(line -> !line.contains(activityName))
                    .collect(Collectors.toList());

            Files.write(ActivityServer.ACTIVITY_FILE.toPath(), out, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
        }
    }

    String getNameOfActivity(String line){
        String[] firstLine = line.split(" ");

        String[] endpoint = firstLine[1].split("/");

        String request = endpoint[1];

        String[] requestInfo = request.replace("?", ":").split(":");

        return requestInfo[1].split("&")[0].split("=")[1];
    }

    void Send404Message(Socket client,String activityName) throws IOException {
        PrintWriter toClient = new PrintWriter(client.getOutputStream());
        toClient.println("HTTP/1.1 404 Not Found\r\n");
        toClient.print("<body> Activity with name "+activityName+" is not found. </body>");
        toClient.flush();
        toClient.close();
        client.close();
    }

    // Not Used in ActivityServer
    void Send400Message(Socket client,String message) throws IOException {
        PrintWriter toClient = new PrintWriter(client.getOutputStream());
        toClient.println("HTTP/1.1 400 Bad Request\r\n");
        toClient.print("<body>Error: "+message+" </body>");
        toClient.flush();
        toClient.close();
        client.close();
    }

    void Send403Message(Socket client,String message) throws IOException {
        PrintWriter toClient = new PrintWriter(client.getOutputStream());

        toClient.println("HTTP/1.1 403 Forbidden\r\n");
        toClient.print("<body>"+message+"</body>");
        toClient.flush();
        toClient.close();
        client.close();
    }

    void Send200Message(Socket client,String message) throws IOException {
        PrintWriter toClient = new PrintWriter(client.getOutputStream());

        toClient.println("HTTP/1.1 200 OK\r\n");
        toClient.print("<body>"+message+"</body>");
        toClient.flush();
        toClient.close();
        client.close();
    }
    String getRequestType(String line){
        String[] firstLine = line.split(" ");

        String[] endpoint = firstLine[1].split("/");

        String request = endpoint[1];
        String[] requestInfo = request.replace("?", ":").split(":");
        return requestInfo[0];
    }

}
