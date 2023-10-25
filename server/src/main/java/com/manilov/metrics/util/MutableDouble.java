package com.manilov.metrics.util;

public class MutableDouble extends Number{

    public MutableDouble(double value) {
        this.value = value;
    }

    private double value;

    @Override
    public int intValue() {
        return (int) value;
    }

    @Override
    public long longValue() {
        return (long) value;
    }

    @Override
    public float floatValue() {
        return (float) value;
    }

    @Override
    public double doubleValue() {
        return (double) value;
    }

    public void setValue(double value) {
        this.value = value;
    }

    public double getValue(double value) {
        return value;
    }

    @Override
    public String toString() {
        return Double.toString(value);
    }
}
