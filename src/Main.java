import com.sun.jdi.VirtualMachine;
import designPatterns.builderPattern.UserBuilder;
import designPatterns.factoryPattern.Notification;
import designPatterns.factoryPattern.NotificationFactory;
import designPatterns.strategyPattern.*;

import javax.print.attribute.standard.RequestingUserName;
import java.awt.*;
import java.util.Random;
import java.util.Scanner;
import java.util.Stack;
import java.util.stream.IntStream;

public class Main {

    public static void main(String[] args) {
        SortService sortService1=new SortService(new BubbleSort());
        SortService sortService2=new SortService(new MergeSort());
        SortService sortService3=new SortService(new QuickSort());


        int[]nums={1,2,3,243,24343,4};
        sortService1.sort(nums);
        sortService2.sort(nums);
        sortService3.sort(nums);

    }
}