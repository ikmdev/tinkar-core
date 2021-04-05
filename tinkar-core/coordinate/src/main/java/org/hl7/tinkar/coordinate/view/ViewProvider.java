package org.hl7.tinkar.coordinate.view;

import org.hl7.tinkar.coordinate.stamp.StampCalculator;

public class ViewProvider implements View {

    final StampCalculator calculator;

    private ViewProvider(StampCalculator calculator) {
        this.calculator = calculator;
    }

    public static View make(StampCalculator calculator) {
        return new ViewProvider(calculator);
    }

}
