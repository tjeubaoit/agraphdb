package com.agraph;

import com.agraph.core.type.VertexId;
import org.apache.tinkerpop.gremlin.structure.Vertex;

public interface AGraphVertex extends AGraphElement, Vertex {

    VertexId id();

    AGraphEdge addEdge(final String label, final Vertex inVertex, final Object... keyValues);
}
