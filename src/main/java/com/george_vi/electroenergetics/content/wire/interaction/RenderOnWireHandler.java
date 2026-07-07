package com.george_vi.electroenergetics.content.wire.interaction;


import com.george_vi.electroenergetics.client.ElectricPropertiesOverlay;
import com.george_vi.electroenergetics.client.WireRenderer;
import com.george_vi.electroenergetics.foundation.QuadraticWireHelper;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNodeConnection;
import com.george_vi.electroenergetics.foundation.nodes.NodeConnectionPoint;
import com.george_vi.electroenergetics.simulation.infrastructure.WireData;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.math.VecHelper;
import net.createmod.catnip.outliner.AABBOutline;
import net.createmod.catnip.outliner.LineOutline;
import net.createmod.catnip.outliner.Outline;
import net.createmod.catnip.outliner.Outliner;
import net.createmod.catnip.render.SuperRenderTypeBuffer;
import net.createmod.catnip.theme.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Function;

public class RenderOnWireHandler {
    public static void renderCurrent(float amperage) {
        ElectricPropertiesOverlay.INSTANCE.setAmmeter(Math.abs(amperage));
        NodeConnectionPoint point = WireInteractionHandler.targetedPoint;
        if (point == null || Math.abs(amperage) < 1e-2d)
            return;
        Level level = Minecraft.getInstance().level;
        InWorldNode node1 = point.node1();
        InWorldNode node2 = point.node2();
        WireData wireData = WireRenderer.getConnectionData(new InWorldNodeConnection(node1, node2));
        Vec3 pos1 = node1.getPositionNoSable(level);
        Vec3 pos2 = node2.getPositionNoSable(level);
        Positions pos = new Positions(pos1, pos2);
        double distance = pos.getDistance();
        if (distance == 0)
            return;
        for (int i = 0; i < 4; i++) {
            int ticks = AnimationTickHolder.getTicks();
            int page = (int) Math.floor((ticks + 6 * i) / 24d);
            int dotID = page << 2 + i;
            float progress = ((ticks + 6 * i) % 24) / 24f;

            float pointOnWire = (float) (((point.point() * distance) + ((amperage > 0 ? progress : 1.0 - progress) * 1.2f) - 0.6f) / distance);
            OutlinerExt.chaseAABBOnWire("cee_clamp_meter_current_visualization_" + dotID, AABB.ofSize(Vec3.ZERO, 0.01, 0.01, 0.01),
                            pos, pointOnWire > 1 ? 1 : pointOnWire < 0 ? 0 : pointOnWire, wireData == null ? 0 : wireData.getSag(distance))
                    .lineWidth(0.15f * Math.min(1, progress * 4))
                    .colored(Color.SPRING_GREEN)
                    .disableLineNormals();
        }
    }

    public static class OutlinerExt {
        static Map<String, WireOutline> wireOutlines = Collections.synchronizedMap(new HashMap<>());

        public static Outline.OutlineParams chaseAABBOnWire(Object slot, AABB bb, Positions pos, float point, float sag) {
            Outliner instance = Outliner.getInstance();
            Map<Object, Outliner.OutlineEntry> outlines = instance.getOutlines();
            if (!outlines.containsKey(slot) || !(outlines.get(slot).getOutline() instanceof AABBOutline)) {
                instance.showOutline(slot, new ChasingAABBOutlineOnWire(bb, pos, point, sag));
            }

            ChasingAABBOutlineOnWire outline = (ChasingAABBOutlineOnWire) outlines.get(slot).getOutline();
            instance.keep(slot);

            outline.targetPoint = point;
            outline.sag = sag;
            outline.pos = pos;
            outline.setSizeFromAABB(bb);
            return outline.getParams();
        }

        public static void showWireOutline(String slot, Positions pos, float sag, float detail,
                                           Function<Outline.OutlineParams, Outline.OutlineParams> processor) {
            if (!wireOutlines.containsKey(slot)) {
                wireOutlines.put(slot, new WireOutline(slot));
            }
            WireOutline wire = wireOutlines.get(slot);
            wire.ttl = 5; // keep it alive during using it

            wire.show(pos, sag, detail, processor);
        }

        @OnlyIn(Dist.CLIENT)
        public static void tick() { // clear
            ArrayList<String> toBeRemoved = new ArrayList<>();
            for (Map.Entry<String, WireOutline> entry : wireOutlines.entrySet()) {
                int ttl = entry.getValue().ttl--;
                if (ttl <= 0) toBeRemoved.add(entry.getKey());
            }
            toBeRemoved.forEach((slot) -> {
                wireOutlines.get(slot).clear();
                wireOutlines.remove(slot);
            });
        }
    }

    public static class Positions { // Position Utilities
        Vec3 pos1, pos2;
        SubLevelAccess sl1, sl2; // SLA cache

        public Positions(Vec3 pos1, Vec3 pos2) {
            setPos(pos1, pos2);
        }

        public static Vec3 toPositionWithSable(Vec3 pos, SubLevelAccess subLevelAccess, float pt) {
            if (subLevelAccess == null) {
                return pos;
            }
            Pose3dc logicalPose = subLevelAccess.logicalPose();
            Pose3dc lastPose = subLevelAccess.lastPose();

            Vec3 lastPos = lastPose.transformPosition(pos);
            Vec3 logicalPos = logicalPose.transformPosition(pos);

            return VecHelper.lerp(pt, lastPos, logicalPos);
        }

