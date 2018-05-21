package requests.bookstreaming;

import akka.Done;
import akka.actor.*;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.pf.DeciderBuilder;
import akka.japi.pf.ReceiveBuilder;
import akka.stream.ActorMaterializer;
import akka.stream.IOResult;
import akka.stream.ThrottleMode;
import akka.stream.javadsl.FileIO;
import akka.stream.javadsl.Framing;
import akka.stream.javadsl.FramingTruncation;
import akka.stream.javadsl.Sink;
import akka.util.ByteString;
import bookstore.messaging.BankStoreAkka;

import java.io.File;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

import static akka.actor.SupervisorStrategy.stop;

public class BookStreamer extends AbstractActor {
    private static final String PATH = "books/";


    private static class Streamer extends AbstractActor {
        private ActorRef requester;
        private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);


        @Override
        public Receive createReceive() {
            return receiveBuilder()
                    .match(BankStoreAkka.Response.class, response -> {
                        requester.tell(response, ActorRef.noSender());
                        if (response.getResponseStatus() == BankStoreAkka.ResponseStatus.EOF) {
                            context().stop(getSelf());
                        }
                    })
                    .match(String.class, s -> {
                        requester = getSender();
                        log.info("Starting streaming");
                        ActorMaterializer materializer = ActorMaterializer.create(context());

                        File file = new File(PATH + s);

                        if (!file.exists() || !file.canRead()) {
                            getSender().tell(BankStoreAkka.Response
                                    .newBuilder()
                                    .setResponseStatus(BankStoreAkka.ResponseStatus.ERROR)
                                    .build(), ActorRef.noSender());
                        } else {
                            FileIO.fromPath(Paths.get(PATH + s))
                                    .via(Framing.delimiter(ByteString.fromString("\n"), 256, FramingTruncation.ALLOW))
                                    .map(line -> BankStoreAkka.Response
                                            .newBuilder()
                                            .setResponseStatus(BankStoreAkka.ResponseStatus.OK)
                                            .setLine(line.utf8String())
                                            .build())
                                    .throttle(1, Duration.ofSeconds(1), 1, ThrottleMode.shaping())
                                    .to(Sink.actorRef(getSender(), BankStoreAkka.Response
                                            .newBuilder()
                                            .setResponseStatus(BankStoreAkka.ResponseStatus.EOF)
                                            .build()))
                                    .run(materializer);
                        }
                    })
                    .build();
        }
    }


    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(String.class, s -> {
                    context().actorOf(Props.create(Streamer.class)).tell(s, getSender());
                })
                .build();
    }

    private static SupervisorStrategy strategy
            = new OneForOneStrategy(10, scala.concurrent.duration.Duration.create("1 minute"), DeciderBuilder.
            matchAny(o -> stop()).
            build());

    @Override
    public SupervisorStrategy supervisorStrategy() {
        return strategy;
    }
}
