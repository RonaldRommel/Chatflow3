package com.chatflow.client.analyzer;

import com.chatflow.client.model.MessageMetrics;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ChartGenerator {

    public void generateThroughputChart(List<MessageMetrics> metrics) {
        // Group by 10-second buckets
        Map<Long, Long> buckets = metrics.stream()
                .collect(Collectors.groupingBy(
                        m -> m.getSendTimestamp().toEpochMilli() / 10000, // 10-second buckets
                        Collectors.counting()
                ));

        // Convert to throughput (messages per second)
        XYSeries series = new XYSeries("Throughput");
        buckets.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    long time = entry.getKey();
                    double throughput = entry.getValue() / 10.0; // per second
                    series.add(time, throughput);
                });

        XYSeriesCollection dataset = new XYSeriesCollection(series);

        JFreeChart chart = ChartFactory.createXYLineChart(
                "Throughput Over Time",
                "Time (10s buckets)",
                "Messages/Second",
                dataset
        );

        try {
            ChartUtils.saveChartAsPNG(
                    new File("results/throughput.png"),
                    chart,
                    800,
                    600
            );
            System.out.println("Chart saved to results/throughput.png");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}