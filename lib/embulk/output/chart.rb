Embulk::JavaPlugin.register_output(
  "chart", "org.embulk.output.chart.ChartOutputPlugin",
  File.expand_path('../../../../classpath', __FILE__))
