import java.io.*;
import java.util.HashSet;
import java.util.Scanner;
import java.util.Set;


public class Main {
    public static void main(String[] args) throws IOException, InterruptedException {
       int test=8/5;
        System.out.println(test);

    }

    private int minEatingSpeed(int[] piles, int h) {
        int pileSum=0;
        for (int i=0;i<piles.length;i++){
            pileSum=pileSum+piles[i];
        }

        int howMany=pileSum/h;

        if (howMany*h<pileSum){
            pileSum=pileSum+1;
        }

        return pileSum;
    }
}

