package it.mazzoni.vis.thesis;

/** Mirrors vis-model-training/schemas/thesis-input.schema.json -> properties.dataQuality. */
public enum DataQuality {
    COMPLETE, PARTIAL, STALE, INCONSISTENT, INSUFFICIENT
}
