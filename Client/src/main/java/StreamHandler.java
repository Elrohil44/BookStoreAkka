import akka.actor.AbstractActor;
import bookstore.messaging.BankStoreAkka;

public class StreamHandler extends AbstractActor{
    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(BankStoreAkka.Response.class, res -> {
                    switch (res.getResponseStatus()) {
                        case OK:
                            System.out.println(res.getLine());
                            break;
                        case EOF:
                            System.out.println("EOF");
                            break;
                        case ERROR:
                            System.out.println("Error");
                            break;
                    }
                })
                .build();
    }
}
