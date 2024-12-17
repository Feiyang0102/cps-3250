package cpu;

import process.ProcessControlBlock;

public class CPU {
    public static Runnable intrExit = () -> {
        System.out.println("Process returned to user space.");
    };

    public static void switchTo(ProcessControlBlock process) {
        System.out.println("Switching to process PID " + process.pid + ".");
        // 模拟从 threadStack 中获取返回地址并执行
        if (process.selfKStack != null && process.selfKStack instanceof ThreadStack) {
            ThreadStack threadStack = (ThreadStack) process.selfKStack;
            // 执行返回地址
            threadStack.eip.run();
        } else {
            System.out.println("No thread stack to switch to.");
        }
    }
}
