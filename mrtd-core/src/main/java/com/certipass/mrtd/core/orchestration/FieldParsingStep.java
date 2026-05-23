package com.certipass.mrtd.core.orchestration;

import com.certipass.mrtd.core.parsing.MrzParserFactory;

/**
 * Pipeline step that parses MRZ fields into identity data.
 */
public final class FieldParsingStep implements PipelineStep<Void> {
    private final MrzParserFactory parserFactory;

    public FieldParsingStep(MrzParserFactory parserFactory) {
        this.parserFactory = parserFactory;
    }

    @Override
    public StepResult<Void> execute(PipelineContext context) {
        context.identityData(parserFactory.parserFor(context.mrzFormat()).parse(context.ocrResult().mrzLines()));
        return new StepResult.Success<>(null);
    }
}
