import jdk.dynalink.linker.LinkerServices;

import java.sql.SQLOutput;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Scanner;


public class Main {

    public static void main(String[] args) throws InterruptedException {
        List<Integer> list = List.of();

        System.out.println(list.getClass().getClassLoader());
    }
}