import java.io.*;
import java.util.HashSet;
import java.util.Set;


public class Main {
    public static void main(String[] args) throws IOException, InterruptedException {
        char[][] board = {
                {'1', '2', '.',     '.', '3', '.',    '.', '.', '.'},
                {'4', '.', '.',     '5', '.', '.',    '.', '.', '.'},
                {'.', '9', '8',     '.', '.', '.',    '.', '.', '3'},

                {'5', '.', '.',     '.', '6', '.',    '.', '.', '4'},
                {'.', '.', '.',     '8', '.', '3',    '.', '.', '5'},
                {'7', '.', '.',     '.', '2', '.',    '.', '.', '6'},

                {'.', '.', '.',     '.', '.', '.',    '2', '.', '.'},
                {'.', '.', '.',     '4', '1', '9',    '.', '.', '8'},
                {'.', '.', '.',     '.', '8', '.',    '.', '7', '9'}
            };

        System.out.println(isValidSudoku(board));
    }


    private static boolean isValidSudoku(char[][] board) {
        return true;
    }
}