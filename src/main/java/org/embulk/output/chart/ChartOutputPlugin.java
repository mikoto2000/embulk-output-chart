package org.embulk.output.chart;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.ObservableList;
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
    private static List<Map<String, Number>> test;
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
            @Config("x_axis_name")
            public String getXAxisName();

            // configuration y-axis type (required AxisType)
            @Config("y_axis_type")
            public AxisType getYAxisType();

            // configuration y-axis name (required String)
            // TODO: change name to "Label"
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

        test = new ArrayList<Map<String, Number>>();

        return new TransactionalPageOutput() {
            private final PageReader reader = new PageReader(schema);
            // TODO: timezone setting.
            private final PagePrinter printer = new PagePrinter(schema, "UTC");

            public void add(Page page) {
                reader.setPage(page);
                List<Column> targets = new ArrayList<>();
                for (SeriesConfig series : task.getSeriesesConfig().getSerieses()) {
                    targets.add(schema.lookupColumn(series.getX()));
                    targets.add(schema.lookupColumn(series.getY()));
                }
                while (reader.nextRecord()) {
                    System.out.println(printer.printRecord(reader, ","));
                    Map<String, Number> m = new HashMap<>();
                    for (Column col : targets) {
                        m.put(col.getName(), reader.getLong(col));
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

    @Override
    public void start(Stage stage) {

        // 縦横の Axis 定義
        final NumberAxis xAxis = new NumberAxis();
        xAxis.setLabel(xAxisName);
        final NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel(yAxisName);

        // Axis を組み合わせてチャートを定義
        final ScatterChart<Number, Number> ScatterChart = new ScatterChart<Number, Number>(xAxis, yAxis);

        // チャートに登録するデータ(シリーズ)を作成
        List<XYChart.Series<Number, Number>> series3 = createAtan();

        // シリーズをチャートに登録
        ObservableList<ScatterChart.Series<Number, Number>> ScatterChartDatas =ScatterChart.getData();
        ScatterChartDatas.addAll(series3);

        // シーンにチャートを追加して表示
        Scene scene = new Scene(ScatterChart, 800, 600);
        stage.setScene(scene);
        stage.setTitle("ScatterChart Sample");
        stage.show();
    }

    // tan のシリーズを生成
    // TODO: List<Series<Number, Number>> を返却するように修正
    private List<XYChart.Series<Number, Number>> createAtan() {
        List<SeriesConfig> seriesConfigs = task.getSeriesesConfig().getSerieses();
        List<XYChart.Series<Number, Number>> serieses = new ArrayList(seriesConfigs.size());

        for (SeriesConfig seriesConfig : seriesConfigs) {
            XYChart.Series<Number, Number> series = new XYChart.Series<Number, Number>();
            series.setName(seriesConfig.getName());

            for (Map<String, Number> m : test) {
                series.getData().add(new ScatterChart.Data<Number, Number>(
                            m.get(seriesConfig.getX()),
                            m.get(seriesConfig.getY())));
            }

            serieses.add(series);
        }


        return serieses;
    }
}

