import java.util.*;
import java.nio.file.*;
import java.io.*;

public class Day10 {
    enum Token {
        PAREN, BRACKET, BRACE, ANGLE
    }

    private String program;
    private Optional<Token> mismatch;
    private Deque<Token> tokens;

    public Day10(String line) {
        program = line;
        tokens = new ArrayDeque<Token>(program.length() / 2);
        mismatch = checkCorrupt();
    }

    public boolean isValid() {
        return mismatch.isEmpty();
    }

    private Optional<Token> checkCorrupt() {
        for (char c : program.toCharArray()) {
            switch (c) {
            case '(':
                tokens.addFirst(Token.PAREN);
                break;
            case '[':
                tokens.addFirst(Token.BRACKET);
                break;
            case '{':
                tokens.addFirst(Token.BRACE);
                break;
            case '<':
                tokens.addFirst(Token.ANGLE);
                break;
            case ')':
                if (tokens.removeFirst() != Token.PAREN) {
                    return Optional.of(Token.PAREN);
                }
                break;
            case ']':
                if (tokens.removeFirst() != Token.BRACKET) {
                    return Optional.of(Token.BRACKET);
                }
                break;
            case '}':
                if (tokens.removeFirst() != Token.BRACE) {
                    return Optional.of(Token.BRACE);
                }
                break;
            case '>':
                if (tokens.removeFirst() != Token.ANGLE) {
                    return Optional.of(Token.ANGLE);
                }
                break;
            default:
                throw new IllegalStateException("Invalid character: " + c);
            }
        }
        return Optional.empty();
    }

    public int score1() {
        if (mismatch.isEmpty()) {
            return 0;
        } else {
            return switch (mismatch.get()) {
            case PAREN -> 3;
            case BRACKET -> 57;
            case BRACE -> 1197;
            case ANGLE -> 25137;
            };
        }
    }

    public long score2() {
        long score = 0;
        for (var c : tokens) {
            score *= 5;
            score +=
                switch (c) {
                case PAREN -> 1;
                case BRACKET -> 2;
                case BRACE -> 3;
                case ANGLE -> 4;
                };
        }
        return score;
    }

    public static void main(String[] args) {
        try {
            var input = Files.lines(Path.of(args[0]))
                .map(Day10::new)
                .toList();

            int part1 = input.stream()
                .mapToInt(Day10::score1)
                .sum();
            System.out.println("Part 1: " + part1);

            var part2 = input.stream()
                .filter(Day10::isValid)
                .mapToLong(Day10::score2)
                .sorted()
                .toArray();
            System.out.println("Part 2: " + part2[part2.length / 2]);
        } catch (IOException e) {
            System.err.println(e);
            System.exit(1);
        }
    }
}
