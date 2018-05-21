package requests;

import akka.actor.*;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.pf.DeciderBuilder;
import bookstore.messaging.BankStoreAkka.*;
import requests.booksearching.BookFinder;
import requests.bookselling.BookSeller;
import requests.bookstreaming.BookStreamer;
import scala.concurrent.duration.Duration;
import static akka.actor.SupervisorStrategy.resume;

public class RequestHandler extends AbstractActor {
    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(Request.class, request -> {
                    log.info("Received request: " + request.getRequestType());
                    switch (request.getRequestType()) {
                        case FIND_BOOK:
                            context().child("book_finder").get().tell(request.getBookTitle(), getSender());
                            break;
                        case ORDER_BOOK:
                            context().child("book_seller").get().tell(request.getBookTitle(), getSender());
                            break;
                        case STREAM_BOOK:
                            context().child("book_streamer").get().tell(request.getBookTitle(), getSender());
                            break;
                        default:
                            log.warning("Received unknown request status");
                            getSender().tell(Response.newBuilder()
                                    .setResponseStatus(ResponseStatus.ERROR)
                                    .build(), getSelf());
                    }
                })
                .matchAny(o -> {
                    log.warning("Received unknown message");
                    getSender().tell(Response.newBuilder()
                            .setResponseStatus(ResponseStatus.ERROR)
                            .build(), getSelf());
                })
                .build();
    }

    @Override
    public void preStart() {
        context().actorOf(Props.create(BookSeller.class), "book_seller");
        context().actorOf(Props.create(BookStreamer.class), "book_streamer");
        context().actorOf(Props.create(BookFinder.class), "book_finder");
    }

    private static SupervisorStrategy strategy
            = new OneForOneStrategy(10, Duration.create("1 minute"), DeciderBuilder.
                    matchAny(o -> resume()).
                    build());

    @Override
    public SupervisorStrategy supervisorStrategy() {
        return strategy;
    }
}