        public static Vec3 toPositionWithSable(Vec3 pos, SubLevelAccess subLevelAccess) {
            if (subLevelAccess == null) {
                return pos;
            }
            return subLevelAccess.logicalPose().transformPosition(pos);
        }

        public void setPos(Vec3 pos1, Vec3 pos2) {
            Level level = Minecraft.getInstance().level;
            sl1 = SableCompanion.INSTANCE.getContaining(level, pos1);
            sl2 = SableCompanion.INSTANCE.getContaining(level, pos2);
            this.pos1 = pos1;
            this.pos2 = pos2;
        }

        public Vec3 getPos1Sable() {
            return toPositionWithSable(pos1, sl1);
        }

        public Vec3 getPos2Sable() {
            return toPositionWithSable(pos2, sl2);
        }

        public double getDistance() {
            return getPos1Sable().distanceTo(getPos2Sable());
        }

        public Vec3 getPos1Sable(float pt) {
            return toPositionWithSable(pos1, sl1, pt);
        }

        public Vec3 getPos2Sable(float pt) {
            return toPositionWithSable(pos2, sl2, pt);
        }

        public double getDistance(float pt) {
            return getPos1Sable(pt).distanceTo(getPos2Sable(pt)); // may need it in the future
        }
    }

    public static class ChasingAABBOutlineOnWire extends AABBOutline {
        protected Vec3 bbSize;
        protected float targetPoint, sag; // Arguments for position computing
        protected Positions pos;
        private float prevPoint;

        public ChasingAABBOutlineOnWire(AABB bb, Positions pos, float prev, float sag) {
            super(AABB.ofSize(Vec3.ZERO, 0.01, 0.01, 0.01)); // avoid to be detected by sable mixin
            setSizeFromAABB(bb);

            this.pos = pos;
            this.sag = sag;
            targetPoint = prev;
            prevPoint = prev;
        }

        public void setSizeFromAABB(AABB bb){
            bbSize = bb.getMaxPosition().subtract(bb.getMinPosition());
        }

        @Override
        public void render(@NotNull PoseStack ms, @NotNull SuperRenderTypeBuffer buffer, @NotNull Vec3 camera, float pt) {
            // avoid offsets caused by sublevel
            Vec3 currentPos = QuadraticWireHelper.posAt(pos.getPos1Sable(pt), pos.getPos2Sable(pt), Mth.lerp(pt, prevPoint, targetPoint), sag);
            setBounds(AABB.ofSize(currentPos, bbSize.x, bbSize.y, bbSize.z));
            super.render(ms, buffer, camera, pt);
        }

        @Override
        public void tick() {
            prevPoint = targetPoint;
        }
    }

    public static class WireOutline {
        private static final int SECTION_STRETCHING_BUFFER = 8;// I think it's enough to deal with stretching
        private float sag, detail, pt = -1; // keep it updated
        private Positions pos;
        private List<Vec3> cachedPoints; // cache for same frame
        private int sectionCount = 0; // count for resource clear
        protected int ttl = 5;// Life Count
        public final String slot;

        public WireOutline(String slot) {
            this.slot = slot;
        }

        protected void clear() {
            Outliner instance = Outliner.getInstance();
            for (int i = 0; i < sectionCount; i++) {
                instance.remove(slot + i);
            }
        }

        public void show(Positions pos, float sag, float detail, Function<Outline.OutlineParams, Outline.OutlineParams> processor) {
            this.sag = sag;
            this.detail = detail;
            this.pos = pos;
            List<Vec3> points = QuadraticWireHelper.cablePoints(pos.getPos1Sable(), pos.getPos2Sable(), sag, detail);
            for (int i = 0; i < points.size() + SECTION_STRETCHING_BUFFER; i++) {
                Outline.OutlineParams params = addSection(slot + i, i);
                if (processor != null) {
                    processor.apply(params); // apply styles
                }
            }
            sectionCount = Math.max(sectionCount, points.size() + SECTION_STRETCHING_BUFFER);
        }

        private Outline.OutlineParams addSection(Object slot, int index) {
            Outliner instance = Outliner.getInstance();
            Map<Object, Outliner.OutlineEntry> outlines = instance.getOutlines();
            if (!outlines.containsKey(slot)) {
                WireOutlineSection outline = new WireOutlineSection(index);
                instance.showOutline(slot, outline);
            }
            Outliner.OutlineEntry entry = outlines.get(slot);
            instance.keep(slot);
            return entry.getOutline().getParams();
        }

        private Vec3 getPoint(int index, float pt) {
            if (Float.compare(pt, this.pt) != 0) {
                cachedPoints = QuadraticWireHelper.cablePoints(pos.getPos1Sable(pt), pos.getPos2Sable(pt), sag, detail);
                cachedPoints.add(pos.pos2);
                this.pt = pt;
            }
            return index < cachedPoints.size() ? cachedPoints.get(index) : null;
        }

        public class WireOutlineSection extends LineOutline {
            public final int index;

            public WireOutlineSection(int index) {
                this.index = index;
            }

            @Override
            public void render(@NotNull PoseStack ms, @NotNull SuperRenderTypeBuffer buffer, @NotNull Vec3 camera, float pt) {
                Vec3 pos1 = getPoint(index, pt);
                Vec3 pos2 = getPoint(index + 1, pt);
                if (pos1 == null || pos2 == null) {
                    return;
                }
                set(pos1, pos2);
                super.render(ms, buffer, camera, pt);
            }
        }
    }
}
