package org.embulk.output.chart;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.chart.Axis;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.Chart;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.ScatterChart;
import javafx.scene.chart.StackedBarChart;
import javafx.scene.chart.XYChart;
import javafx.stage.Stage;

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
import org.embulk.spi.Column;
import org.embulk.spi.Exec;
import org.embulk.spi.OutputPlugin;
import org.embulk.spi.Page;
import org.embulk.spi.PageReader;
import org.embulk.spi.Schema;
import org.embulk.spi.TransactionalPageOutput;
import org.embulk.spi.util.PagePrinter;

import org.slf4j.Logger;

public class ChartOutputPlugin extends Application
    implements OutputPlugin
{
    private static String xAxisName;
    private static String yAxisName;
    // List<Map<SeriesName, Map<ColumnName, Value>>>
    private static List<Map<String, Object>> test;
    private static PluginTask task;

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
            // TODO: change name to "Label"
            // TODO: change to optional
            @Config("x_axis_name")
            public String getXAxisName();

            // configuration y-axis type (required AxisType)
            @Config("y_axis_type")
            public AxisType getYAxisType();

            // configuration y-axis name (required String)
            // TODO: change name to "Label"
            // TODO: change to optional
            @Config("y_axis_name")
            public String getYAxisName();

            @Config("serieses")
            public SeriesesConfig getSeriesesConfig();

            @Config("series_mapping_rule")
            @ConfigDefault("null")
            public Optional<SeriesMappingRulesConfig> getSeriesesMappingRulesConfig();
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
        this.task = task;

        log.info("chart type: {}.", task.getChartType());
        log.info("x axis type: {}.", task.getXAxisType());
        log.info("x axis name: {}.", task.getXAxisName());
        log.info("y axis type: {}.", task.getYAxisType());
        log.info("y axis name: {}.", task.getYAxisName());
        log.info("serieses:");

        xAxisName = task.getXAxisName();
        yAxisName = task.getYAxisName();

        for (SeriesConfig series : task.getSeriesesConfig().getSerieses()) {
            log.info("{}.", series);
        }

        if (task.getSeriesesMappingRulesConfig().isPresent()) {
            for (SeriesMappingRuleConfig rule : task.getSeriesesMappingRulesConfig().get().getRules()) {
                log.info("{}.", rule);
            }
        }

        test = new ArrayList<Map<String, Object>>();

        return new TransactionalPageOutput() {
            private final PageReader reader = new PageReader(schema);
            // TODO: timezone setting.
            private final PagePrinter printer = new PagePrinter(schema, "UTC");

            public void add(Page page) {
                reader.setPage(page);
                List<Column> columns = schema.getColumns();
                while (reader.nextRecord()) {
                    Map<String, Object> m = new HashMap<>();
                    for (Column col : columns) {
                        String typeName = col.getType().getName();
                        if (typeName.equals("string")) {
                            m.put(col.getName(), reader.getString(col));
                        } else if (typeName.equals("long")){
                            m.put(col.getName(), reader.getLong(col));
                        } else if (typeName.equals("double")) {
                            m.put(col.getName(), reader.getDouble(col));
                        } else {
                            log.warn("Unsupported column, type: {}, name: {}.", col.getType(), col.getName());
                        }
                    }
                    test.add(m);
                }
            }

            public void finish() {
                String[] args = {};
                ExecutorService service = Executors.newFixedThreadPool(1);
                try {
                    service.submit(() -> Application.launch(ChartOutputPlugin.class, args)).get();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                service.shutdown();
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

    private static Axis getAxis(AxisType axisType) {
        switch (axisType) {
            case NUMBER:
                return new NumberAxis();
            case CATEGORY:
                return new CategoryAxis();
            default:
                throw new RuntimeException("Unsupported Axis Type: " + axisType);
        }
    }

    private static <X,Y> XYChart getXYChart(ChartType chartType, Axis<X> xAxis, Axis<Y> yAxis) {
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
        private final String x;
        private final String y;

        @JsonCreator
        public SeriesConfig(ConfigSource conf) {
            this.name = conf.get(String.class, "name");
            this.x = conf.get(String.class, "x");
            this.y = conf.get(String.class, "y");
        }

        public String getName() {
            return name;
        }

        public String getX() {
            return x;
        }

        public String getY() {
            return y;
        }

        public String toString() {
            return String.format("SeriesConfig[%s, %s, %s]", getName(), getX(), getY());
        }
    }

    public static class SeriesMappingRulesConfig {
        private final List<SeriesMappingRuleConfig> rules;

        @JsonCreator
        public SeriesMappingRulesConfig(List<SeriesMappingRuleConfig> rules) {
            this.rules = rules;
        }

        @JsonValue
        public List<SeriesMappingRuleConfig> getRules() {
            return rules;
        }
    }

    public static class SeriesMappingRuleConfig {
        private final String column;
        private final String value;
        private final String series;

        @JsonCreator
        public SeriesMappingRuleConfig(ConfigSource conf) {
            this.column = conf.get(String.class, "column");
            this.value = conf.get(String.class, "value");
            this.series = conf.get(String.class, "series");
        }

        public String getColumn() {
            return column;
        }

        public String getValue() {
            return value;
        }

        public String getSeries() {
            return series;
        }

        public String toString() {
            return String.format("SeriesMappingRuleConfig[%s, %s, %s]", getColumn(), getValue(), getSeries());
        }
    }

    @Override
    public void start(Stage stage) {

        // 縦横の Axis 定義
        final Axis xAxis = getAxis(task.getXAxisType());
        xAxis.setLabel(xAxisName);
        final Axis yAxis = getAxis(task.getYAxisType());
        yAxis.setLabel(yAxisName);

        // Axis を組み合わせてチャートを定義
        final XYChart chart = getXYChart(task.getChartType(), xAxis, yAxis);

        // シリーズをチャートに登録
        chart.getData().addAll(createSerieses());

        // シーンにチャートを追加して表示
        Scene scene = new Scene(chart, 800, 600);
        stage.setScene(scene);
        stage.setTitle("embulk-output-chart");
        stage.show();
    }

    // tan のシリーズを生成
    private Collection<XYChart.Series> createSerieses() {
        Map<String, SeriesConfig> seriesConfigs = new HashMap<>();
        Map<String, XYChart.Series> serieses = new HashMap<>();

        for (SeriesConfig seriesConfig : task.getSeriesesConfig().getSerieses()) {
            XYChart.Series series = new XYChart.Series();
            series.setName(seriesConfig.getName());
            serieses.put(seriesConfig.getName(), series);
            seriesConfigs.put(seriesConfig.getName(), seriesConfig);
        }

        for (Map<String, Object> m : test) {
            if (task.getSeriesesMappingRulesConfig().isPresent()) {
                for (SeriesMappingRuleConfig rule : task.getSeriesesMappingRulesConfig().get().getRules()) {
                    if (rule.getValue().equals(m.get(rule.getColumn()))) {
                        XYChart.Series series = serieses.get(rule.getSeries());
                        series.getData().add(new XYChart.Data(
                                    m.get(seriesConfigs.get(rule.getSeries()).getX()),
                                    m.get(seriesConfigs.get(rule.getSeries()).getY())));
                    }
                }
            } else {
                for (XYChart.Series series : serieses.values()) {
                    series.getData().add(new XYChart.Data(
                                m.get(seriesConfigs.get(series.getName()).getX()),
                                m.get(seriesConfigs.get(series.getName()).getY())));
                }
            }
        }

        return serieses.values();
    }
}

