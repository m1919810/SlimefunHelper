package me.matl114.hacks.utils.render;

import static me.matl114.utils.RenderUtils.*;

import java.awt.*;
import java.util.List;
import java.util.function.Function;
import me.matl114.events.RenderListener;
import me.matl114.utils.ChatUtils;
import me.matl114.utils.RenderUtils;
import me.matl114.utils.collections.IndexEntry;
import me.matl114.utils.render.RenderCollector;
import me.matl114.versioned.api.VDrawContext;
import me.matl114.versioned.api.VRender;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector2d;

public class RenderCollectors {
    public static final MinecraftClient mc = MinecraftClient.getInstance();

    public static RenderCollector<Box> createBoxCollector(
            boolean drawOutline, boolean drawSolid, boolean drawTraceLine) {
        return new RenderCollector.Impl<Box>() {
            @Override
            public void render3D(MatrixStack matrices) {
                if (entries.isEmpty()) return;
                Vec3d cameraPos = getCameraPos().negate();
                if (drawSolid) {
                    VRender.getInstance()
                            .createQuadsLayer(
                                    (operation, vertexConsumer) -> {
                                        if (!entries.isEmpty()) {
                                            for (IndexEntry<Box> boxEntry : entries) {
                                                var box = boxEntry.val().offset(cameraPos);
                                                operation.drawSolidBoxQuad(
                                                        matrices,
                                                        vertexConsumer,
                                                        box.getMinPos(),
                                                        box.getMaxPos(),
                                                        boxEntry.index());
                                            }
                                        }
                                    },
                                    true);
                }
                if (drawOutline || drawTraceLine) {
                    VRender.getInstance().createLinesLayer((op, vtx) -> {
                        if (drawOutline) {
                            for (var re : entries) {
                                var box = re.val().offset(cameraPos);
                                op.drawOutlinedBox(matrices, vtx, box.getMinPos(), box.getMaxPos(), re.index());
                            }
                        }
                        if (drawTraceLine) {
                            Vec3d traceOrigin = RenderUtils.getTracerOrigin(0.0F);
                            for (var re : entries) {
                                var box = re.val().getCenter().add(cameraPos);
                                op.drawLine(matrices, vtx, traceOrigin, box, re.index());
                            }
                        }
                    });
                }
            }

            @Override
            public void render2D(VDrawContext vdraw) {
                if (entries.isEmpty()) return;
                Matrix4f camMatrix = RenderListener.getWorldModelViewMatrix();
                Matrix4f projMatrix = RenderListener.getWorldBasicProjectionMatrix();
                if (drawOutline || drawSolid) {

                    Function<Vec3d, Vector2d> projector = RenderUtils.createProjector(camMatrix, projMatrix, true);

                    for (IndexEntry<Box> entry : entries) {
                        Box box = entry.val();
                        Vec3d min = box.getMinPos();
                        Vec3d max = box.getMaxPos();

                        // 8 个顶点的局部偏移（在 Box 坐标系中）
                        double[] xs = {min.x, max.x};
                        double[] ys = {min.y, max.y};
                        double[] zs = {min.z, max.z};

                        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
                        double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
                        boolean anyNoVisible = false;

                        // 遍历 8 个顶点，投影并取极值
                        forEach:
                        for (double x : xs) {
                            for (double y : ys) {
                                for (double z : zs) {
                                    Vec3d worldPos = new Vec3d(x, y, z);
                                    Vector2d screen = projector.apply(worldPos);
                                    if (screen != null) {
                                        if (screen.x < minX) minX = screen.x;
                                        if (screen.x > maxX) maxX = screen.x;
                                        if (screen.y < minY) minY = screen.y;
                                        if (screen.y > maxY) maxY = screen.y;
                                    } else {
                                        anyNoVisible = true;
                                        break forEach;
                                    }
                                }
                            }
                        }

                        if (anyNoVisible) continue; // 完全在相机后方或不可见

                        int color = entry.index(); // 颜色（含 alpha）
                        int x1 = (int) Math.round(minX);
                        int y1 = (int) Math.round(minY);
                        int x2 = (int) Math.round(maxX);
                        int y2 = (int) Math.round(maxY);

                        if (drawSolid) {
                            vdraw.fill(x1, y1, x2, y2, color);
                        }

                        // 绘制边框（如果启用）
                        if (drawOutline) {
                            vdraw.lineGui(x1, y1, x2, y1, color, 0);
                            vdraw.lineGui(x2, y1, x2, y2, color, 0);
                            vdraw.lineGui(x2, y2, x1, y2, color, 0);
                            vdraw.lineGui(x1, y2, x1, y1, color, 0);
                        }
                    }
                }
                if (drawTraceLine) {
                    Function<Vec3d, Vector2d> projector = RenderUtils.createProjector(camMatrix, projMatrix, false);
                    Vector2d screenCenter = RenderUtils.getScreenSize().mul(0.5D, 0.5D);
                    vdraw.getMatrices().pushMatrix();
                    try {
                        vdraw.getMatrices().translate((float) screenCenter.x, (float) screenCenter.y);
                        for (var entry : entries) {
                            Vec3d pos = entry.val().getCenter();
                            int color = entry.index();
                            Vector2d screenPos = projector.apply(pos);
                            if (screenPos != null) {
                                vdraw.lineGuiGradient(
                                        (int) (screenPos.x - screenCenter.x),
                                        (int) (screenPos.y - screenCenter.y),
                                        0,
                                        0,
                                        color,
                                        color,
                                        0);
                            }
                        }
                    } finally {
                        vdraw.getMatrices().popMatrix();
                    }
                }
            }
        };
    }

