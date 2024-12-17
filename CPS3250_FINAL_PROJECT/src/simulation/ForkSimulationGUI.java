package simulation;

import cpu.*;
import filesystem.*;
import javafx.application.Application;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.chart.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import memory.*;
//import memory.PageTableEntry;
//import memory.PhysicalMemoryManager;
import process.*;
//import utils.PIDGenerator;

import java.util.*;

public class ForkSimulationGUI extends Application {
    private final ObservableList<Process> processes = FXCollections.observableArrayList();
    private final TableView<Process> processTable = new TableView<>();
    private final TreeItem<String> rootItem = new TreeItem<>("Processes");

    private final PieChart progressPieChart = new PieChart();
    private final BarChart<String, Number> progressBarChart = new BarChart<>(new CategoryAxis(), new NumberAxis());




    private final Label initTimeLabel = new Label("Initialization Time: N/A");
    private static final Label copyOnWriteTimeLabel = new Label("Copy-On-Write Time: N/A");


    private String duration;
    private static String cowDuration;



    // 新增：用于显示日志信息的文本区域
    private static final TextArea logTextArea = new TextArea();

    // 保持与 ForkSimulation 中相同的静态变量
    static final int PG_SIZE = 4096;
    static final int BITMAP_MASK = 1;

    static List<ProcessControlBlock> readyQueue = new LinkedList<>();
    static List<ProcessControlBlock> allProcesses = new LinkedList<>();

    // 模拟当前运行的进程
    static ProcessControlBlock runningProcess;


    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Process Management Simulation");

        // Task tree representing process hierarchy
        rootItem.setExpanded(true);
        TreeView<String> taskTree = new TreeView<>(rootItem);
        taskTree.setPrefWidth(200);

        // Process table showing process details
        processTable.setPrefWidth(400);
        processTable.setItems(processes);

        // Table columns
        TableColumn<Process, String> pidCol = new TableColumn<>("PID");
        pidCol.setCellValueFactory(data -> data.getValue().pidProperty());

        TableColumn<Process, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(data -> data.getValue().statusProperty());

        TableColumn<Process, Integer> taskCol = new TableColumn<>("Task Count");
        taskCol.setCellValueFactory(data -> data.getValue().taskCountProperty().asObject());

        TableColumn<Process, Double> progressCol = new TableColumn<>("Progress");
        progressCol.setCellValueFactory(data -> data.getValue().progressProperty().asObject());
        progressCol.setCellFactory(col -> new ProgressBarTableCell());

        processTable.getColumns().addAll(pidCol, statusCol, taskCol, progressCol);

        // Control buttons
        Button addParentBtn = new Button("Add Parent Process");
        addParentBtn.setOnAction(event -> addParentProcess());

        Button addChildBtn = new Button("Add Child Process");
        addChildBtn.setOnAction(event -> addChildProcess(taskTree.getSelectionModel().getSelectedItem()));

        Button assignTaskBtn = new Button("Assign Task");
        assignTaskBtn.setOnAction(event -> assignTaskToSelectedProcess());

        Button simulateErrorBtn = new Button("Simulate Exception");
        simulateErrorBtn.setOnAction(event -> simulateErrorForSelectedProcess());

        HBox buttonBox = new HBox(10, addParentBtn, addChildBtn, assignTaskBtn, simulateErrorBtn);
        buttonBox.setPadding(new Insets(10));

        // Chart setup for visualization
        VBox chartsBox = new VBox(10);
        chartsBox.getChildren().addAll(
                new Label("Process Progress Distribution"), progressPieChart,
                new Label("Process Progress Overview"), progressBarChart
        );
        chartsBox.setPadding(new Insets(10));
        chartsBox.setPrefWidth(400);

        // Timing labels setup
        HBox timeBox = new HBox(10, initTimeLabel, copyOnWriteTimeLabel);
        timeBox.setPadding(new Insets(10));
        timeBox.setStyle("-fx-background-color: #f4f4f4;");

        // Log area setup
        logTextArea.setEditable(false);
        logTextArea.setPrefHeight(200);
        VBox logBox = new VBox(new Label("Simulation Logs"), logTextArea);
        logBox.setPadding(new Insets(10));

        // Layout setup
        BorderPane layout = new BorderPane();
        layout.setLeft(taskTree);
        layout.setCenter(processTable);
        layout.setRight(chartsBox);

        // Add log area and timing labels to bottom
        VBox bottomBox = new VBox(buttonBox, logBox, timeBox);
        layout.setBottom(bottomBox);

