package requests.booksearching;

import java.util.Optional;

public class QueryResult {
    private final Optional<Float> price;

    public QueryResult(Optional<Float> price) {
        this.price = price;
    }

    public Optional<Float> getPrice() {
        return price;
    }
}
