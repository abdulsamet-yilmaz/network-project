package RoomServer;

import java.io.*;
import java.net.ServerSocket;

class RoomServer {
    static final File ROOMS_FILE = new File("roomServerDB.txt");

    public static void main(String[] args) throws IOException {

        var port = "8080";
        ServerSocket socket = new ServerSocket(Integer.parseInt(port));

        while (true) {
            new Thread(new SocketHandler(socket.accept())).start();
        }
    }

}