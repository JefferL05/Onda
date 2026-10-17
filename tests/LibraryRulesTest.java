import app.onda.mobile.LibraryQuery;
import app.onda.mobile.QueueNavigator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class LibraryRulesTest {
    private static int checks;
    private static void check(boolean ok, String message) {
        checks++;
        if (!ok) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        check(LibraryQuery.matches("João — Coração", " JOAO "), "accent-insensitive search");
        check(LibraryQuery.matches("Águas de Março", "marco"), "cedilla search");
        check(LibraryQuery.matches("Jazz", ""), "empty filter");
        check(!LibraryQuery.matches("Rock", "Jazz"), "non-matching filter");
        List<String> original = Arrays.asList("mpb", "João", "jazz");
        List<String> searches = LibraryQuery.rememberSearch(original, " JOAO ");
        check(searches.equals(Arrays.asList("JOAO", "mpb", "jazz")), "deduplicate and promote search");
        check(original.get(0).equals("mpb"), "input is not mutated");
        check(LibraryQuery.rememberSearch(original, "   ").equals(original), "empty search does not overwrite");
        List<String> many = new ArrayList<>();
        for (int i = 0; i < 20; i++) many = LibraryQuery.rememberSearch(many, "query " + i);
        check(many.size() == 12 && many.get(11).equals("query 8"), "recent search retention");
        List<String> queue = Arrays.asList("a", "b", "c");
        check(QueueNavigator.adjacent(queue, "a", 1) == 1, "next item");
        check(QueueNavigator.adjacent(queue, "b", -1) == 0, "previous item");
        check(QueueNavigator.adjacent(queue, "a", -1) == -1, "no previous at start");
        check(QueueNavigator.adjacent(queue, "c", 1) == -1, "no implicit wrap");
        check(QueueNavigator.adjacent(queue, null, 1) == 0, "start queue when no active item");
        check(QueueNavigator.adjacent(queue, "external", -1) == -1, "external item has no previous");
        check(QueueNavigator.adjacent(new ArrayList<>(), "a", 1) == -1, "empty queue");
        check(QueueNavigator.adjacent(Arrays.asList("a"), "a", 1) == -1, "single-item queue");
        System.out.println("PASS: " + checks + " library/queue checks");
    }
}
