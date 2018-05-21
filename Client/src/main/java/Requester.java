import akka.actor.AbstractActor;
import akka.actor.OneForOneStrategy;
import akka.actor.Props;
import akka.actor.SupervisorStrategy;
import akka.japi.pf.DeciderBuilder;
import bookstore.messaging.BankStoreAkka.Request;
import bookstore.messaging.BankStoreAkka.RequestType;
import bookstore.messaging.BankStoreAkka.Response;
import scala.concurrent.duration.Duration;

import static akka.actor.SupervisorStrategy.stop;

public class Requester extends AbstractActor {


    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(Response.class, res -> {
                    switch (res.getResponseStatus()) {
                        case OK:
                            if (!res.getBookTitle().isEmpty()) {
                                System.out.println(res.getBookTitle());
                            }
                            if (res.getPrice() > 0) {
                                System.out.println(res.getPrice());
                            } else {
                                System.out.println("Order completed");
                            }
                            break;
                        case NOT_FOUND:
                            System.out.println("Book not found");
                            break;
                        case ERROR:
                            System.out.println("Error");
                            break;
                        default:
                            System.out.println(res);
                    }
                })
                .match(String.class, s -> {
                    String[] cmd = s.split(" ");
                    switch (cmd[0]) {
                        case "stream":
                            getContext()
                                    .actorSelection("akka.tcp://book_store@127.0.0.1:2552/user/store")
                                    .tell(Request.newBuilder()
                                            .setBookTitle(cmd[1])
                                            .setRequestType(RequestType.STREAM_BOOK)
                                            .build(), context().actorOf(Props.create(StreamHandler.class)));
                            break;
                        case "order":
                            getContext()
                                    .actorSelection("akka.tcp://book_store@127.0.0.1:2552/user/store")
                                    .tell(Request.newBuilder()
                                            .setBookTitle(cmd[1])
                                            .setRequestType(RequestType.ORDER_BOOK)
                                            .build(), getSelf());
                            break;
                        case "find":
                            getContext()
                                    .actorSelection("akka.tcp://book_store@127.0.0.1:2552/user/store")
                                    .tell(Request.newBuilder()
                                            .setBookTitle(cmd[1])
                                            .setRequestType(RequestType.FIND_BOOK)
                                            .build(), getSelf());
                            break;
                    }

                })
                .matchAny(System.out::println)
                .build();

    }
    private static SupervisorStrategy strategy
            = new OneForOneStrategy(10, Duration.create("1 minute"), DeciderBuilder.
            matchAny(o -> stop()).
            build());

    @Override
    public SupervisorStrategy supervisorStrategy() {
        return strategy;
    }
}
