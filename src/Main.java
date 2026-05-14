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

        for(int i=0;i<9;i++){
            Set<Character>set=new HashSet<>();
            for (int j=0;j<9;j++){
                if (board[i][j]=='.') continue;
                if (set.contains(board[i][j])) return false;
                set.add(board[i][j]);
            }
        }
        for (int i = 0; i < 9; i++) {
            Set<Character> set = new HashSet<>();
            for (int j = 0; j < 9; j++) {
                if (board[j][i] == '.') continue;
                if (set.contains(board[j][i])) return false;
                set.add(board[j][i]);
            }
        }
        for (int square = 0; square < 9; square++) {
            Set<Character> seen = new HashSet<>();
            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 3; j++) {
                    int row = (square / 3) * 3 + i;
                    int col = (square % 3) * 3 + j;
                    if (board[row][col] == '.') continue;
                    if (seen.contains(board[row][col])) return false;
                    seen.add(board[row][col]);
                }
            }
        }
        return true;
    }
}