import akka.actor.ActorSystem;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class BookStore {
    public static void main (String[] args) throws IOException {
        final ActorSystem system = ActorSystem.create("book_store");

        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        while (true) {
            String line = br.readLine();
            if (line.equals("q")) {
                break;
            }
        }

        system.terminate();
    }
}
