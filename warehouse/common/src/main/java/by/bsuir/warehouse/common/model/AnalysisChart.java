package by.bsuir.warehouse.common.model;

import java.io.Serializable;
import java.util.List;

public class AnalysisChart implements Serializable {
    private static final long serialVersionUID = 1L;

    private String title;
    private String recommendedType; // "PIE", "LINE", "BAR"
    private List<ChartDataPoint> data;
    private List<ChartDataPoint> secondSeries; // для графиков с двумя сериями

    public AnalysisChart() {}

    public AnalysisChart(String title, String recommendedType, List<ChartDataPoint> data) {
        this.title = title;
        this.recommendedType = recommendedType;
        this.data = data;
    }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getRecommendedType() { return recommendedType; }
    public void setRecommendedType(String recommendedType) { this.recommendedType = recommendedType; }

    public List<ChartDataPoint> getData() { return data; }
    public void setData(List<ChartDataPoint> data) { this.data = data; }

    public List<ChartDataPoint> getSecondSeries() { return secondSeries; }
    public void setSecondSeries(List<ChartDataPoint> secondSeries) { this.secondSeries = secondSeries; }
}