package ml.mypals.vectorthree.mc.render;

import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.RenderPipeline;

import java.util.List;
import java.util.Optional;

public final class Pipelines {
    private Pipelines() {}

    /** Everything {@code base} sets, as a snippet to build a variant on; the variant names its own location and shaders. */
    public static RenderPipeline.Snippet snippetOf(RenderPipeline base, List<BindGroupLayout> bindGroupLayouts) {
        return new RenderPipeline.Snippet(Optional.of(base.getVertexShader()), Optional.of(base.getFragmentShader()),
                Optional.of(base.getShaderDefines()), Optional.of(bindGroupLayouts),
                base.getColorTargetStates().clone(), base.getColorTargetStates().length,
                Optional.ofNullable(base.getDepthStencilState()), Optional.of(base.getPolygonMode()), Optional.of(base.isCull()),
                base.getVertexFormatBindings().clone(), Optional.of(base.getPrimitiveTopology()));
    }

    public static RenderPipeline.Snippet snippetOf(RenderPipeline base) {
        return snippetOf(base, base.getBindGroupLayouts());
    }
}
