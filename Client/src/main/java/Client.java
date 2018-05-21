import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class Client {
    public static void main(String[] args) throws IOException {
        final ActorSystem system = ActorSystem.create("client");
        final ActorRef test = system.actorOf(Props.create(Requester.class), "requester");

        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        while (true) {
            String line = br.readLine();
            if (line.equals("q")) {
                break;
            }
            test.tell(line, ActorRef.noSender());
        }

        system.terminate();
    }
}
