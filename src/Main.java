import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) throws IOException, InterruptedException {
            List<Integer> nums=new ArrayList<>(List.of(1,2,3,4,5,6,7,8,9,20));
            List<Integer>biggerThenFour=nums.stream().filter(n->n<=4).map(n->n*n)
                    .collect(Collectors.toList());
        System.out.println(biggerThenFour);
    }
}