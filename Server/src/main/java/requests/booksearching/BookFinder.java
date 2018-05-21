package requests.booksearching;

import akka.NotUsed;
import akka.actor.*;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.pf.DeciderBuilder;
import akka.japi.pf.PFBuilder;
import akka.japi.pf.ReceiveBuilder;
import akka.stream.*;
import akka.stream.impl.QueueSink;
import akka.stream.javadsl.*;
import akka.util.ByteString;
import bookstore.messaging.BankStoreAkka;
import scala.concurrent.duration.Duration;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Optional;

import static akka.actor.SupervisorStrategy.restart;
import static akka.actor.SupervisorStrategy.stop;


public class BookFinder extends AbstractActor {
    private static class DatabaseResearcher extends AbstractActor {
        private final String DATABASE1 = "databases/1";
        private final String DATABASE2 = "databases/2";

        private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);
        private int endStream = 0;
        private boolean found = false;

        private ActorRef requester;

        @Override
        public Receive createReceive() {
            return receiveBuilder()
                    .match(QueryResult.class, result -> {
                        if (result.getPrice().isPresent()) {
                            if (!found) {
                                found = true;
                                requester.tell(BankStoreAkka.Response.newBuilder()
                                        .setResponseStatus(BankStoreAkka.ResponseStatus.OK)
                                        .setPrice(result.getPrice().get())
                                        .build(), ActorRef.noSender()
                                );
                            }
                        } else {
                            endStream++;
                        }
                        if (endStream == 2) {
                            if (!found) {
                                requester.tell(BankStoreAkka.Response.newBuilder()
                                        .setResponseStatus(BankStoreAkka.ResponseStatus.NOT_FOUND)
                                        .build(), ActorRef.noSender()
                                );
                            }
                            context().stop(getSelf());
                        }
                    })
                    .match(String.class, title -> {
                        requester = getSender();

                        createStreams(title);

                    })
                    .matchAny(o -> log.warning("Unknown message"))
                    .build();
        }

        private void createStreams(String title) {
            ActorMaterializer actorMaterializer = ActorMaterializer.create(context());

            Flow<Path, QueryResult, NotUsed> processFile = Flow.of(Path.class)
                    .map(FileIO::fromPath)
                    .flatMapConcat(source ->
                            source.via(Framing.delimiter(ByteString.fromString("\n"), 256, FramingTruncation.ALLOW))
                                    .map(ByteString::utf8String)
                                    .filter(line -> line.split(";")[0].equals(title))
                                    .map(line -> new QueryResult(Optional.of(Float.parseFloat(line.split(";")[1]))))
                    );

            Arrays.asList(DATABASE1, DATABASE2).forEach(database ->
                    Source.single(database)
                            .map(path -> Paths.get(path))
                            .filter(path -> path.toFile().exists() && path.toFile().canRead())
                            .via(processFile)
                            .take(1)
                            .runWith(Sink.actorRef(getSelf(), new QueryResult(Optional.empty())), actorMaterializer)
            );
        }
    }


    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(String.class, title -> {
                    context().actorOf(Props.create(DatabaseResearcher.class)).tell(title, getSender());
                })
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
