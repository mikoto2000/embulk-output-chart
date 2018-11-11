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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
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
import org.embulk.spi.Page;
import org.embulk.spi.PageReader;
import org.embulk.spi.Schema;
import org.embulk.spi.TransactionalPageOutput;
import org.embulk.spi.util.PagePrinter;

import org.slf4j.Logger;

public class ChartOutputPlugin
        implements OutputPlugin
{
    public interface PluginTask
            extends Task
    {
        // configuration chart type (required ChartType)
        @Config("chart_type")
        public ChartType getChartType();

        // configuration x-axis type (required AxisType)
        @Config("x_axis_type")
        public AxisType getXAxisType();

        // configuration x-axis name (required String)
        @Config("x_axis_name")
        public String getXAxisName();

        // configuration y-axis type (required AxisType)
        @Config("y_axis_type")
        public AxisType getYAxisType();

        // configuration y-axis name (required String)
        @Config("y_axis_name")
        public String getYAxisName();

        // configuration y-axis column name (required String)
        @Config("serieses")
        public SeriesesConfig getSeriesesConfig();
    }

    private Logger log = Exec.getLogger(ChartOutputPlugin.class);

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

        log.info("chart type: {}.", task.getChartType());
        log.info("x axis type: {}.", task.getXAxisType());
        log.info("x axis name: {}.", task.getXAxisName());
        log.info("y axis type: {}.", task.getYAxisType());
        log.info("y axis name: {}.", task.getYAxisName());
        log.info("serieses:");
        for (SeriesConfig series : task.getSeriesesConfig().getSerieses()) {
            log.info("{}.", series);
        }

        return new TransactionalPageOutput() {
            private final PageReader reader = new PageReader(schema);
            // TODO: timezone setting.
            private final PagePrinter printer = new PagePrinter(schema, "UTC");

            public void add(Page page) {
                reader.setPage(page);
                while (reader.nextRecord()) {
                    System.out.println(printer.printRecord(reader, ","));
                }
            }

            public void finish() {
                System.out.flush();
            }

            public void close() {
                reader.close();
            }

            public void abort() {}

            public TaskReport commit() {
                return Exec.newTaskReport();
            }
        };
    }

    public enum ChartType {
        BAR,
        LINE,
        SCATTER,
        STACKED_BAR;
    }

    public enum AxisType {
        NUMBER,
        CATEGORY;
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

    public static class SeriesesConfig {
        private final List<SeriesConfig> serieses;

        @JsonCreator
        public SeriesesConfig(List<SeriesConfig> serieses) {
            this.serieses = serieses;
        }

        @JsonValue
        public List<SeriesConfig> getSerieses() {
            return serieses;
        }
    }

    public static class SeriesConfig {
        private final String name;
        private final String column;

        @JsonCreator
        public SeriesConfig(ConfigSource conf) {
            this.name = conf.get(String.class, "name");
            this.column = conf.get(String.class, "column");
        }

        public String getName() {
            return name;
        }

        public String getColumn() {
            return column;
        }

        public String toString() {
            return String.format("SeriesConfig[%s, %s]", getName(), getColumn());
        }
    }
}
