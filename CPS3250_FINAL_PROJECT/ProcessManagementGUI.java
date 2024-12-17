package com.example.demo;

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

public class ProcessManagementGUI extends Application {
    private final ObservableList<Process> processes = FXCollections.observableArrayList();
    private final TableView<Process> processTable = new TableView<>();
    private final TreeItem<String> rootItem = new TreeItem<>("Processes");

    private final PieChart progressPieChart = new PieChart();
    private final BarChart<String, Number> progressBarChart = new BarChart<>(new CategoryAxis(), new NumberAxis());

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

        // Initial chart configuration
        configureBarChart();
        updateCharts();

        // Layout setup
        BorderPane layout = new BorderPane();
        layout.setLeft(taskTree);
        layout.setCenter(processTable);
        layout.setRight(chartsBox);
        layout.setBottom(buttonBox);

        Scene scene = new Scene(layout, 1200, 600);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private void updateCharts() {
        // Print process data for debugging
        for (Process process : processes) {
            System.out.println("Process " + process.getPid() + " Progress: " + process.getProgress());
        }
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
        String pid = "Parent-PID-" + (rootItem.getChildren().size() + 1);
        Process parent = new Process(pid, "Ready", 0, 0.0);
        processes.add(parent);

        TreeItem<String> parentItem = new TreeItem<>(pid);
        rootItem.getChildren().add(parentItem);

        updateCharts(); // Update charts after adding a parent process
    }

    // Adds a child process to a specific parent process
    private void addChildProcess(TreeItem<String> parentItem) {
        if (parentItem == null || parentItem == rootItem) {
            showAlert("No Parent Process Selected", "Please select a parent process to add a child process.");
            return;
        }

        String pid = "Child-PID-" + (processes.size() + 1);
        Process child = new Process(pid, "Ready", 0, 0.0);
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

    // Process data model
    public static class Process {
        private final SimpleStringProperty pid;
        private final SimpleStringProperty status;
        private final SimpleIntegerProperty taskCount;
        private final SimpleDoubleProperty progress;

        public Process(String pid, String status, int taskCount, double progress) {
            this.pid = new SimpleStringProperty(pid);
            this.status = new SimpleStringProperty(status);
            this.taskCount = new SimpleIntegerProperty(taskCount);
            this.progress = new SimpleDoubleProperty(progress);
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
