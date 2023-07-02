package ReservationServer;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;

class ReservationServer {
    static final File RESERVATION_FILE = new File("reservationServerDB.txt");
    public static void main(String[] args) throws IOException {

        var port = "8081";
        ServerSocket socket = new ServerSocket(Integer.parseInt(port));

        while (true) {
            new Thread(new SocketHandler(socket.accept())).start();
        }
    }

}