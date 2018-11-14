# Chart output plugin for Embulk

Display chart with Java FX.

![](https://raw.githubusercontent.com/mikoto2000/embulk-output-chart/images/doc/image/scatter.png)

## Overview

* **Plugin type**: output
* **Load all or nothing**: no
* **Resume supported**: no
* **Cleanup supported**: yes

## Configuration

- **chart_type**: description (enum, required)
    - BAR
    - LINE
    - SCATTER
    - STACKED_BAR
- **x_axis_type**: description (enum, required)
    - NUMBER
    - CATEGORY
- **x_axis_name**: description (string, required)
- **y_axis_type**: description (enum, required)
    - NUMBER
    - CATEGORY
- **x_axis_name**: description (string, required)
- **serieses**: description (SeriesesConfig, required)
    - **name**: series name (string, required)
    - **x**: column name of x axis value (string, required)
    - **y**: column name of y axis value (string, required)
- **series_mapping_rule**: description (SeriesesMappingRuleConfig, required)
    - **column**: column name (string, required)
    - **value**: column で指定した列の値がこの値と等しい場合、series で指定したシリーズにデータを登録する (string, required)
    - **series**: series name (string, required)

## Limitation

- Only on `Local Executor Plugin`
- You need set `1` to `max_threads` and `min_output_tasks`
- LocalFileInputPlugin で複数ファイルを読み込むなどで、複数タスクが実行される場合の動作保証はできません


```yaml
exec:
  max_threads: 1
  min_output_tasks: 1
```

## Example

### 各シリーズで数が同じ場合

例えばこんな CSV ファイルの場合。

```csv
Value A,Value B Value C,Value D,Name
51,35,14,02,User 1
70,32,47,14,User 2
63,33,60,25,User 3
```

config.yml

```yaml
out:
  type: chart
  chart_type: BAR
  x_axis_name: Name
  x_axis_type: CATEGORY
  y_axis_name: Value
  y_axis_type: NUMBER
  serieses:
    - {name: A, x: Name, y: Value A}
    - {name: B, x: Name, y: Value B}
    - {name: C, x: Name, y: Value C}
    - {name: D, x: Name, y: Value D}
```

![](https://raw.githubusercontent.com/mikoto2000/embulk-output-chart/images/doc/image/bar.png)


### 各シリーズで数が違う場合

例えばこんな csv ファイルの場合。

```csv
time,value,name
1,2,user 1
2,1,user 2
2,4,user 1
3,2,user 2
3,6,user 1
4,1,user 3
4,4,user 2
4,8,user 1
5,2,user 3
5,8,user 2
6,3,user 3
7,5,user 3
```

config.yml

```yaml
out:
  type: chart
  chart_type: line
  x_axis_name: time
  x_axis_type: number
  y_axis_name: value
  y_axis_type: number
  serieses:
    - {name: user 1, x: time, y: value}
    - {name: user 2, x: time, y: value}
    - {name: user 3, x: time, y: value}
  series_mapping_rule:
    - {column: name, value: user 1, series: user 1} # name 列の値が `user 1` であれば、シリーズ `user 1` のデータに登録する
    - {column: name, value: user 2, series: user 2}
    - {column: name, value: user 3, series: user 3}
```

![](https://raw.githubusercontent.com/mikoto2000/embulk-output-chart/images/doc/image/line.png)


## Build

```
$ ./gradlew gem  # -t to watch change of files and rebuild continuously
```
