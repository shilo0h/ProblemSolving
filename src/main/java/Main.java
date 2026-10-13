import org.springframework.data.redis.core.RedisTemplate;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;


public class Main {

    private static final RedisTemplate redisTemplate = new RedisTemplate<>();


    public static void main(String[] args) throws InterruptedException {

         redisTemplate.opsForSet().add("test", "test");

    }

    public static int maxProfit(int[] prices) {

        return 0;
    }
}

    /*

     🧩 Next problem: Best Time to Buy and Sell Stock
        Actually, let's make this one a little different from the previous version.
        Given an array of prices, find the maximum profit you can make by buying once and selling once.
        prices = [7, 1, 5, 3, 6, 4]
        → 5
        Because:
        buy at 1
        sell at 6
        profit = 5
        If no profit is possible:
        prices = [7, 6, 4, 3, 1]
        → 0
        Start with:

     */