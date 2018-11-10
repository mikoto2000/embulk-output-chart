package org.embulk.output.chart;

import java.util.List;

import javafx.scene.chart.Axis;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.Chart;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.ScatterChart;
import javafx.scene.chart.StackedBarChart;
import javafx.scene.chart.XYChart;

import com.google.common.base.Optional;

import org.embulk.config.Config;
import org.embulk.config.ConfigDefault;
import org.embulk.config.ConfigDiff;
import org.embulk.config.ConfigSource;
import org.embulk.config.Task;
import org.embulk.config.TaskReport;
import org.embulk.config.TaskSource;
import org.embulk.spi.Exec;
import org.embulk.spi.OutputPlugin;
import org.embulk.spi.PageOutput;
import org.embulk.spi.Schema;
import org.embulk.spi.TransactionalPageOutput;

public class ChartOutputPlugin
        implements OutputPlugin
{
    public interface PluginTask
            extends Task
    {
        // configuration option 1 (required integer)
        @Config("option1")
        public int getOption1();

        // configuration option 2 (optional string, null is not allowed)
        @Config("option2")
        @ConfigDefault("\"myvalue\"")
        public String getOption2();

        // configuration option 3 (optional string, null is allowed)
        @Config("option3")
        @ConfigDefault("null")
        public Optional<String> getOption3();
    }

    @Override
    public ConfigDiff transaction(ConfigSource config,
            Schema schema, int taskCount,
            OutputPlugin.Control control)
    {
        PluginTask task = config.loadConfig(PluginTask.class);

        // retryable (idempotent) output:
        // return resume(task.dump(), schema, taskCount, control);

        // non-retryable (non-idempotent) output:
        control.run(task.dump());
        return Exec.newConfigDiff();
    }

    @Override
    public ConfigDiff resume(TaskSource taskSource,
            Schema schema, int taskCount,
            OutputPlugin.Control control)
    {
        throw new UnsupportedOperationException("chart output plugin does not support resuming");
    }

    @Override
    public void cleanup(TaskSource taskSource,
            Schema schema, int taskCount,
            List<TaskReport> successTaskReports)
    {
    }

    @Override
    public TransactionalPageOutput open(TaskSource taskSource, Schema schema, int taskIndex)
    {
        PluginTask task = taskSource.loadTask(PluginTask.class);

        // Write your code here :)
        throw new UnsupportedOperationException("ChartOutputPlugin.run method is not implemented yet");
    }

    enum ChartType {
        BAR,
        LINE,
        SCATTER,
        STACKED_BAR
    }

    enum AxisType {
        NUMBER,
        CATEGORY
    }

    private Axis getAxis(AxisType axisType) {
        switch (axisType) {
            case NUMBER:
                return new NumberAxis();
            case CATEGORY:
                return new CategoryAxis();
            default:
                throw new RuntimeException("Unsupported Axis Type: " + axisType);
        }
    }

    private <X,Y> XYChart getXYChart(ChartType chartType, Axis<X> xAxis, Axis<Y> yAxis) {
        switch (chartType) {
            case BAR:
                return new BarChart<X, Y>(xAxis, yAxis);
            case LINE:
                return new LineChart<X, Y>(xAxis, yAxis);
            case SCATTER:
                return new ScatterChart<X, Y>(xAxis, yAxis);
            case STACKED_BAR:
                return new StackedBarChart<X, Y>(xAxis, yAxis);
            default:
                throw new RuntimeException("Unsupported Chart Type: " + chartType);
        }
    }
}
