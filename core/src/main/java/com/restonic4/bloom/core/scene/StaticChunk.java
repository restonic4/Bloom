package com.restonic4.bloom.core.scene;

import java.util.List;

/** The static instances inside one cell of the scene's grid. Owned by a {@link Scene}. */
public final class StaticChunk {
    private final ChunkCoord coord;
    private final BatchSet set = new BatchSet();

    StaticChunk(ChunkCoord coord) {
        this.coord = coord;
    }

    public ChunkCoord coord() { return coord; }

    /** Never contains an empty batch. */
    public List<DrawBatch> batches() { return set.batches(); }

    public int instanceCount() { return set.instanceCount(); }

    /** Changes whenever an instance is added to or removed from this chunk, so a backend knows its copy is out of date. */
    public long version() { return set.version(); }

    BatchSet set() { return set; }
}
