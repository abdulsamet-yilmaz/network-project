package ReservationServer;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.net.Socket;
import java.util.HashMap;
import java.util.Objects;
import java.util.UUID;

class SocketHandler implements Runnable{
    Socket client;

    public SocketHandler(Socket client) {
        this.client = client;
    }
    @Override
    public void run() {
        String[] daysOfWeek = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};
        try{
            BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream()));

            String line = reader.readLine();

            Request requestInfo = getRequestType(line);

            switch (requestInfo.name){
                case RequestType.reserve:
                    ValidateReserveRequest(requestInfo.parameters);

                    String activity = requestInfo.parameters.get("activity");
                    if (!CheckActivityExist(activity)){
                        Send404Message(client,"Activity with name "+activity+" is not found");
                        return;
                    }
                    if(MakeReservation(requestInfo.parameters)){
                        Send200Message(client,"Room " + requestInfo.parameters.get("room")
                        + " is successfully reserved on " + daysOfWeek[Integer.parseInt(requestInfo.parameters.get("day"))-1] + " "
                                + requestInfo.parameters.get("hour") + ":00-" + (Integer.parseInt(requestInfo.parameters.get("hour")) +
                                Integer.parseInt(requestInfo.parameters.get("duration"))) + ":00\n" +
                                "Your reservation ID is " + requestInfo.parameters.get("id"));
                    }else{
                        Send403Message(client);
                    }
                    break;
                case RequestType.listavailabilityWithDay:
                    ListAvailableHoursWithDay(client,requestInfo.parameters.get("room"), requestInfo.parameters.get("day"));
                    break;
                case RequestType.listavailability:
                    ListAvailableHours(client,requestInfo.parameters.get("room"));
                    break;
                case RequestType.display:
                    ReservationInfo reservationInfo=reservationExistInDB(requestInfo.parameters.get("id"));
                    if (reservationInfo==null){
                        Send404Message(client,"Reservation with ID "+requestInfo.parameters.get("id")+" is not found");
                        return;
                    }
                    Send200MessageWithReservationInfo(client,reservationInfo);
                    break;
            }
        }catch (Exception exception){
            // TODO: send 400 Bad Request Message
            System.out.println(exception.getMessage());
            try {
                Send400Message(client,exception.getMessage());
            } catch (IOException e) {
                System.out.println("400 Bad Request Message Failed");
            }
        }
    }

    void ListAvailableHoursWithDay(Socket client,String roomName,String day) throws IOException {
        Socket clientSocket = new Socket("localhost",8080);
        PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
        BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

        out.println("GET /checkavailability?name="+roomName+"&day="+day+" HTTP/1.1");

        String resp = in.readLine();
        String secondLine = in.readLine();

        in.close();
        out.close();
        clientSocket.close();

        String statusCode = GetStatusCode(resp);//400, 404, 200

        if (statusCode.equals("200")){
            Send200Message(client,secondLine);
        }else if(statusCode.equals("400")){
            Send400Message(client,secondLine);
        }else if(statusCode.equals("404")){
            Send404Message(client,secondLine);
        }
    }

    void ListAvailableHours(Socket client,String roomName) throws IOException {
        StringBuilder availableHours = new StringBuilder();
        for(int i = 1; i <= 7; i++){
            Socket clientSocket = new Socket("localhost",8080);
            PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

            out.println("GET /checkavailability?name="+roomName+"&day="+i+" HTTP/1.1");

            String resp = in.readLine();
            //in.readLine();
            String secondLine = in.readLine();

            String statusCode = GetStatusCode(resp);//400, 404, 200

            if (statusCode.equals("200")){
                availableHours.append("<h1>Day-").append(i).append("</h1>\n");
                availableHours.append(secondLine).append("\n");
            }else if(statusCode.equals("400")){
                Send400Message(client,secondLine);
                break;
            }else if(statusCode.equals("404")){
                Send404Message(client,secondLine);
                break;
            }
            in.close();
            out.close();
            clientSocket.close();
        }
        Send200Message(client, availableHours.toString());
    }

    boolean MakeReservation(HashMap<String,String> parameters) throws IOException {
        Socket clientSocket = new Socket("localhost",8080);
        PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
        BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

        UUID uuid = UUID.randomUUID();


        String name,day,hour,duration;
        name = parameters.get("room");
        day = parameters.get("day");
        hour = parameters.get("hour");
        duration = parameters.get("duration");

        out.println("GET /reserve?name="+name+"&day="+day+"&hour="+hour+"&duration="+duration+" HTTP/1.1");

        String resp = in.readLine();

        in.close();
        out.close();
        clientSocket.close();

        String statusCode = GetStatusCode(resp);

        if(Objects.equals(statusCode, "200")){
            ReservationInfo reservationInfo = new ReservationInfo(uuid.toString(),name,day,hour,duration);
            addReservationToDB(reservationInfo);
            return true;
        }

        return false;
    }

    boolean CheckActivityExist(String activityName) throws IOException {

        Socket clientSocket = new Socket("localhost",8082);
        PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
        BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

        out.println("GET /check?name="+activityName+" HTTP/1.1");

        String resp = in.readLine();

        in.close();
        out.close();
        clientSocket.close();

        String statusCode = GetStatusCode(resp);

        return Objects.equals(statusCode, "200");
    }

    void ValidateReserveRequest(HashMap<String,String> parameters) throws Exception {
        for (String expectedParameter : new String[]{"room","activity","day","hour","duration"}) {
            if(!parameters.containsKey(expectedParameter)){
                throw new Exception("should provide "+expectedParameter);
            }
        }
        int day = Integer.parseInt(parameters.get("day"));
        if(!(day >= 1 && day <= 7)){
            throw new Exception("day parameter should be between 1 and 7");
        }

        int hour = Integer.parseInt(parameters.get("hour"));
        int duration = Integer.parseInt(parameters.get("duration"));

        if( hour < 9){
            throw new Exception("rooms are closed before 9");
        }

        if(hour + duration > 18){
            throw new Exception("rooms are closed after 18");
        }
    }
    void addReservationToDB(ReservationInfo reservationInfo) throws IOException {

        ObjectMapper mapper = new ObjectMapper();

        String serializedObject = mapper.writeValueAsString(reservationInfo);

        synchronized (ReservationServer.RESERVATION_FILE){
            BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(ReservationServer.RESERVATION_FILE,true));
            bufferedWriter.write(serializedObject);
            bufferedWriter.write("\n");
            bufferedWriter.close();
        }
    }
    ReservationInfo reservationExistInDB(String reservationId){
        ObjectMapper mapper = new ObjectMapper();

        try {
            synchronized (ReservationServer.RESERVATION_FILE){
                BufferedReader reader = new BufferedReader(new FileReader(ReservationServer.RESERVATION_FILE));

                String line;

                while ((line = reader.readLine()) != null) {
                    ReservationInfo reservationInfo = mapper.readValue(line, ReservationInfo.class);
                    if (Objects.equals(reservationInfo.id, reservationId)){
                        reader.close();
                        return reservationInfo;
                    }
                }
                reader.close();
            }

            return null;

        } catch (IOException e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
        }
        return null;
    }

    Request getRequestType(String line){
        String[] firstLine = line.split(" ");

        String[] endpoint = firstLine[1].split("/");

        String parametersString = endpoint[1];
        String[] requestInfo = parametersString.replace("?", ":").split(":");

        String requestName = requestInfo[0];

        String[] parameters = requestInfo[1].split("&");
        if (requestName.equals(RequestType.listavailability) && parameters.length > 1){
            requestName = RequestType.listavailabilityWithDay;
        }

        return new Request(requestName, new HashMap<>() {
            {
                for (String parameter : parameters) {
                    String[] key_value = parameter.split("=");
                    put(key_value[0],key_value[1]);
                }
            }
        });
    }

    void Send400Message(Socket client,String message) throws IOException {
        PrintWriter toClient = new PrintWriter(client.getOutputStream());
        toClient.println("HTTP/1.1 400 Bad Request\r\n");
        toClient.print("<BODY>Error: Inputs are not valid.</BODY>");
        toClient.flush();
        toClient.close();
        client.close();
    }

    void Send403Message(Socket client) throws IOException {
        PrintWriter toClient = new PrintWriter(client.getOutputStream());

        toClient.println("HTTP/1.1 403 Forbidden\r\n");
        toClient.print("<BODY>The room is not available on that date.</BODY>");
        toClient.flush();
        toClient.close();
        client.close();
    }

    void Send404Message(Socket client,String message) throws IOException {
        PrintWriter toClient = new PrintWriter(client.getOutputStream());
        toClient.println("HTTP/1.1 404 Not Found\r\n");
        toClient.print("<body>Error: "+message+" </body>");
        toClient.flush();
        toClient.close();
        client.close();
    }

    void Send200Message(Socket client,String message) throws IOException {
        PrintWriter toClient = new PrintWriter(client.getOutputStream());

        toClient.println("HTTP/1.1 200 OK\r\n");
        toClient.print("<HTML>\n<HEAD>\n<TITLE>Reservation Successful</TITLE>\n</HEAD>\n<BODY>"+message+"</BODY>\n</HTML>");
        toClient.flush();
        toClient.close();
        client.close();
    }
    void Send200MessageWithReservationInfo(Socket client,ReservationInfo reservationInfo) throws IOException {
        PrintWriter toClient = new PrintWriter(client.getOutputStream());

        toClient.println("HTTP/1.1 200 OK\r\n");
        toClient.println("<HTML> <HEAD> <TITLE>Reservation Info</TITLE> </HEAD>");
        toClient.print("<BODY> "+reservationInfo.toString() + " </BODY>\n</HTML>");
        toClient.flush();
        toClient.close();
        client.close();
    }


    String GetStatusCode(String line){
       return line.split(" ")[1];
    }
}


class Request{
    String name;
    HashMap<String,String>parameters;

    public Request(String name, HashMap<String, String> parameters) {
        this.name = name;
        this.parameters = parameters;
    }
}
class ReservationInfo{
    String id;
    String name;
    String day;
    String hour;
    String duration;
    String[] daysOfWeek = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};

    @Override
    public String toString() {
        return  "Reservation ID: " + id + " <BR>" +
                "\n Room: " + name + " <BR>" +
                "\n When: " + daysOfWeek[Integer.parseInt(day)-1] + " " + hour + ":00-" + (Integer.parseInt(hour) +
                Integer.parseInt(duration)) + ":00";
    }



    public ReservationInfo() {
    }

    public ReservationInfo(String id, String name, String day, String hour, String duration) {
        this.id = id;
        this.name = name;
        this.day = day;
        this.hour = hour;
        this.duration = duration;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDay() {
        return day;
    }

    public void setDay(String day) {
        this.day = day;
    }

    public String getHour() {
        return hour;
    }

    public void setHour(String hour) {
        this.hour = hour;
    }

    public String getDuration() {
        return duration;
    }

    public void setDuration(String duration) {
        this.duration = duration;
    }
}

