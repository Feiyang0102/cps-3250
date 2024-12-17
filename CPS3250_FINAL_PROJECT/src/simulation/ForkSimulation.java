package simulation;


import cpu.*;
import filesystem.FileDescriptor;
import memory.PageDirectory;
import memory.PageTableEntry;
import memory.PhysicalMemoryManager;
import process.*;
import utils.PIDGenerator;

import java.util.*;

public class ForkSimulation {
    static final int PG_SIZE = 4096;
    static final int BITMAP_MASK = 1;

    static List<ProcessControlBlock> readyQueue = new LinkedList<>();
    static List<ProcessControlBlock> allProcesses = new LinkedList<>();

    // 模拟当前运行的进程
    static ProcessControlBlock runningProcess;

    public static void main(String[] args) {
        // 创建父进程
        ProcessControlBlock parentProcess = new ProcessControlBlock("parent");
        parentProcess.pid = PIDGenerator.forkPid();
        System.out.println("Parent process created with PID: " + parentProcess.pid);
        parentProcess.userProgVAddr = new UserProgVAddr(0x8048000, 1024); // 假设位图大小为 1024 字节

        // 初始化父进程的内存和位图
        initParentProcess(parentProcess);

        // 将父进程设置为当前运行的进程
        setRunningProcess(parentProcess);
        System.out.println("Parent process is now running.");

        // 调用 sysFork 创建子进程
        System.out.println("Calling sysFork to create child process.");
        long childPid = sysFork();
        if (childPid == -1) {
            System.out.println("Fork failed.");
        } else {
            System.out.println("Fork succeeded. Child PID: " + childPid);
        }

        // 获取子进程
        ProcessControlBlock childProcess = getProcessByPid(childPid);

        // 验证子进程是否成功复制了数据
        if (childProcess != null) {
            System.out.println("Verifying child process memory.");
            verifyChildProcess(childProcess, parentProcess);

            // 模拟进程切换并执行子进程
            System.out.println("Simulating process execution for child PID " + childProcess.pid + ".");
            simulateProcessExecution(childProcess);

            // 模拟子进程写入内存，触发写时复制
            int testVirtualAddress = 0x8048000; // 测试的虚拟地址
            byte[] testData = new byte[]{1, 2, 3, 4};

            System.out.println("Child process attempting to write to memory.");
            writeMemory(childProcess, testVirtualAddress, testData);

            // 验证父进程的内存未被修改
            byte[] parentData = PhysicalMemoryManager.readPhysicalMemory(
                    parentProcess.pageDirectory.getPageTableEntry(testVirtualAddress).physicalAddress);

            System.out.println("Verifying that parent process memory is unchanged.");
            if (Arrays.equals(Arrays.copyOf(parentData, 4), testData)) {
                System.out.println("Error: Parent process memory has been modified.");
            } else {
                System.out.println("Success: Parent process memory is unchanged.");
            }
        }
    }

    public static ProcessControlBlock runningProcess() {
        return runningProcess;
    }

    public static void setRunningProcess(ProcessControlBlock process) {
        runningProcess = process;
    }

    public static ProcessControlBlock getProcessByPid(long pid) {
        for (ProcessControlBlock process : allProcesses) {
            if (process.pid == pid) {
                return process;
            }
        }
        return null;
    }

    // 初始化父进程的内存和位图
    private static void initParentProcess(ProcessControlBlock parentProcess) {
        System.out.println("Initializing parent process memory and bitmap.");
        for (int i = 0; i < 10; i++) {
            int idx = i;
            int virtualAddress = parentProcess.userProgVAddr.vaddrStart + idx * PG_SIZE;
            // 分配物理页面
            int physicalAddress = PhysicalMemoryManager.allocatePhysicalPage();

            // 填充数据
            byte[] data = new byte[PG_SIZE];
            for (int j = 0; j < PG_SIZE; j++) {
                data[j] = (byte) (i + j);
            }
            PhysicalMemoryManager.writePhysicalMemory(physicalAddress, data);

            // 创建页表项
            PageTableEntry entry = new PageTableEntry(physicalAddress, false);
            parentProcess.pageDirectory.addPageTableEntry(virtualAddress, entry);

            // 设置位图
            int idxByte = idx / 8;
            int idxBit = idx % 8;
            parentProcess.userProgVAddr.vaddrBitmap.bits[idxByte] |= (BITMAP_MASK << idxBit);
        }
    }

