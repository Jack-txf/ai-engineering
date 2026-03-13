package com.feng.claudecode;

import java.util.*;

public class Solution {
    public static void main(String[] args) {
        // System.out.println(new Solution().maxProfit(new int[]{1, 3, 5, 2, 6, 4}, 1));

        System.out.println(new Solution().find_lucky_person(3, 0, 1, new int[]{1, 2, 3}));

        // System.out.println(new Solution().maximumResources(new int[]{2, 3})); // 5
        // System.out.println(new Solution().maximumResources(new int[]{10, 9, 8, 7, 6})); // 22
    }
    // ============= 第三题
    /**
     * 计算管理层最多能投入的资源总量
     * * @param resources int整型一维数组 项目资源上限数组
     * @return int整型 (注：若结果超过int范围，建议在实际比赛中与出题人确认或使用long)
     */
    public int maximumResources(int[] resources) {
        int n = resources.length;
        if (n == 0) return 0;

        // dp[i] 表示以索引 i 为区间右端点时的最优资源分配总和
        long[] dp = new long[n];
        // 维护一个单调递增栈，存储索引，使得 resources[idx] - idx 随栈深增加而增加
        Deque<Integer> stack = new ArrayDeque<>();
        long maxTotal = 0;

        for (int i = 0; i < n; i++) {
            // 我们关注这个差值：vi = resources[i] - i
            long vi = (long) resources[i] - i;

            // 弹出所有“瓶颈”比当前项目更宽松（差值更大）的项目
            while (!stack.isEmpty() && (long) resources[stack.peek()] - stack.peek() >= vi) {
                stack.pop();
            }

            if (stack.isEmpty()) {
                // 情况A：左侧没有瓶颈，分配量从 resources[i] 开始向左依次减1
                // 需要注意资源分配量必须 > 0 才有意义（隐含在严格递增约束中）
                long count = Math.min((long) i + 1, (long) resources[i]);
                long lastValue = resources[i];
                long firstValue = lastValue - count + 1;
                dp[i] = (firstValue + lastValue) * count / 2;
            } else {
                // 情况B：左侧存在瓶颈索引 p
                int p = stack.peek();
                // 从 p+1 到 i 这段区间形成等差数列
                long count = i - p;
                long lastValue = resources[i];
                long firstValue = lastValue - count + 1;
                // 总和 = 瓶颈点 p 的最优解 + 这一段等差数列的和
                dp[i] = dp[p] + (firstValue + lastValue) * count / 2;
            }

            stack.push(i);
            maxTotal = Math.max(maxTotal, dp[i]);
        }

        // 强转回 int 返回，这是由题目模板决定的
        return (int) maxTotal;
    }


    // ============= 第二题
    /**
     * 节点类：模拟双向循环链表中的每一个玩家
     */
    // 使用 long 类型存储 lucky 值，防止多次累加后溢出
    class Node {
        int id;
        long lucky;
        Node next; // 顺时针
        Node prev; // 逆时针

