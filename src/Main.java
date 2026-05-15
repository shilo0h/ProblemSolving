import java.io.*;
import java.util.HashSet;
import java.util.Scanner;
import java.util.Set;


public class Main {
    public static void main(String[] args) throws IOException, InterruptedException {
        String name = "Was it a car or a cat I saw";

        System.out.println(isPalindrome(name));
    }


    private static boolean isPalindrome(String s) {
        String reversed = "";
        String correct = "";
        ;

        for (int j = 0; j < s.length(); j++) {
            if (s.charAt(j) != ' ') {
                correct = correct + s.charAt(j);
            }
        }

            for (int i = correct.length() - 1; i >= 0; i--) {
                reversed = reversed + correct.charAt(i);
            }

            if (reversed.equalsIgnoreCase(correct)) {
                return true;
            } else {
                return false;
            }
        }
}