        Scene scene = new Scene(layout, 1200, 700);
        primaryStage.setScene(scene);
        primaryStage.show();
    }


    private void updateCharts() {
        updatePieChart();  // 更新饼图
        updateBarChart();  // 更新柱状图
    }

    private void updatePieChart() {
        progressPieChart.getData().clear();  // 清空原有数据

        if (processes.isEmpty()) return;

        for (Process process : processes) {
            // Create PieChart data slice
            PieChart.Data slice = new PieChart.Data(
                    process.getPid() + " (" + process.getStatus() + ")",
                    process.getProgress() > 0 ? process.getProgress() : 0.1 // Ensure there's always a slice
            );

            // Add PieChart data slice to the pie chart
            progressPieChart.getData().add(slice);

            // Wait for the node to be created before setting the style
            slice.getNode().setStyle(getColorStyle(process.getStatus()));
        }
    }

    private void updateBarChart() {
        progressBarChart.getData().clear();  // Clear old data

        if (processes.isEmpty()) return;

        for (TreeItem<String> parentItem : rootItem.getChildren()) {
            XYChart.Series<String, Number> series = new XYChart.Series<>();
            series.setName(parentItem.getValue());

            double parentProgress = 0.0;
            for (TreeItem<String> childItem : parentItem.getChildren()) {
                String childPid = childItem.getValue();
                Process childProcess = processes.stream()
                        .filter(p -> p.getPid().equals(childPid))
                        .findFirst()
                        .orElse(null);

                if (childProcess != null) {
                    parentProgress += childProcess.getProgress();
                    series.getData().add(new XYChart.Data<>(childProcess.getPid(), childProcess.getProgress()));
                }
            }

            series.getData().add(new XYChart.Data<>(parentItem.getValue(), parentProgress));
            progressBarChart.getData().add(series);
        }
    }

    // Configures the bar chart initially
    private void configureBarChart() {
        progressBarChart.setTitle("Parent and Child Process Progress");
        progressBarChart.getXAxis().setLabel("Processes");
        progressBarChart.getYAxis().setLabel("Progress");
    }

    // Returns a color style based on the process status
    private String getColorStyle(String status) {
        switch (status) {
            case "Running": return "-fx-pie-color: green;";
            case "Error": return "-fx-pie-color: red;";
            case "Ready": return "-fx-pie-color: blue;";
            default: return "-fx-pie-color: gray;";
        }
    }

    // Adds a new parent process to the tree and table
    private void addParentProcess() {
        long startTime = System.nanoTime(); // start timing

        // 原有的父进程创建逻辑
        ProcessControlBlock parentProcess = new ProcessControlBlock("parent");
        parentProcess.pid = PIDGenerator.forkPid();
        parentProcess.userProgVAddr = new UserProgVAddr(0x8048000, 1024);

        initParentProcess(parentProcess); // Initialization
        setRunningProcess(parentProcess);
        allProcesses.add(parentProcess);

        long endTime = System.nanoTime(); // end timing
        long duration = (endTime - startTime) / 1_000_000; // switching to millisecond
        initTimeLabel.setText("Initialization Time: " + duration + " ms");

        // 在 GUI 中添加父进程
        String pid = "PID-" + parentProcess.pid;
        Process parent = new Process(pid, "Ready", 0, 0.0, parentProcess);
        processes.add(parent);

        TreeItem<String> parentItem = new TreeItem<>(pid);
        rootItem.getChildren().add(parentItem);

        updateCharts(); // 更新图表
    }


    // Adds a child process to a specific parent process
    private void addChildProcess(TreeItem<String> parentItem) {
        if (parentItem == null || parentItem == rootItem) {
            showAlert("No Parent Process Selected", "Please select a parent process to add a child process.");
            return;
        }

        // 获取父进程的 PCB
        String parentPidStr = parentItem.getValue();
        Process parentProcess = processes.stream()
                .filter(p -> p.getPid().equals(parentPidStr))
                .findFirst()
                .orElse(null);

        if (parentProcess == null || parentProcess.getPcb() == null) {
            showAlert("Invalid Parent Process", "The selected parent process is invalid.");
            return;
        }

        // 模拟 sysFork 创建子进程
        ProcessControlBlock childPcb = new ProcessControlBlock("child");
        long childPid = sysFork(parentProcess.getPcb(), childPcb);
        if (childPid == -1) {
            log("Fork failed.");
            showAlert("Fork Failed", "Unable to create child process.");
            return;
        } else {
            log("Fork succeeded. Child PID: " + childPid);
        }

        // 在 GUI 中添加子进程
        String pid = "PID-" + childPid;
        Process child = new Process(pid, "Ready", 0, 0.0, childPcb);
        processes.add(child);

        TreeItem<String> childItem = new TreeItem<>(pid);
        parentItem.getChildren().add(childItem);
        parentItem.setExpanded(true); // Expand parent to make the new child visible

        updateCharts(); // Update charts after adding a child process
    }

    // Assigns a task to the selected process
    private void assignTaskToSelectedProcess() {
        Process selectedProcess = processTable.getSelectionModel().getSelectedItem();
        if (selectedProcess == null) {
            showAlert("No Process Selected", "Please select a process to assign a task.");
            return;
        }

        if (selectedProcess.getProgress() >= 1.0) {
            showAlert("Maximum Progress Reached", "Cannot assign more tasks to a process with 100% progress.");
            return;
        }

        int newTaskCount = selectedProcess.getTaskCount() + 1;
        double newProgress = Math.min(1.0, selectedProcess.getProgress() + 0.1); // Limit progress to 1.0
        selectedProcess.setTaskCount(newTaskCount);
        selectedProcess.setProgress(newProgress);
        selectedProcess.setStatus("Running");

        // 模拟进程执行
        simulateProcessExecution(selectedProcess.getPcb());

        updateCharts(); // Update charts after modifying a process
    }

    // Simulates an exception for the selected process
    private void simulateErrorForSelectedProcess() {
        Process selectedProcess = processTable.getSelectionModel().getSelectedItem();
        if (selectedProcess == null) {
            showAlert("No Process Selected", "Please select a process to simulate an exception.");
            return;
        }

        selectedProcess.setStatus("Error");
        log("Process " + selectedProcess.getPid() + " encountered an error.");
        updateCharts(); // Update charts to reflect error status
    }

    // Displays an alert dialog
    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    // 日志输出方法
    private static void log(String message) {
        logTextArea.appendText(message + "\n");
    }

    // 模拟 initParentProcess 方法
    private void initParentProcess(ProcessControlBlock parentProcess) {
        log("Initializing parent process memory and bitmap.");

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

    // 模拟 sysFork 方法
    public static long sysFork(ProcessControlBlock parentProcess, ProcessControlBlock childProcess) {
        logStatic("Starting fork operation for parent PID " + parentProcess.pid + ".");

        if (childProcess == null) {
            logStatic("Failed to create child process.");
            return -1;
        }

        // 调用 copyProcess 复制父进程的资源到子进程
        if (copyProcess(childProcess, parentProcess) == -1) {
            logStatic("Failed to copy process.");
            return -1;
        }

        // 将子进程添加到就绪队列和所有进程列表
        logStatic("Adding child PID " + childProcess.pid + " to ready queue and all processes list.");
        readyQueue.add(childProcess);
        allProcesses.add(childProcess);

        // 父进程返回子进程的 PID
        return childProcess.pid;
    }

    // 模拟 copyProcess 方法
    public static int copyProcess(ProcessControlBlock childProcess, ProcessControlBlock parentProcess) {
        logStatic("Copying process from parent PID " + parentProcess.pid + " to child.");

        // a. 复制父进程的 PCB、虚拟地址位图、内核栈到子进程
        logStatic("Copying PCB, virtual address bitmap, and kernel stack.");
        if (copyPcbVaddrBitmap(childProcess, parentProcess) == -1) {
            return -1;
        }

        // b. 为子进程创建新的页目录
        logStatic("Creating new page directory for child process.");
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
        logStatic("Building child thread stack and modifying return value.");
        buildChildStack(childProcess);

        // e. 更新文件 inode 的打开数
        logStatic("Updating inode open counts.");
        updateInodeOpenCounts(childProcess);

        return 0;
    }

    // 模拟 copyPcbVaddrBitmap 方法
    public static int copyPcbVaddrBitmap(ProcessControlBlock childProcess, ProcessControlBlock parentProcess) {
        logStatic("Copying PCB and virtual address bitmap from parent PID " + parentProcess.pid + " to child.");

        // 复制父进程的内容到子进程
        childProcess.copyFrom(parentProcess);

        // 修改子进程的特定字段
        childProcess.pid = PIDGenerator.forkPid();
        logStatic("Assigned PID " + childProcess.pid + " to child process.");
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

    // 为子进程构建线程栈，并修改返回值
    public static int buildChildStack(ProcessControlBlock childProcess) {
        logStatic("Building child thread stack and modifying return value.");

        // a. 使子进程的 PID 返回值为 0
        IntrStack intr0Stack = new IntrStack();
        intr0Stack.eax = 0;

        // b. 为 switch_to 构建 ThreadStack，将其构建在紧邻 IntrStack 之下的空间
        ThreadStack threadStack = new ThreadStack();
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
        logStatic("Updating inode open counts for child PID " + process.pid + ".");
        // 假设我们有一个文件描述符表
        // 增加每个打开文件的引用计数
        for (FileDescriptor fd : process.openFiles) {
            fd.inode.openCount++;
        }
    }

    // 模拟进程执行
    public static void simulateProcessExecution(ProcessControlBlock process) {

        if (process == null) return;

        log("Simulating process execution for PID " + process.pid + ".");
        // 模拟进程切换
        CPU.switchTo(process);

        // 模拟子进程写入内存，触发写时复制
















        long startTime = System.nanoTime(); // Start timing
















        int testVirtualAddress = 0x8048000; // 测试的虚拟地址
        byte[] testData = new byte[]{1, 2, 3, 4};

        log("Process PID " + process.pid + " attempting to write to memory.");
        writeMemory(process, testVirtualAddress, testData);

        // 验证父进程的内存未被修改
        ProcessControlBlock parentProcess = getProcessByPid(process.parentPid);
        if (parentProcess != null) {
            byte[] parentData = PhysicalMemoryManager.readPhysicalMemory(
                    parentProcess.pageDirectory.getPageTableEntry(testVirtualAddress).physicalAddress);

            log("Verifying that parent process memory is unchanged.");
            if (Arrays.equals(Arrays.copyOf(parentData, 4), testData)) {
                log("Error: Parent process memory has been modified.");
            } else {
                log("Success: Parent process memory is unchanged.");
            }
        }
    }

    // 模拟写入内存，触发写时复制
    public static void writeMemory(ProcessControlBlock process, int virtualAddress, byte[] data) {
        PageTableEntry entry = process.pageDirectory.getPageTableEntry(virtualAddress);
        if (entry == null) {
            logStatic("Invalid memory access at virtual address " + virtualAddress);
            return;
        }

        if (entry.readOnly) {
            logStatic("Process PID " + process.pid + " is writing to a shared page at virtual address " + virtualAddress + ". Triggering copy-on-write.");




























            long endTime = System.nanoTime(); // End timing
            long startTime = 0;
            long duration = (endTime - startTime) / 1_000_000; // Convert to milliseconds
            System.out.println("Copy-On-Write Time: " + duration + " ms");
            log("Copy-On-Write Time: " + duration + " ms");







            copyOnWriteTimeLabel.setText("Copy-On-Write Time: " + duration + " ms");



















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
    

    // 根据 PID 获取进程 PCB
    public static ProcessControlBlock getProcessByPid(long pid) {
        for (ProcessControlBlock process : allProcesses) {
            if (process.pid == pid) {
                return process;
            }
        }
        return null;
    }

    // 设置当前运行的进程
    public static void setRunningProcess(ProcessControlBlock process) {
        runningProcess = process;
    }

    // 日志输出方法，用于静态方法中
    private static void logStatic(String message) {
        // 由于静态方法无法直接访问实例变量，这里简单打印到控制台
        System.out.println(message);
    }

    // Process data model
    public static class Process {
        private final SimpleStringProperty pid;
        private final SimpleStringProperty status;
        private final SimpleIntegerProperty taskCount;
        private final SimpleDoubleProperty progress;
        private final ProcessControlBlock pcb; // 关联的 PCB

        public Process(String pid, String status, int taskCount, double progress, ProcessControlBlock pcb) {
            this.pid = new SimpleStringProperty(pid);
            this.status = new SimpleStringProperty(status);
            this.taskCount = new SimpleIntegerProperty(taskCount);
            this.progress = new SimpleDoubleProperty(progress);
            this.pcb = pcb;
        }

        public String getPid() {
            return pid.get();
        }

        public SimpleStringProperty pidProperty() {
            return pid;
        }

        public String getStatus() {
            return status.get();
        }

        public void setStatus(String status) {
            this.status.set(status);
        }

        public SimpleStringProperty statusProperty() {
            return status;
        }

        public int getTaskCount() {
            return taskCount.get();
        }

        public void setTaskCount(int taskCount) {
            this.taskCount.set(taskCount);
        }

        public SimpleIntegerProperty taskCountProperty() {
            return taskCount;
        }

        public double getProgress() {
            return progress.get();
        }

        public void setProgress(double progress) {
            this.progress.set(progress);
        }

        public SimpleDoubleProperty progressProperty() {
            return progress;
        }

        public ProcessControlBlock getPcb() {
            return pcb;
        }
    }

    // Custom Progress Bar Table Cell
    private static class ProgressBarTableCell extends TableCell<Process, Double> {
        private final ProgressBar progressBar = new ProgressBar();

        @Override
        protected void updateItem(Double progress, boolean empty) {
            super.updateItem(progress, empty);
            if (empty || progress == null) {
                setGraphic(null);
            } else {
                progressBar.setProgress(progress);
                setGraphic(progressBar);
            }
        }
    }
}
