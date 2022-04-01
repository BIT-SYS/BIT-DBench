import java.util.*;
import java.util.function.*;
import java.util.regex.*;
import java.util.stream.*;
import java.nio.file.*;
import java.io.*;
import aoc.util.*;

public class Day05 {
    private static Stream<Coord> parseMatch(Matcher m, boolean part2) {
        Stream.Builder<Coord> c = Stream.builder();
        int x1 = Integer.parseInt(m.group(1));
        int y1 = Integer.parseInt(m.group(2));
        int x2 = Integer.parseInt(m.group(3));
        int y2 = Integer.parseInt(m.group(4));
        if (x1 == x2) {
            int begy = Integer.min(y1, y2);
            int endy = Integer.max(y1, y2);
            for (int y = begy; y <= endy; y++) {
                c.accept(new Coord(x1, y));
            }
        } else if (y1 == y2) {
            int begx = Integer.min(x1, x2);
            int endx = Integer.max(x1, x2);
            for (int x = begx; x <= endx; x++) {
                c.accept(new Coord(x, y1));
            }
        } else if (part2) {
            int deltax = x1 < x2 ? 1 : -1;
            int deltay = y1 < y2 ? 1 : -1;
            for (int x = x1, y = y1;
                 !(x == x2 + deltax && y == y2 + deltay);
                 x += deltax, y += deltay) {
                c.accept(new Coord(x, y));
            }
        }
        return c.build();
    }

    private static long solve(Stream<Coord> s) {
        return s.collect(Collectors.groupingBy(Function.identity(),
                                               Collectors.counting()))
            .values().stream()
            .filter(v -> v > 1)
            .count();
    }

    public static void main(String[] args) {
        try {
            var re = Pattern.compile("(\\d+),(\\d+) -> (\\d+),(\\d+)");
            var input = Files.lines(Path.of(args[0]))
                .map(line -> re.matcher(line))
                .filter(m -> m.matches()).toList();

            long intersections1 = solve(input.stream()
                                        .flatMap(m -> Day05.parseMatch(m, false)));
            System.out.println("Part 1: " + intersections1);

            long intersections2 = solve(input.stream()
                                        .flatMap(m -> Day05.parseMatch(m, true)));
            System.out.println("Part 2: " + intersections2);

        } catch (IOException e) {
            System.err.println(e);
            System.exit(1);
        }
    }
}
