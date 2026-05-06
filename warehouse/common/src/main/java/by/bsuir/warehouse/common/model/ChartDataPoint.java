package by.bsuir.warehouse.common.model;

import java.io.Serializable;

public class ChartDataPoint implements Serializable {
    private static final long serialVersionUID = 1L;
    private String label;
    private double value;

    public ChartDataPoint() {}
    public ChartDataPoint(String label, double value) { this.label = label; this.value = value; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public double getValue() { return value; }
    public void setValue(double value) { this.value = value; }
}