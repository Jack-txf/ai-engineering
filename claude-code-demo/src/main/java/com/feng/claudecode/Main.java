package com.feng.claudecode;

import java.util.Arrays;

public class Main {
    public static void main(String[] args) {
        // 测试快速排序
        int[] arr = {1, 5, 3, 2, 4};
        System.out.println("排序前: " + Arrays.toString(arr));

        Solution solution = new Solution();
        solution.quickSort(arr);

        System.out.println("排序后: " + Arrays.toString(arr));
    }
}