    public static RenderCollector<Vec3d> createTracerCollector() {
        return new RenderCollector.Impl<Vec3d>() {
            @Override
            public void render3D(MatrixStack matrices) {
                if (entries.isEmpty()) return;
                Vec3d cameraPos = getCameraPos().negate();
                Vec3d traceOrigin = RenderUtils.getTracerOrigin(0.0F);
                VRender.getInstance().createLinesLayer((op, vtx) -> {
                    for (var re : entries) {
                        var box = re.val().add(cameraPos);
                        op.drawLine(matrices, vtx, traceOrigin, box, re.index());
                    }
                });
            }

            @Override
            public void render2D(VDrawContext vdraw) {
                if (entries.isEmpty()) return;
                Matrix4f cam = RenderListener.getWorldModelViewMatrix();
                Matrix4f proj = RenderListener.getWorldBasicProjectionMatrix();
                Function<Vec3d, Vector2d> projector = RenderUtils.createProjector(cam, proj, false);
                Vector2d screenCenter = RenderUtils.getScreenSize().mul(0.5D, 0.5D);
                vdraw.getMatrices().pushMatrix();
                try {
                    vdraw.getMatrices().translate((float) screenCenter.x, (float) screenCenter.y);
                    for (var entry : entries) {
                        Vec3d pos = entry.val();
                        int color = entry.index();
                        Vector2d screenPos = projector.apply(pos);
                        if (screenPos != null) {
                            vdraw.lineGuiGradient(
                                    (int) (screenPos.x - screenCenter.x),
                                    (int) (screenPos.y - screenCenter.y),
                                    0,
                                    0,
                                    color,
                                    color,
                                    0);
                        }
                    }
                } finally {
                    vdraw.getMatrices().popMatrix();
                }
            }
        };
    }

    public static double HEIGHT = 9.0d;

    public static RenderCollector<RenderElements.Text> createTextCollector() {
        return new RenderCollector.Impl<>() {
            @Override
            public void render3D(MatrixStack stack) {
                if (entries.isEmpty()) return;
                Vec3d cameraNeg = getCameraPos().negate();
                for (IndexEntry<RenderElements.Text> entry : entries) {
                    var col = entry.index();
                    var vec3d = entry.val().position();
                    var text = entry.val().text();
                    var delta = vec3d.add(cameraNeg);
                    float scale = entry.val().scale();
                    stack.push();
                    stack.translate(delta.x, delta.y, delta.z);
                    stack.multiply(RenderUtils.getBillboardRotation(DisplayEntity.BillboardMode.CENTER, 0, 0));
                    stack.scale(0.03125F * scale, 0.03125F * scale, 1);
                    List<Text> listText = ChatUtils.splitToMultiLineText(text, Integer.MAX_VALUE);
                    int index = 0;
                    for (var re : listText) {
                        VRender.getInstance()
                                .drawTextCameraCoord(
                                        re.asOrderedText(),
                                        stack,
                                        Vec3d.ZERO.add(0, -index * 9.0D, 0),
                                        entry.val().offSetFlag(),
                                        new Color(col),
                                        VRender.DEFAULT_TEXT);
                        index += 1;
                    }
                    stack.pop();
                }
            }

            @Override
            public void render2D(VDrawContext vdraw) {
                if (entries.isEmpty()) return;
                Matrix4f cam = RenderListener.getWorldModelViewMatrix();
                Matrix4f proj = RenderListener.getWorldBasicProjectionMatrix();
                Function<Vec3d, Vector2d> projector = RenderUtils.createProjector(cam, proj);
                for (var entry : entries) {
                    var pair = entry.val();
                    Vec3d pos = pair.position();
                    int color = entry.index();
                    Vector2d screenPos = projector.apply(pos);
                    if (screenPos != null) {
                        vdraw.pushMatrix();
                        try {
                            vdraw.getMatrices().translate((float) screenPos.x, (float) screenPos.y);
                            float scale = entry.val().scale();
                            vdraw.getMatrices().scale(scale, scale);
                            Text text = pair.text();
                            int offsetFlag = pair.offSetFlag();
                            double dd = mc.textRenderer.getTextHandler().getWidth(text);
                            int xAlign = offsetFlag % 3;
                            int yAlign = offsetFlag / 3;
                            vdraw.getMatrices().translate((float) (-((dd * xAlign) / 2.0D)), (float)
                                    (-((HEIGHT * yAlign) / 2.0D)));
                            vdraw.drawText(mc.textRenderer, text.asOrderedText(), 0, 0, color, true);
                        } finally {
                            vdraw.popMatrix();
                        }
                    }
                }
            }
        };
    }
}
