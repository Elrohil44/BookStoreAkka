import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import requests.RequestHandler;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class BookStore {
    public static void main (String[] args) throws IOException {
        final ActorSystem system = ActorSystem.create("book_store");
        system.actorOf(Props.create(RequestHandler.class), "store");

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
