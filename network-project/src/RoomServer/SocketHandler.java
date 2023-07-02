package RoomServer;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

class SocketHandler implements Runnable{
    Socket client;
    private String[] daysOfWeek = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};;

    public SocketHandler(Socket client) {
        this.client = client;
    }
    @Override
    public void run() {
        try{
            BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream()));

            String line = reader.readLine();

            switch (getRequestType(line)){
                case RequestType.add -> {
                    String roomName = getNameOfRoom(line);
                    System.out.println("Add Room Request, "+"Room Name:"+roomName);
                    if(!RoomExistInDB(roomName)){
                        AddRoomToDB(roomName);
                        Send200Message(client,roomName+" is added successfully");
                        System.out.println(roomName+" is added successfully");
                    }else{
                        System.out.println("Error, "+roomName+" already added");
                        Send403Message(client,"Error, "+roomName+" already added");
                    }
                }
                case RequestType.remove -> {
                    String roomName = getNameOfRoom(line);
                    System.out.println("Remove Room Request, "+"Room Name:"+roomName);
                    if(!RoomExistInDB(roomName)){
                        System.out.println("Error, "+"Room with name "+roomName+" does not exist");
                        Send403Message(client,"Error, "+"Room with name "+roomName+" does not exist");
                    }else{
                        RemoveRoomFromDB(roomName);
                        Send200Message(client,roomName+" is removed successfully");
                        System.out.println(roomName+" is removed successfully");
                    }
                }
                case RequestType.checkavailability -> {
                    List<String> availabilityParameters = getCheckAvailabilityParameters(line);
                    String roomName = availabilityParameters.get(0);
                    int day = Integer.parseInt(availabilityParameters.get(1));
                    System.out.println("Check Availability Request, "+"Room Name:"+roomName+" Day:"+day);
                    if(!RoomExistInDB(roomName)){
                        System.out.println("Error, "+"Room with name "+roomName+" does not exist");
                        Send404Message(client,roomName);
                        return;
                    }
                    
                    List<Integer> availableHours = getAvailableHours(roomName, day);
                    SendAvaibleHours(client,availableHours, roomName, day);
                    System.out.println("Available hours in "+ daysOfWeek[day-1] +" are "+ availableHours.stream().map(Objects::toString).collect(Collectors.joining(", ")));

                }
                case RequestType.reserve -> {
                    String roomName = getNameOfRoom(line);
                    System.out.println("Reserve Request, Room Name:"+roomName);
                    if(!RoomExistInDB(roomName)){
                        System.out.println("Error, "+"Room with name "+roomName+" does not exist");
                        Send403Message(client,"Error, "+"Room with name "+roomName+" does not exist");
                        return;
                    }
                    List<String> reserveParameters = getReserveParameters(line);
                    MakeReservation(reserveParameters);
                    Send200Message(client,roomName+" is reserved successfully");
                    System.out.println(roomName+" is reserved successfully");


                }
            }
        }catch (Exception exception){
            System.out.println(exception.getMessage());
            try {
                Send400Message(client,exception.getMessage());
            } catch (IOException e) {
                System.out.println("An error occured. Inputs are not valid.");
            }
        }
    }

    boolean RoomExistInDB(String roomName){
        ObjectMapper mapper = new ObjectMapper();

        try {
            synchronized (RoomServer.ROOMS_FILE) {
            BufferedReader reader = new BufferedReader(new FileReader(RoomServer.ROOMS_FILE));

            String line;

            while ((line = reader.readLine()) != null) {
                RoomInfo roomInfo = mapper.readValue(line, RoomInfo.class);
                if (Objects.equals(roomInfo.name, roomName)) {
                    reader.close();
                    return true;
                }
            }
            reader.close();
            return false;
        }
        } catch (IOException e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
        }
        return false;
    }

    void AddRoomToDB(String roomName) throws IOException {
        ObjectMapper mapper = new ObjectMapper();

        RoomInfo roomInfo = new RoomInfo();
        roomInfo.name = roomName;
        ArrayList<Integer> hours = new ArrayList<>();

        for (var i = 9; i <= 17;i++){
            hours.add(i);
        }

        for (var i = 1; i <= 7;i++){
            roomInfo.dayHoursMap.put(i,hours);
        }

        String serializedObject = mapper.writeValueAsString(roomInfo);

        synchronized (RoomServer.ROOMS_FILE){
            BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(RoomServer.ROOMS_FILE,true));
            bufferedWriter.write(serializedObject);
            bufferedWriter.write("\n");
            bufferedWriter.close();
        }
    }

    void AddRoomToDB(RoomInfo roomInfo) throws IOException {
        ObjectMapper mapper = new ObjectMapper();

        String serializedObject = mapper.writeValueAsString(roomInfo);

        synchronized (RoomServer.ROOMS_FILE){
            BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(RoomServer.ROOMS_FILE,true));
            bufferedWriter.write(serializedObject);
            bufferedWriter.write("\n");
            bufferedWriter.close();
        }
    }

    void RemoveRoomFromDB(String roomName) throws IOException {
        synchronized (RoomServer.ROOMS_FILE){
            List<String> out = Files.lines(RoomServer.ROOMS_FILE.toPath())
                    .filter(line -> !line.contains("\""+roomName+"\""))
                    .collect(Collectors.toList());

            Files.write(RoomServer.ROOMS_FILE.toPath(), out, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
        }
    }

    void Send404Message(Socket client,String roomName) throws IOException {
        PrintWriter toClient = new PrintWriter(client.getOutputStream());
        toClient.println("HTTP/1.1 404 Not Found\r\n");
        toClient.print("<BODY> Room with name "+roomName+" is not found. </BODY>");
        toClient.flush();
        toClient.close();
        client.close();
    }

    void Send400Message(Socket client,String message) throws IOException {
        PrintWriter toClient = new PrintWriter(client.getOutputStream());
        toClient.println("HTTP/1.1 400 Bad Request\r\n");
        toClient.print("<BODY>Error: "+message+" </BODY>");
        toClient.flush();
        toClient.close();
        client.close();
    }

    void Send403Message(Socket client,String message) throws IOException {
        PrintWriter toClient = new PrintWriter(client.getOutputStream());

        toClient.println("HTTP/1.1 403 Forbidden\r\n");
        toClient.print("<BODY>"+message+"</BODY>");
        toClient.flush();
        toClient.close();
        client.close();
    }

    void Send200Message(Socket client,String message) throws IOException {
        PrintWriter toClient = new PrintWriter(client.getOutputStream());

        toClient.println("HTTP/1.1 200 OK\r\n");
        toClient.print("<BODY>"+message+"</BODY>");
        toClient.flush();
        toClient.close();
        client.close();
    }

    void SendAvaibleHours(Socket client, List<Integer> availableHours, String roomName, int day) throws IOException {
        PrintWriter toClient = new PrintWriter(client.getOutputStream());

        String message = "Available Hours On " + daysOfWeek[day-1] + ", Room " + roomName + "<ul>";
        for (Integer availableHour : availableHours) {
            message += "<li>"+availableHour+"</li>";
        }

        message += "</ul>";
        toClient.print("HTTP/1.1 200 OK\r\n");
        toClient.print(message);
        toClient.flush();
        toClient.close();
        client.close();
    }

    //  This method parses the request string and returns the request type as an enumeration
    String getRequestType(String line){
        String[] firstLine = line.split(" ");

        String[] endpoint = firstLine[1].split("/");

        String request = endpoint[1];
        String[] requestInfo = request.replace("?", ":").split(":");
        return requestInfo[0];
    }

    List<String> getReserveParameters(String line) throws Exception{
        String[] firstLine = line.split(" ");

        String[] endpoint = firstLine[1].split("/");

        String request = endpoint[1];

        String[] requestInfo = request.replace("?", ":").split(":");

        String[] parameters = requestInfo[1].split("&");

        List<String> parameterValues = new ArrayList<>();

        String name = parameters[0].split("=")[1];
        parameterValues.add(name);

        String day = parameters[1].split("=")[1];
        int dayOfWeek = Integer.parseInt(day);
        if( dayOfWeek > 7 || dayOfWeek < 1 ){
            throw new Exception("Day parameter must be between 1 and 7");
        }
        parameterValues.add(day);

        String hour = parameters[2].split("=")[1];
        int hourValue = Integer.parseInt(hour);
        if( hourValue > 17 || hourValue < 9 ){
            throw new Exception("Hour parameter must be between 9 and 17");
        }
        parameterValues.add(hour);

        String duration = parameters[3].split("=")[1];

        parameterValues.add(duration);

        return parameterValues;
    }

    List<String> getCheckAvailabilityParameters(String line) throws Exception{
        String[] firstLine = line.split(" ");

        String[] endpoint = firstLine[1].split("/");

        String request = endpoint[1];

        String[] requestInfo = request.replace("?", ":").split(":");


        String[] parameters = requestInfo[1].split("&");

        List<String> parameterValues = new ArrayList<>();

        String name = parameters[0].split("=")[1];
        parameterValues.add(name);

        String day = parameters[1].split("=")[1];
        int dayOfWeek = Integer.parseInt(day);
        if( dayOfWeek > 7 || dayOfWeek < 1 ){
            throw new Exception("Day parameter must be between 1 and 7");
        }
        parameterValues.add(day);

        return parameterValues;
    }

    List<Integer> getAvailableHours(String roomName,int day){

        ArrayList<Integer> availableHours = new ArrayList<>();
        ObjectMapper mapper = new ObjectMapper();

        try {
            synchronized (RoomServer.ROOMS_FILE){
                BufferedReader reader = new BufferedReader(new FileReader(RoomServer.ROOMS_FILE));

                String line;

                while ((line = reader.readLine()) != null) {
                    RoomInfo roomInfo = mapper.readValue(line, RoomInfo.class);
                    if (roomInfo.name.equals(roomName)){
                        reader.close();
                        return roomInfo.dayHoursMap.get(day);
                    }
                }
            }

        } catch (IOException e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
        }

        return availableHours;
    }

    public void MakeReservation(List<String> reservationParameters) throws Exception {
        String roomName = reservationParameters.get(0);
        Integer day = Integer.parseInt(reservationParameters.get(1));
        int hour = Integer.parseInt(reservationParameters.get(2));
        int duration = Integer.parseInt(reservationParameters.get(3));

        ObjectMapper mapper = new ObjectMapper();

        RoomInfo roomInfo = null;

        synchronized (RoomServer.ROOMS_FILE){
            BufferedReader reader = new BufferedReader(new FileReader(RoomServer.ROOMS_FILE));

            String line;

            while ((line = reader.readLine()) != null) {
                RoomInfo info = mapper.readValue(line, RoomInfo.class);
                if (info.name.equals(roomName)){
                    roomInfo = info;
                    break;
                }
            }

            List<Integer> availableHours = roomInfo.dayHoursMap.get(day);

            for (int i = 0; i < duration; i++){
                if (!availableHours.contains(hour+i)){
                    throw new Exception("Room is not available for this range!",new Throwable("Bad Request"));
                }
                availableHours.remove(Integer.valueOf(hour+i));
            }
            reader.close();
        }
        RemoveRoomFromDB(roomName);

        AddRoomToDB(roomInfo);
    }

    String getNameOfRoom(String line){
        String[] firstLine = line.split(" ");

        String[] endpoint = firstLine[1].split("/");

        String request = endpoint[1];

        String[] requestInfo = request.replace("?", ":").split(":");

        return requestInfo[1].split("&")[0].split("=")[1];
    }

    public RoomInfo getRoomInfo(String line){
        // line --> m1z6 1:9,10,11,12,13,14,15,16,17 2:9,10,11,12,13,14,15,16,17
        RoomInfo roomInfo = new RoomInfo();

        String[] strings = line.split(" ");

        roomInfo.name = strings[0];

        for(int i = 1; i <= 7;i++){
            String[] availabilityInfo = strings[i].split(":");

            List<Integer> availableHours = getAvailableHours(roomInfo.name, i);
            roomInfo.dayHoursMap.put(i,availableHours);
        }

        return roomInfo;
    }
}

class RoomInfo{
    String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    HashMap<Integer,List<Integer>> dayHoursMap = new HashMap<>();

    public HashMap<Integer, List<Integer>> getDayHoursMap() {
        return dayHoursMap;
    }

    public void setDayHoursMap(HashMap<Integer, List<Integer>> dayHoursMap) {
        this.dayHoursMap = dayHoursMap;
    }
}