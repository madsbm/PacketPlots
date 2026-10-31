package net.dashmc.plots.api.pipeline;

import java.util.LinkedList;

import net.dashmc.plots.pipeline.IRenderTransformer;
import net.dashmc.plots.pipeline.RenderPipeline;

/**
 * Builds a RenderPipeline from transformers, e.g. those of
 * net.dashmc.plots.api.pipeline.transformers, for IEnvironment#setRenderPipeline.
 */
public class RenderPipelineFactory {
	private LinkedList<IRenderTransformer> transformers = new LinkedList<>();

	public RenderPipelineFactory addTransformer(IRenderTransformer transformer) {
		transformers.add(transformer);
		return this;
	}

	public RenderPipeline build() {
		return new RenderPipeline(transformers);
	}

	public static RenderPipeline createEmptyPipeline() {
		return new RenderPipelineFactory().build();
	}
}
