package com.george_vi.electroenergetics.foundation.electrical_properties;

import com.george_vi.electroenergetics.simulation.electrical_properties.NonlinearProperties;
import com.george_vi.electroenergetics.simulation.util.SparseMatrix;

public class DiodeProperties extends NonlinearProperties {

    public final double thermalVoltage;
    public final double saturationCurrent;
    private double prevVoltage;

    public DiodeProperties(double thermalVoltage, double saturationCurrent) {
        this.thermalVoltage = thermalVoltage;
        this.saturationCurrent = saturationCurrent;
    }

    @Override
    public void stampNonLinear(double v1, double v2, SparseMatrix matrix, double[] rhs, int n1, int n2, boolean first) {
        if (!first)
            prevVoltage = v1 - v2;
        double vd = prevVoltage;

        double expVal = safeExp(vd / thermalVoltage);

        double Id = saturationCurrent * (expVal - 1.0);

        double gd = (saturationCurrent / thermalVoltage) * expVal;

        double Ieq = Id - gd * vd;

        double gMin = 1e-8d;
        matrix.add(n1, n1, gd + gMin);
        matrix.add(n2, n2, gd + gMin);
        matrix.add(n1, n2, -gd);
        matrix.add(n2, n1, -gd);

        rhs[n1] -= Ieq;
        rhs[n2] += Ieq;
    }

    /**
     * exp() can return extremely large values for seemingly small values.
     * Simply clamping the argument results in the derivative being non-continuous,
     * which is terrible for Newton iteration.
     * <br>
     * This implementation doesn't clamp the result, instead it smooths it out for higher values,
     * while keeping the derivative continuous.
     */
    double safeExp(double x) {
        double limit = 13.0;

        if (x <= limit)
            return Math.exp(x);

        double dx = x - limit;

        return Math.exp(limit) * (1 + dx + 0.5 * dx * dx);
    }
}