    // 复制 PCB 和虚拟地址位图
    public static int copyPcbVaddrBitmap(ProcessControlBlock childProcess, ProcessControlBlock parentProcess) {
        System.out.println("Copying PCB and virtual address bitmap from parent PID " + parentProcess.pid + " to child.");

        // 复制父进程的内容到子进程
        childProcess.copyFrom(parentProcess);

        // 修改子进程的特定字段
        childProcess.pid = PIDGenerator.forkPid();
        System.out.println("Assigned PID " + childProcess.pid + " to child process.");
        childProcess.elapsedTicks = 0;
        childProcess.status = TaskStatus.TASK_READY;
        childProcess.ticks = childProcess.priority;
        childProcess.parentPid = parentProcess.pid;
        childProcess.generalTag.prev = null;
        childProcess.generalTag.next = null;
        childProcess.allListTag.prev = null;
        childProcess.allListTag.next = null;
        blockDescInit(childProcess.uBlockDesc);

        // 复制父进程的虚拟地址位图
        int bitmapSize = parentProcess.userProgVAddr.vaddrBitmap.btmpBytesLen;
        byte[] vaddrBtmp = new byte[bitmapSize];
        System.arraycopy(parentProcess.userProgVAddr.vaddrBitmap.bits, 0, vaddrBtmp, 0, bitmapSize);
        childProcess.userProgVAddr.vaddrBitmap.bits = vaddrBtmp;

        if (childProcess.name.length() >= 999999999) {
            throw new AssertionError("Process name too long");
        }
        childProcess.name = childProcess.name + "_fork";

        return 0;
    }

    // 初始化块描述符数组
    public static void blockDescInit(BlockDesc[] uBlockDesc) {
        for (BlockDesc blockDesc : uBlockDesc) {
            blockDesc.init();
        }
    }

    // 复制进程，使用写时复制机制
    public static int copyProcess(ProcessControlBlock childProcess, ProcessControlBlock parentProcess) {
        System.out.println("Copying process from parent PID " + parentProcess.pid + " to child.");

        // a. 复制父进程的 PCB、虚拟地址位图、内核栈到子进程
        System.out.println("Copying PCB, virtual address bitmap, and kernel stack.");
        if (copyPcbVaddrBitmap(childProcess, parentProcess) == -1) {
            return -1;
        }

        // b. 为子进程创建新的页目录
        System.out.println("Creating new page directory for child process.");
        childProcess.pageDirectory = new PageDirectory();

        // c. 遍历父进程的页表项，为子进程创建对应的页表项
        for (Map.Entry<Integer, PageTableEntry> entry : parentProcess.pageDirectory.pageTableEntries.entrySet()) {
            int virtualAddress = entry.getKey();
            PageTableEntry parentEntry = entry.getValue();

            // 创建子进程的页表项，指向相同的物理地址，标记为只读
            PageTableEntry childEntry = new PageTableEntry(parentEntry.physicalAddress, true);

            // 将页表项添加到子进程的页目录中
            childProcess.pageDirectory.addPageTableEntry(virtualAddress, childEntry);

            // 增加物理页的引用计数
            PhysicalMemoryManager.increaseReferenceCount(parentEntry.physicalAddress);

            // 将父进程的页表项也标记为只读
            parentEntry.readOnly = true;
        }

        // d. 构建子进程的线程栈，并修改返回值 PID
        System.out.println("Building child thread stack and modifying return value.");
        buildChildStack(childProcess);

        // e. 更新文件 inode 的打开数
        System.out.println("Updating inode open counts.");
        updateInodeOpenCounts(childProcess);

        return 0;
    }

    // 为子进程构建 thread_stack 并修改返回值
    public static int buildChildStack(ProcessControlBlock childProcess) {
        System.out.println("Building child thread stack and modifying return value.");

        // a. 使子进程的 PID 返回值为 0
        // 获取子进程 0 级栈栈顶
        IntrStack intr0Stack = new IntrStack();
        // 修改子进程的返回值为 0
        intr0Stack.eax = 0;

        // b. 为 switch_to 构建 ThreadStack，将其构建在紧邻 IntrStack 之下的空间
        ThreadStack threadStack = new ThreadStack();
        // switch_to 的返回地址更新为 intr_exit，直接从中断返回
        threadStack.eip = CPU.intrExit;

        // 把构建的 threadStack 的栈顶作为 switch_to 恢复数据时的栈顶
        childProcess.selfKStack = threadStack;

        // 将 IntrStack 和 ThreadStack 保存到子进程的内核栈中
        childProcess.kernelStack = new KernelStack();
        childProcess.kernelStack.intrStack = intr0Stack;
        childProcess.kernelStack.threadStack = threadStack;

        return 0;
    }

