package com.george_vi.electroenergetics.simulation.electrical_properties;

import java.util.Arrays;

public class AdvancedParallelDissolvedProperties extends MicroTickingElectricalProperties implements IDissolvedProperties {
    public final ElectricalProperties[] originalProperties;
    public final int node1;
    public final int node2;

    public AdvancedParallelDissolvedProperties(ElectricalProperties[] originalProperties,
                                               int node1, int node2) {
        this.node1 = node1;
        this.node2 = node2;
        double conductance = Arrays.stream(originalProperties).mapToDouble(ElectricalProperties::conductance).sum();
        this.resistance = conductance == 0 ? 1e+11d : 1 / conductance;
        this.originalProperties = originalProperties;
    }

    @Override
    public void getVoltages(double v1, double v2, double[] toFill, int microTick, int totalMicroTicks) {

    }

    @Override
    public void tick(double[] allVoltages, int microTick, int totalMicroTicks, int n1, int n2) {
        double conductance = 0;
        double currentSource = 0;
        for (ElectricalProperties originalProperty : originalProperties) {
            switch (originalProperty) {
                case MicroTickingElectricalProperties microTicking ->
                        microTicking.tick(allVoltages, microTick, totalMicroTicks, node1, node2);
                case MicroTickingInvertedElectricalProperties inv ->
                        inv.original.tick(allVoltages, microTick, totalMicroTicks, node1, node2);
                default -> {}
            }

            conductance += originalProperty.conductance();
            currentSource += originalProperty.currentSource();
        }
        this.resistance = conductance == 0 ? 1e+11d : 1 / conductance;
        this.currentSource = currentSource;
    }

    @Override
    public void afterTick(double[] allVoltages, int n1, int n2, int microTick, int totalMicroTicks) {
        for (ElectricalProperties originalProperty : originalProperties) {
            switch (originalProperty) {
                case MicroTickingElectricalProperties microTicking ->
                        microTicking.afterTick(allVoltages, node1, node2, microTick, totalMicroTicks);
                case MicroTickingInvertedElectricalProperties inv ->
                        inv.original.afterTick(allVoltages, node2, node1, microTick, totalMicroTicks);
                case null, default -> {}
            }
        }
    }
}
