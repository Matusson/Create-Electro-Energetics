package com.george_vi.electroenergetics.simulation.electrical_properties;

public final class NonLinearInvertedElectricalProperties extends ElectricalProperties {
    public NonlinearProperties original;
    public NonLinearInvertedElectricalProperties(NonlinearProperties original) {
        this.original = original;
    }

    @Override
    public double resistance() {
        return original.resistance();
    }

    @Override
    public double conductance() {
        return original.conductance();
    }

    @Override
    public double voltageSource() {
        return -original.voltageSource();
    }

    @Override
    public double currentSource() {
        return -original.currentSource();
    }

    @Override
    public boolean isVoltageSource() {
        return original.isVoltageSource();
    }

    @Override
    public boolean isCurrentSource() {
        return original.isCurrentSource();
    }

    @Override
    public ElectricalProperties invert() {
        return original;
    }

    @Override
    public boolean canDissolve() {
        return false;
    }
}