        Node(int id, long lucky) {
            this.id = id;
            this.lucky = lucky;
        }
    }
    public int find_lucky_person(int n, int start, int k, int[] lucky) {
        // 特殊情况：只有一个人
        if (n <= 1) return start;

        // 1. 建立双向循环链表
        Node[] nodes = new Node[n];
        for (int i = 0; i < n; i++) {
            nodes[i] = new Node(i, (long) lucky[i]);
        }
        for (int i = 0; i < n; i++) {
            nodes[i].next = nodes[(i + 1) % n];
            nodes[i].prev = nodes[(i - 1 + n) % n];
        }

        Node currentStart = nodes[start];
        int currentSize = n;

        // 进行 n-1 轮游戏
        for (int t = 1; t <= n - 1; t++) {
            // --- 规则 1: 计算步长 kt ---
            int kt = (k + t) % currentSize;
            if (kt == 0) kt = currentSize;

            // --- 规则 2: 确定方向 ---
            // 顺时针第一个邻接人始终是 currentStart.next
            boolean clockwise = currentStart.lucky >= currentStart.next.lucky;

            // --- 规则 3: 确定目标人 targetNode ---
            Node targetNode = currentStart;
            for (int j = 0; j < kt; j++) {
                if (clockwise) {
                    targetNode = targetNode.next;
                } else {
                    targetNode = targetNode.prev;
                }
            }

            // --- 规则 4: 确定淘汰对象 elimNode ---
            Node targetNeighbor = targetNode.next; // 目标人的顺时针邻居
            long sum = targetNode.lucky + targetNeighbor.lucky;
            Node elimNode;
            if (sum % 2 != 0) {
                // 奇数淘汰顺时针邻居
                elimNode = targetNeighbor;
            } else {
                // 偶数淘汰自己
                elimNode = targetNode;
            }

            // --- 规则 6: 确定下一轮起点 (必须在删除节点前锁定位置) ---
            Node nextStart;
            if (elimNode == targetNode) {
                // 规则说：淘汰目标人，起点为目标人的“逆时针”邻居
                nextStart = elimNode.prev;
            } else {
                // 规则说：淘汰邻居，起点为该邻居的“顺时针”邻居
                nextStart = elimNode.next;
            }

            // --- 规则 5: 幸运值更新 ---
            // 此时 elimNode 还在圈子里，遍历所有人，跳过 elimNode 进行更新
            Node temp = elimNode.next;
            while (temp != elimNode) {
                temp.lucky += (temp.id % 10);
                temp = temp.next;
            }

            // --- 规则 7: 淘汰对象退出圈子 ---
            elimNode.prev.next = elimNode.next;
            elimNode.next.prev = elimNode.prev;

            // 进入下一轮
            currentStart = nextStart;
            currentSize--;
        }

        // 返回最后幸存者的 id
        return currentStart.id;
    }


    //============== 快速排序
    public void quickSort(int[] arr) {
        if (arr == null || arr.length <= 1) return;
        quickSortHelper(arr, 0, arr.length - 1);
    }

    private void quickSortHelper(int[] arr, int left, int right) {
        if (left < right) {
            int pivotIndex = partition(arr, left, right);
            quickSortHelper(arr, left, pivotIndex - 1);
            quickSortHelper(arr, pivotIndex + 1, right);
        }
    }

    private int partition(int[] arr, int left, int right) {
        // 选择最右边的元素作为基准
        int pivot = arr[right];
        int i = left - 1;

        for (int j = left; j < right; j++) {
            if (arr[j] <= pivot) {
                i++;
                swap(arr, i, j);
            }
        }

        swap(arr, i + 1, right);
        return i + 1;
    }

    private void swap(int[] arr, int i, int j) {
        int temp = arr[i];
        arr[i] = arr[j];
        arr[j] = temp;
    }

    //============== 第一题
    public int maxProfit(int[] prices, int k) {
        int n = prices.length;
        // 无法完成任何交易的情况
        if (n < 2) {
            return 0;
        }
        
        // 初始化状态数组
        int[] hold = new int[n];
        int[] sold = new int[n];
        int[] rest = new int[n];
        
        // 第0天的初始状态
        hold[0] = -prices[0];
        sold[0] = 0;
        rest[0] = 0;
        
        // 遍历每一天更新状态
        for (int i = 1; i < n; i++) {
            hold[i] = Math.max(hold[i-1], rest[i-1] - prices[i]);
            sold[i] = hold[i-1] + prices[i];
            if (i >= k) {
                rest[i] = Math.max(rest[i-1], sold[i - k]);
            } else {
                rest[i] = rest[i-1];
            }
        }
        
        // 最终最大利润只能是「可买入」或「刚卖出」状态（不能持有股票到结束）
        return Math.max(rest[n-1], sold[n-1]);
    }
}