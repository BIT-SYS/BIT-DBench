package codechef.feblongchallange;

import java.util.Scanner;

public class CHEFBARBER {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.println("Enter N Value :- ");
        var n = scanner.nextInt();
        System.out.println("Enter M Value :- ");
        var m = scanner.nextInt();
        System.out.println(timeTake(n,m));
    }

    private static int timeTake(int n, int m) {
        return n*m;
    }
}
