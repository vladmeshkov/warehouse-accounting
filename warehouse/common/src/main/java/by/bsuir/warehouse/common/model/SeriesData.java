package by.bsuir.warehouse.common.model;

import java.io.Serializable;
import java.util.List;

public class SeriesData implements Serializable {
    private static final long serialVersionUID = 1L;
    private String name;
    private List<ChartDataPoint> dataPoints;

    public SeriesData() {}
    public SeriesData(String name, List<ChartDataPoint> dataPoints) { this.name = name; this.dataPoints = dataPoints; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public List<ChartDataPoint> getDataPoints() { return dataPoints; }
    public void setDataPoints(List<ChartDataPoint> dataPoints) { this.dataPoints = dataPoints; }
}