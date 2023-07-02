package ActivityServer;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;

public class ActivityServer {
    static final File ACTIVITY_FILE = new File("activityServerDB.txt");

    public static void main(String[] args) throws IOException {

        var port = "8082";
        ServerSocket socket = new ServerSocket(Integer.parseInt(port));

        while (true) {
            new Thread(new SocketHandler(socket.accept())).start();
        }
    }

}