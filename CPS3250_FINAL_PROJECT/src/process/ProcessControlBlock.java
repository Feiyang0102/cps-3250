package process;

import cpu.KernelStack;
import filesystem.FileDescriptor;
import memory.PageDirectory;

import java.util.ArrayList;
import java.util.List;

public class ProcessControlBlock {
    public long pid;
    public int elapsedTicks;
    public TaskStatus status;
    public int ticks;
    public int priority;
    public long parentPid;
    public Tag generalTag;
    public Tag allListTag;
    public BlockDesc[] uBlockDesc;
    public UserProgVAddr userProgVAddr;
    public String name;
    public KernelStack kernelStack; // 内核栈
    public Object selfKStack; // 栈顶指针
    public PageDirectory pageDirectory; // 页目录
    public List<FileDescriptor> openFiles; // 打开的文件列表

    public ProcessControlBlock(String name) {
        this.name = name;
        this.generalTag = new Tag();
        this.allListTag = new Tag();
        this.uBlockDesc = new BlockDesc[7]; // 假设有 7 个块描述符
        for (int i = 0; i < uBlockDesc.length; i++) {
            uBlockDesc[i] = new BlockDesc();
        }
        this.openFiles = new ArrayList<>();
        // 初始化其他属性...
        this.priority = 10; // 假设默认优先级为 10
        this.pageDirectory = new PageDirectory();
    }

    // 复制父进程的内容到子进程
    public void copyFrom(ProcessControlBlock parent) {
        this.pid = parent.pid;
        this.elapsedTicks = parent.elapsedTicks;
        this.status = parent.status;
        this.ticks = parent.ticks;
        this.priority = parent.priority;
        this.parentPid = parent.pid;
        this.name = parent.name;
        // 不复制 pageDirectory 引用
        // this.pageDirectory = parent.pageDirectory;
        // 复制 userProgVAddr
        this.userProgVAddr = new UserProgVAddr(parent.userProgVAddr);
        // 复制打开的文件列表
        this.openFiles = new ArrayList<>(parent.openFiles);
    }
}
