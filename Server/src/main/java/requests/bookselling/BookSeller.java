package requests.bookselling;

import akka.NotUsed;
import akka.actor.*;
import akka.japi.pf.DeciderBuilder;
import akka.japi.pf.ReceiveBuilder;
import akka.stream.ActorMaterializer;
import akka.stream.IOResult;
import akka.stream.javadsl.FileIO;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.util.ByteString;
import akka.util.ReentrantGuard;
import bookstore.messaging.BankStoreAkka;
import scala.concurrent.duration.Duration;

import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.HashSet;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.locks.ReentrantLock;

import static akka.actor.SupervisorStrategy.restart;
import static akka.actor.SupervisorStrategy.resume;
import static akka.actor.SupervisorStrategy.stop;

public class BookSeller extends AbstractActor {
    private static final Sink<ByteString, CompletionStage<IOResult>> sink = FileIO.toPath(Paths.get("databases/orders"),
            new HashSet<>(Arrays.asList(StandardOpenOption.APPEND, StandardOpenOption.SYNC, StandardOpenOption.CREATE)));


    private static class FileHandler extends AbstractActor {
        @Override
        public Receive createReceive() {
            return receiveBuilder()
                    .match(String.class, title -> {
                        ActorRef requester = getSender();
                        ActorMaterializer mat = ActorMaterializer.create(context());
                        Source<ByteString, NotUsed> source = Source.single(ByteString.fromString(title + "\n"));
                        CompletionStage<IOResult> completionStage = source.toMat(sink, Keep.right())
                                .run(mat);

                        completionStage
                                .thenApply(ioResult -> {
                                    requester.tell(BankStoreAkka.Response.newBuilder()
                                            .setResponseStatus(BankStoreAkka.ResponseStatus.OK)
                                            .build(), ActorRef.noSender());
                                    return ioResult;
                                })
                                .exceptionally(ex -> {
                                    requester.tell(BankStoreAkka.Response.newBuilder()
                                            .setResponseStatus(BankStoreAkka.ResponseStatus.ERROR)
                                            .build(), ActorRef.noSender());
                                    return IOResult.createFailed(0, ex);
                                });
                    })
                    .build();
        }
    }


    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(String.class, title -> {
                    context().child("file_handler").get().tell(title, getSender());
                })
                .build();
    }


    @Override
    public void preStart() {
        context().actorOf(Props.create(FileHandler.class), "file_handler");
    }

    private static SupervisorStrategy strategy
            = new OneForOneStrategy(10, Duration.create("1 minute"), DeciderBuilder.
            matchAny(o -> restart()).
            build());

    @Override
    public SupervisorStrategy supervisorStrategy() {
        return strategy;
    }
}
