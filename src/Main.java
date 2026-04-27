import smallProjects.calculator.Calculator;
import streams.FunctionalProgramming;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) throws IOException, InterruptedException {
        int []nums={1,2,3,4,5,6,7,8,9};

        int numToFind=-1;

        int beggining=0;
        int end= nums.length-1;

        while (beggining<=end){
            int middle=(beggining+end)/2;
            if(numToFind==nums[middle]){
                System.out.println("Found at "+middle);
                return;
            }
            if (numToFind<nums[middle]){
                end=middle-1;
            }else{
                beggining=middle+1;
            }
        }
        System.out.println("Didt find it");
    }
}