    // 更新文件 inode 的打开数（这里简单模拟）
    public static void updateInodeOpenCounts(ProcessControlBlock process) {
        System.out.println("Updating inode open counts for child PID " + process.pid + ".");
        // 假设我们有一个文件描述符表
        // 增加每个打开文件的引用计数
        for (FileDescriptor fd : process.openFiles) {
            fd.inode.openCount++;
        }
    }

    // sysFork 方法
    public static long sysFork() {
        ProcessControlBlock parentProcess = runningProcess();
        System.out.println("Starting fork operation for parent PID " + parentProcess.pid + ".");

        // 为子进程创建 PCB
        ProcessControlBlock childProcess = new ProcessControlBlock("child");
        if (childProcess == null) {
            System.out.println("Failed to create child process.");
            return -1;
        }

        // 调用 copyProcess 复制父进程的资源到子进程
        if (copyProcess(childProcess, parentProcess) == -1) {
            System.out.println("Failed to copy process.");
            return -1;
        }

        // 将子进程添加到就绪队列和所有线程队列
        System.out.println("Adding child PID " + childProcess.pid + " to ready queue and all processes list.");
        readyQueue.add(childProcess);
        allProcesses.add(childProcess);

        // 父进程返回子进程的 PID
        return childProcess.pid;
    }

    // 验证子进程的内存数据是否与父进程一致
    private static void verifyChildProcess(ProcessControlBlock childProcess, ProcessControlBlock parentProcess) {
        System.out.println("Verifying memory of child PID " + childProcess.pid + " against parent PID " + parentProcess.pid + ".");
        for (int virtualAddress : parentProcess.pageDirectory.pageTableEntries.keySet()) {
            PageTableEntry parentEntry = parentProcess.pageDirectory.getPageTableEntry(virtualAddress);
            PageTableEntry childEntry = childProcess.pageDirectory.getPageTableEntry(virtualAddress);

            if (childEntry == null) {
                System.out.println("Child process is missing page at virtual address " + virtualAddress);
                continue;
            }

            if (parentEntry.physicalAddress != childEntry.physicalAddress) {
                System.out.println("Physical address mismatch at virtual address " + virtualAddress);
                return;
            }
        }
        System.out.println("Child process memory copied successfully.");
    }

    // 模拟进程执行
    public static void simulateProcessExecution(ProcessControlBlock process) {
        System.out.println("Simulating process execution for: " + process.name + " with PID " + process.pid);
        // 模拟进程切换
        CPU.switchTo(process);
    }

    // 模拟写入内存，触发写时复制
    public static void writeMemory(ProcessControlBlock process, int virtualAddress, byte[] data) {
        PageTableEntry entry = process.pageDirectory.getPageTableEntry(virtualAddress);
        if (entry == null) {
            System.out.println("Invalid memory access at virtual address " + virtualAddress);
            return;
        }

        if (entry.readOnly) {
            System.out.println("Process PID " + process.pid + " is writing to a shared page at virtual address " + virtualAddress + ". Triggering copy-on-write.");

            // a. 分配新的物理页面
            int newPhysicalAddress = PhysicalMemoryManager.allocatePhysicalPage();

            // b. 复制原始数据到新页面
            byte[] originalData = PhysicalMemoryManager.readPhysicalMemory(entry.physicalAddress);
            PhysicalMemoryManager.writePhysicalMemory(newPhysicalAddress, originalData);

            // c. 更新页表项
            int oldPhysicalAddress = entry.physicalAddress; // 保存旧的物理地址
            entry.physicalAddress = newPhysicalAddress;
            entry.readOnly = false;

            // d. 更新引用计数
            PhysicalMemoryManager.decreaseReferenceCount(oldPhysicalAddress);
        }

        // 写入数据到物理内存
        PhysicalMemoryManager.writePhysicalMemory(entry.physicalAddress, data);
    }
}
