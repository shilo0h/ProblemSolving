import java.io.*;
import java.time.Duration;
import java.util.*;


public class Main {
    public static void main(String[] args) throws IOException, InterruptedException {
        int[] array = {1, 1, 2, 3, 3, 3, 4, 4, 5, 6, 6, 6, 7, 8, 9};

        HashMap<Integer, Integer> map = new HashMap<>();
            for (int num : array) {
                if (!map.containsKey(num)) {
                    map.put(num, 1);
                }else{
                    map.put(num, map.get(num) + 1);
                }
            }
        System.out.println(map);
    }
}
