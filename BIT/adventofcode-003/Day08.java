import java.util.*;
import java.util.regex.*;
import java.util.stream.*;
import java.nio.file.*;
import java.io.*;
import aoc.util.*;

public class Day08 {
    private static class Entry {
        private String[] signals;
        private String[] digits;
        public Entry(String line) {
            String[] halves = line.split("\\s+\\|\\s+");
            signals = halves[0].split("\\s+");
            Arrays.sort(signals, (a, b) -> Integer.compare(a.length(), b.length()));
            digits = halves[1].split("\\s+");
        }

        public String[] getSignals() { return signals; }
        public String[] getDigits() { return digits; }

        private static Map<Integer, Integer> displays =
            Map.of(0b1110111, 0, 0b0010010, 1, 0b1011101, 2, 0b1011011, 3, 0b0111010, 4,
                   0b1101011, 5, 0b1101111, 6, 0b1010010, 7, 0b1111111, 8, 0b1111011, 9);
        private static Map<Character, Integer> charTable =
            Map.of('a', 0, 'b', 1, 'c', 2, 'd', 3, 'e', 4, 'f', 5, 'g', 6);

        private int charToIdx(char c) {
            return charTable.get(c);
        }

        private int segmentsToInt(int[] offsets, String d) {
            int num = 0;
            for (char c : d.toCharArray()) {
                num |= 1 << (6 - offsets[charTable.get(c)]);
            }
            return num;
        }

        private boolean tryToSetOffset(int[] offsets, char c, int val) {
            int i = charTable.get(c);
            for (int n = 0; n < offsets.length; n++) {
                if (offsets[n] == val && n != i) {
                    return false;
                }
            }
            if (offsets[i] == -1 || offsets[i] == val) {
                offsets[i] = val;
                return true;
            } else {
                return false;
            }
        }

