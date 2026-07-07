package com.george_vi.electroenergetics.foundation;


import com.george_vi.electroenergetics.client.ElectricPropertiesOverlay;
import com.george_vi.electroenergetics.client.WireRenderer;
import com.george_vi.electroenergetics.content.wire.interaction.WireInteractionHandler;
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
import net.createmod.catnip.outliner.Outline;
import net.createmod.catnip.outliner.Outliner;
import net.createmod.catnip.render.SuperRenderTypeBuffer;
import net.createmod.catnip.theme.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class RenderHelper {
    public static void renderCurrent(float amperage) {
        ElectricPropertiesOverlay.INSTANCE.setAmmeter(Math.abs(amperage));
        NodeConnectionPoint point = WireInteractionHandler.targetedPoint;
        if (point == null || Math.abs(amperage) < 1e-2d)
            return;
        Level level = Minecraft.getInstance().level;
        WireData wireData = WireRenderer.getConnectionData(new InWorldNodeConnection(point.node1(), point.node2()));

        Vec3 pos1 = point.node1().getPositionNoSable(level);
        Vec3 pos2 = point.node2().getPositionNoSable(level);
        Vec3 pos1s = point.node1().getPosition(level);
        Vec3 pos2s = point.node1().getPosition(level);
        if(pos1s == null || pos2s == null){
            return;
        }
        double distance = pos1s.distanceTo(pos2s);
        if (distance == 0)
            return;
        for (int i = 0; i < 4; i++) {
            int ticks = AnimationTickHolder.getTicks();
            int page = (int) Math.floor((ticks + 6 * i) / 24d);
            int dotID = page << 2 + i;

            float progress = ((ticks + 6 * i) % 24) / 24f;

            float pointOnWire = (float) (((point.point() * distance) + ((amperage > 0 ? progress : 1.0 - progress) * 1.2f) - 0.6f) / distance);
            RenderHelper.chaseAABBOnWire("cee_clamp_meter_current_visualization_" + dotID, AABB.ofSize(Vec3.ZERO, 0.01, 0.01, 0.01),
                            pos1, pos2, pointOnWire > 1 ? 1 : pointOnWire < 0 ? 0 : pointOnWire, wireData == null ? 0 : wireData.getSag(distance))
                    .lineWidth(0.15f * Math.min(1, progress * 4))
                    .colored(Color.SPRING_GREEN)
                    .disableLineNormals();
        }
    }

    public static Vec3 toPositionWithSable(SubLevelAccess subLevelAccess, Vec3 pos, float pt) {
        if (subLevelAccess == null) {
            return pos;
        }
        Pose3dc logicalPose = subLevelAccess.logicalPose();
        Pose3dc lastPose = subLevelAccess.lastPose();

        Vec3 lastPos = lastPose.transformPosition(pos);
        Vec3 logicalPos = logicalPose.transformPosition(pos);

        return VecHelper.lerp(pt, lastPos, logicalPos);
    }

    public static Outline.OutlineParams chaseAABBOnWire(Object slot, AABB bb, Vec3 pos1, Vec3 pos2, float point, float sag) {
        Outliner instance = Outliner.getInstance();
        Map<Object, Outliner.OutlineEntry> outlines = instance.getOutlines();
        if (!outlines.containsKey(slot) || !(outlines.get(slot).getOutline() instanceof AABBOutline)) {
            instance.showOutline(slot, new ChasingAABBOutlineOnWire(bb, point));
        }

        ChasingAABBOutlineOnWire outline = (ChasingAABBOutlineOnWire) outlines.get(slot).getOutline();
        instance.keep(slot);

        outline.setPos(pos1, pos2);
        outline.targetPoint = point;
        outline.sag = sag;

        return outline.getParams();
    }

    public static class ChasingAABBOutlineOnWire extends AABBOutline {
        Vec3 pos1, pos2, bbSize;
        float prevPoint, targetPoint, sag; // Arguments for position computing
        SubLevelAccess sl1, sl2; // SLA cache

        public ChasingAABBOutlineOnWire(AABB bb, float prev) {
            super(AABB.ofSize(Vec3.ZERO, 0.01, 0.01, 0.01)); // avoid to be detected by sable mixin
            bbSize = bb.getMaxPosition().subtract(bb.getMinPosition());

            setPos(Vec3.ZERO, Vec3.ZERO);
            targetPoint = sag = 0;
            prevPoint = prev;
        }

        public void setPos(Vec3 pos1, Vec3 pos2) {
            Level level = Minecraft.getInstance().level;
            sl1 = SableCompanion.INSTANCE.getContaining(level, pos1);
            sl2 = SableCompanion.INSTANCE.getContaining(level, pos2);
            this.pos1 = pos1;
            this.pos2 = pos2;
        }

        @Override
        public void render(@NotNull PoseStack ms, @NotNull SuperRenderTypeBuffer buffer, @NotNull Vec3 camera, float pt) {

            Vec3 pos1s = toPositionWithSable(sl1, pos1, pt);
            Vec3 pos2s = toPositionWithSable(sl2, pos2, pt);

            // avoid offsets caused by sublevel
            Vec3 currentPos = QuadraticWireHelper.posAt(pos1s, pos2s, Mth.lerp(pt, prevPoint, targetPoint), sag);
            setBounds(AABB.ofSize(currentPos, bbSize.x, bbSize.y, bbSize.z));
            super.render(ms, buffer, camera, pt);
        }

        @Override
        public void tick() {
            prevPoint = targetPoint;
        }
    }
}
