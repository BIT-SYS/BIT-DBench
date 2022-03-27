import java.nio.file.*;
import java.io.*;

class Day02 {
    private static int depth1 = 0;
    private static int depth2 = 0;
    private static int horizontal = 0;
    private static int aim = 0;

    private static void parse(String line) {
        if (line.startsWith("forward")) {
            int dist = Integer.parseInt(line.substring(8));
            horizontal += dist;
            depth2 += aim * dist;
        } else if (line.startsWith("down")) {
            int dist = Integer.parseInt(line.substring(5));
            depth1 += dist;
            aim += dist;
        } else if (line.startsWith("up")) {
            int dist = Integer.parseInt(line.substring(3));
            depth1 -= dist;
            aim -= dist;
        }
    }

    public static void main(String[] args) {
        try {
            Files.lines(Path.of(args[0])).forEachOrdered(Day02::parse);
            System.out.println("Part 1: " + (depth1 * horizontal));
            System.out.println("Part 2: " + (depth2 * horizontal));
        } catch (IOException e) {
            System.err.println(e);
        }
    }
}