        private boolean fillOffsets(int[] offsets, int sig) {
            if (sig < signals.length) {
                for (String perm_s : StringPermutations.of(signals[sig])) {
                    char[] perm = perm_s.toCharArray();
                    int[] newOffsets = Arrays.copyOf(offsets, offsets.length);
                    switch (perm.length) {
                    case 2:
                        // 1
                        if (displays.getOrDefault(segmentsToInt(offsets, perm_s),
                                                  -1) == 1 &&
                            fillOffsets(offsets, sig + 1)) {
                            return true;
                        } else if (tryToSetOffset(newOffsets, perm[0], 2) &&
                                   tryToSetOffset(newOffsets, perm[1], 5) &&
                                   fillOffsets(newOffsets, sig + 1)) {
                            System.arraycopy(newOffsets, 0, offsets, 0,
                                             newOffsets.length);
                            return true;
                        }
                        break;

                    case 3:
                        // 7
                        if (displays.getOrDefault(segmentsToInt(offsets, perm_s), -1)
                            == 7 && fillOffsets(offsets, sig + 1)) {
                            return true;
                        } else if (tryToSetOffset(newOffsets, perm[0], 0) &&
                                   tryToSetOffset(newOffsets, perm[1], 2) &&
                                   tryToSetOffset(newOffsets, perm[2], 5) &&
                                   fillOffsets(newOffsets, sig + 1)) {
                            System.arraycopy(newOffsets, 0, offsets, 0,
                                             newOffsets.length);
                            return true;
                        }
                        break;

                    case 4:
                        // 4
                        if (displays.getOrDefault(segmentsToInt(offsets, perm_s), -1)
                            == 4 && fillOffsets(offsets, sig + 1)) {
                            return true;
                        } else if (tryToSetOffset(newOffsets, perm[0], 1) &&
                                   tryToSetOffset(newOffsets, perm[1], 2) &&
                                   tryToSetOffset(newOffsets, perm[2], 3) &&
                                   tryToSetOffset(newOffsets, perm[3], 5) &&
                                   fillOffsets(newOffsets, sig + 1)) {
                            System.arraycopy(newOffsets, 0, offsets, 0,
                                             newOffsets.length);
                            return true;
                        }
                        break;

                    case 7:
                        // 8
                        if (displays.getOrDefault(segmentsToInt(offsets, perm_s), -1)
                            == 8 && fillOffsets(offsets, sig + 1)) {
                            return true;
                        } else if (tryToSetOffset(newOffsets, perm[0], 0) &&
                            tryToSetOffset(newOffsets, perm[1], 1) &&
                            tryToSetOffset(newOffsets, perm[2], 2) &&
                            tryToSetOffset(newOffsets, perm[3], 3) &&
                            tryToSetOffset(newOffsets, perm[4], 4) &&
                            tryToSetOffset(newOffsets, perm[5], 5) &&
                            tryToSetOffset(newOffsets, perm[6], 6) &&
                            fillOffsets(newOffsets, sig + 1)) {
                            System.arraycopy(newOffsets, 0, offsets, 0,
                                             newOffsets.length);
                            return true;
                        }
                        break;

                    case 5:
                        // 2, 3, 5
                        int n5 = displays.getOrDefault(segmentsToInt(offsets, perm_s),
                                                       -1);
                        if ((n5 == 2 || n5 == 3 || n5 == 5) &&
                            fillOffsets(offsets, sig + 1)) {
                            return true;
                        }

                        // Common segments
                        if (!(tryToSetOffset(newOffsets, perm[0], 0) &&
                              tryToSetOffset(newOffsets, perm[2], 3) &&
                              tryToSetOffset(newOffsets, perm[4], 6))) {
                            continue;
                        }

                        // Try 2
                        int[] twoOffsets = Arrays.copyOf(newOffsets, newOffsets.length);
                        if (tryToSetOffset(twoOffsets, perm[1], 2) &&
                            tryToSetOffset(twoOffsets, perm[3], 4) &&
                            fillOffsets(twoOffsets, sig + 1)) {
                            System.arraycopy(twoOffsets, 0, offsets, 0,
                                             twoOffsets.length);
                            return true;
                        }

                        // Try 3
                        int[] threeOffsets = Arrays.copyOf(newOffsets, newOffsets.length);
                        if (tryToSetOffset(threeOffsets, perm[1], 2) &&
                            tryToSetOffset(threeOffsets, perm[3], 5) &&
                            fillOffsets(threeOffsets, sig + 1)) {
                            System.arraycopy(threeOffsets, 0, offsets, 0,
                                             threeOffsets.length);
                            return true;
                        }

                        // Try 5
                        int[] fiveOffsets = Arrays.copyOf(newOffsets, newOffsets.length);
                        if (tryToSetOffset(fiveOffsets, perm[1], 1) &&
                            tryToSetOffset(fiveOffsets, perm[3], 5) &&
                            fillOffsets(fiveOffsets, sig + 1)) {
                            System.arraycopy(fiveOffsets, 0, offsets, 0,
                                             fiveOffsets.length);
                            return true;
                        }
                        break;

                    case 6:
                        // 0, 6, 9
                        int n6 = displays.getOrDefault(segmentsToInt(offsets, perm_s),
                                                       -1);
                        if ((n6 == 0 || n6 == 6 || n6 == 9) &&
                            fillOffsets(offsets, sig + 1)) {
                            return true;
                        }

                        // Common segments
                        if (!(tryToSetOffset(newOffsets, perm[0], 0) &&
                              tryToSetOffset(newOffsets, perm[1], 1) &&
                              tryToSetOffset(newOffsets, perm[4], 5) &&
                              tryToSetOffset(newOffsets, perm[5], 6))) {
                            continue;
                        }

                        // Try 0
                        int[] zeroOffsets = Arrays.copyOf(newOffsets, newOffsets.length);
                        if (tryToSetOffset(zeroOffsets, perm[2], 2) &&
                            tryToSetOffset(zeroOffsets, perm[3], 4) &&
                            fillOffsets(zeroOffsets, sig + 1)) {
                            System.arraycopy(zeroOffsets, 0, offsets, 0,
                                             zeroOffsets.length);
                            return true;
                        }

                        // Try 6
                        int[] sixOffsets = Arrays.copyOf(newOffsets, newOffsets.length);
                        if (tryToSetOffset(sixOffsets, perm[2], 3) &&
                            tryToSetOffset(sixOffsets, perm[3], 4) &&
                            fillOffsets(sixOffsets, sig + 1)) {
                            System.arraycopy(sixOffsets, 0, offsets, 0,
                                             sixOffsets.length);
                            return true;
                        }

                        // Try 9
                        int[] nineOffsets = Arrays.copyOf(newOffsets, newOffsets.length);
                        if (tryToSetOffset(nineOffsets, perm[2], 2) &&
                            tryToSetOffset(nineOffsets, perm[3], 3) &&
                            fillOffsets(nineOffsets, sig + 1)) {
                            System.arraycopy(nineOffsets, 0, offsets, 0,
                                             nineOffsets.length);
                            return true;
                        }
                        break;

                    default:
                        return false;
                    }
                }
                return false;
            } else {
                return Arrays.stream(offsets).filter(i -> i == -1).count() == 0;
            }
        }

        public int part2() {
            int[] offsets = new int[7];
            Arrays.fill(offsets, -1);

            if (!fillOffsets(offsets, 0)) {
                System.err.println("fillOffsets failed");
                return -1;
            }

            int total = 0;
            for (var digit : digits) {
                int num = displays.getOrDefault(segmentsToInt(offsets, digit), -1);
                if (num == -1) {
                    System.err.println("Unable to find a match for " + digit);
                    System.err.println("Offsets is " + Arrays.toString(offsets));
                    return -1;
                }
                total *= 10;
                total += num;
            }
            return total;
        }
    }


    public static void main(String[] args) {
        try {
            var input = Files.lines(Path.of(args[0])).map(Entry::new).toList();

            long part1 = input.stream()
                .flatMap(entry ->
                         Arrays.stream(entry.getDigits()))
                .filter(d -> d.length() <= 4 || d.length() == 7)
                .count();
            System.out.println("Part 1: " + part1);

            int part2 = input.stream().mapToInt(Entry::part2).sum();
            System.out.println("Part 2: " + part2);
        } catch (IOException e) {
            System.err.println(e);
            System.exit(1);
        }
    }
}
