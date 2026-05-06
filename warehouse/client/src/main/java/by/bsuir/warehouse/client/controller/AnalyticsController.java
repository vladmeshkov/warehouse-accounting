package by.bsuir.warehouse.client.controller;

import by.bsuir.warehouse.client.network.ClientContext;
import by.bsuir.warehouse.common.model.AnalysisChart;
import by.bsuir.warehouse.common.model.ChartDataPoint;
import by.bsuir.warehouse.common.protocol.Action;
import by.bsuir.warehouse.common.protocol.Request;
import by.bsuir.warehouse.common.protocol.Response;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;

public class AnalyticsController {

    @FXML private VBox chartContainer;
    @FXML private Label statusLabel;

    private List<AnalysisChart> allCharts;
    private final List<VBox> chartCards = new ArrayList<>();

    @FXML
    public void initialize() {
        loadAnalytics();
    }

    private void loadAnalytics() {
        statusLabel.setText("Загрузка аналитики...");
        new Thread(() -> {
            try {
                Response resp = ClientContext.getInstance()
                        .send(new Request.Builder(Action.GET_ANALYTICS).build());
                Platform.runLater(() -> {
                    if (resp.isSuccess() && resp.getData() instanceof List<?> raw) {
                        allCharts = (List<AnalysisChart>) raw;
                        renderAllCharts();
                        statusLabel.setText("Готово");
                    } else {
                        statusLabel.setText("Ошибка загрузки данных");
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> statusLabel.setText("Ошибка соединения"));
            }
        }).start();
    }

    private void renderAllCharts() {
        chartContainer.getChildren().clear();
        chartCards.clear();
        if (allCharts == null || allCharts.isEmpty()) {
            chartContainer.getChildren().add(new Label("Нет данных для отображения"));
            return;
        }
        for (AnalysisChart chart : allCharts) {
            VBox card = createChartCard(chart);
            chartCards.add(card);
            chartContainer.getChildren().add(card);
        }
    }

    private VBox createChartCard(AnalysisChart chart) {
        VBox card = new VBox(8);
        card.setStyle("-fx-background-color: white; -fx-padding: 16; -fx-background-radius: 12;"
                + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.05), 6, 0, 0, 2);");
        card.setAlignment(Pos.CENTER);

        Label titleLabel = new Label(chart.getTitle());
        titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");

        VBox chartDisplay = new VBox();
        chartDisplay.setAlignment(Pos.CENTER);
        chartDisplay.getChildren().add(buildChart(chart, chart.getRecommendedType()));

        HBox toggleButtons = new HBox(8);
        toggleButtons.setAlignment(Pos.CENTER);

        Button pieBtn = new Button("🥧 Круговая");
        Button barBtn = new Button("📊 Гистограмма");
        Button lineBtn = new Button("📈 Линейный");

        pieBtn.setStyle("-fx-background-color: #ecf0f1; -fx-text-fill: #2c3e50; -fx-background-radius: 4;"
                + "-fx-cursor: hand; -fx-padding: 6 12 6 12;");
        barBtn.setStyle("-fx-background-color: #ecf0f1; -fx-text-fill: #2c3e50; -fx-background-radius: 4;"
                + "-fx-cursor: hand; -fx-padding: 6 12 6 12;");
        lineBtn.setStyle("-fx-background-color: #ecf0f1; -fx-text-fill: #2c3e50; -fx-background-radius: 4;"
                + "-fx-cursor: hand; -fx-padding: 6 12 6 12;");

        pieBtn.setOnAction(e -> {
            chartDisplay.getChildren().clear();
            chartDisplay.getChildren().add(buildChart(chart, "PIE"));
            highlightActiveButton(pieBtn, barBtn, lineBtn);
        });
        barBtn.setOnAction(e -> {
            chartDisplay.getChildren().clear();
            chartDisplay.getChildren().add(buildChart(chart, "BAR"));
            highlightActiveButton(barBtn, pieBtn, lineBtn);
        });
        lineBtn.setOnAction(e -> {
            chartDisplay.getChildren().clear();
            chartDisplay.getChildren().add(buildChart(chart, "LINE"));
            highlightActiveButton(lineBtn, pieBtn, barBtn);
        });

        String rec = chart.getRecommendedType();
        if ("PIE".equalsIgnoreCase(rec)) highlightActiveButton(pieBtn, barBtn, lineBtn);
        else if ("BAR".equalsIgnoreCase(rec)) highlightActiveButton(barBtn, pieBtn, lineBtn);
        else highlightActiveButton(lineBtn, pieBtn, barBtn);

        toggleButtons.getChildren().addAll(pieBtn, barBtn, lineBtn);
        card.getChildren().addAll(titleLabel, chartDisplay, toggleButtons);
        return card;
    }

    private void highlightActiveButton(Button active, Button... others) {
        active.setStyle("-fx-background-color: #2980b9; -fx-text-fill: white; -fx-background-radius: 4;"
                + "-fx-cursor: hand; -fx-padding: 6 12 6 12;");
        for (Button btn : others) {
            btn.setStyle("-fx-background-color: #ecf0f1; -fx-text-fill: #2c3e50; -fx-background-radius: 4;"
                    + "-fx-cursor: hand; -fx-padding: 6 12 6 12;");
        }
    }

    private String trimLabel(String label, int maxLen) {
        if (label == null || label.isEmpty()) return "—";
        if (label.length() <= maxLen) return label;
        return label.substring(0, maxLen - 2) + "…";
    }

    private Chart buildChart(AnalysisChart chart, String type) {
        List<ChartDataPoint> points = chart.getData();
        if (points.isEmpty() && chart.getSecondSeries() == null) {
            PieChart empty = new PieChart();
            empty.setTitle(chart.getTitle() + " (нет данных)");
            empty.setPrefSize(450, 300);
            return empty;
        }

        if ("Финансовые потоки".equals(chart.getTitle()) && "LINE".equalsIgnoreCase(type)) {
            return buildFinancialFlowChart(chart);
        }

        if ("PIE".equalsIgnoreCase(type)) {
            PieChart pie = new PieChart();
            pie.setTitle(chart.getTitle());
            for (ChartDataPoint p : points) {
                pie.getData().add(new PieChart.Data(p.getLabel(), p.getValue()));
            }
            pie.setPrefSize(450, 300);
            return pie;
        } else {
            CategoryAxis xAxis = new CategoryAxis();
            NumberAxis yAxis = new NumberAxis();
            xAxis.setLabel("Категория");
            yAxis.setLabel("Значение");
            xAxis.setTickLabelFont(javafx.scene.text.Font.font(10));
            xAxis.setTickLabelRotation(20);

            if ("LINE".equalsIgnoreCase(type)) {
                LineChart<String, Number> lineChart = new LineChart<>(xAxis, yAxis);
                lineChart.setTitle(chart.getTitle());
                XYChart.Series<String, Number> series = new XYChart.Series<>();
                series.setName(chart.getTitle());
                for (ChartDataPoint p : points) {
                    String shortLabel = trimLabel(p.getLabel(), 18);
                    series.getData().add(new XYChart.Data<>(shortLabel, p.getValue()));
                }
                lineChart.getData().add(series);
                lineChart.setPrefSize(550, 320);
                return lineChart;
            } else {
                BarChart<String, Number> barChart = new BarChart<>(xAxis, yAxis);
                barChart.setTitle(chart.getTitle());
                XYChart.Series<String, Number> series = new XYChart.Series<>();
                series.setName(chart.getTitle());
                for (ChartDataPoint p : points) {
                    String shortLabel = trimLabel(p.getLabel(), 18);
                    series.getData().add(new XYChart.Data<>(shortLabel, p.getValue()));
                }
                barChart.getData().add(series);
                barChart.setPrefSize(550, 320);
                return barChart;
            }
        }
    }

    private LineChart<String, Number> buildFinancialFlowChart(AnalysisChart chart) {
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        xAxis.setLabel("Дата");
        yAxis.setLabel("Сумма, руб.");
        xAxis.setTickLabelFont(javafx.scene.text.Font.font(10));
        xAxis.setTickLabelRotation(20);

        LineChart<String, Number> lineChart = new LineChart<>(xAxis, yAxis);
        lineChart.setTitle("Финансовые потоки");

        XYChart.Series<String, Number> incomeSeries = new XYChart.Series<>();
        incomeSeries.setName("Доходы");
        for (ChartDataPoint p : chart.getData()) {
            String shortLabel = trimLabel(p.getLabel(), 18);
            incomeSeries.getData().add(new XYChart.Data<>(shortLabel, p.getValue()));
        }

        XYChart.Series<String, Number> outcomeSeries = new XYChart.Series<>();
        outcomeSeries.setName("Расходы");
        if (chart.getSecondSeries() != null) {
            for (ChartDataPoint p : chart.getSecondSeries()) {
                String shortLabel = trimLabel(p.getLabel(), 18);
                outcomeSeries.getData().add(new XYChart.Data<>(shortLabel, p.getValue()));
            }
        }

        lineChart.getData().addAll(incomeSeries, outcomeSeries);
        lineChart.setPrefSize(550, 320);
        return lineChart;
    }
}