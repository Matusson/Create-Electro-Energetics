package com.george_vi.electroenergetics.simulation.electrical_properties;

import com.george_vi.electroenergetics.simulation.util.SparseMatrix;

public abstract class NonlinearProperties extends ElectricalProperties {
    NonLinearInvertedElectricalProperties inverted = null;

    @Override
    public final double resistance() {
        return 1;
    }

    @Override
    public double conductance() {
        return 0;
    }

    @Override
    public final boolean canDissolve() {
        return false;
    }

    @Override
    public final ElectricalProperties invert() {
        if (inverted == null)
            return inverted = new NonLinearInvertedElectricalProperties(this);
        return inverted;
    }

    public abstract void stampNonLinear(double v1, double v2, SparseMatrix matrix, double[] rhs, int n1, int n2);
}
