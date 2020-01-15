package com.agraph.core;

import com.agraph.AGraphEdge;
import com.agraph.AGraphVertex;
import com.agraph.State;
import com.agraph.core.type.VertexId;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterators;
import io.reactivex.Observable;
import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.VertexProperty;
import org.apache.tinkerpop.gremlin.structure.util.ElementHelper;
import org.apache.tinkerpop.gremlin.structure.util.StringFactory;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * @author <a href="https://github.com/tjeubaoit">tjeubaoit</a>
 */
public class InternalVertex extends AbstractElement implements AGraphVertex {

    public InternalVertex(DefaultAGraph graph, VertexId id) {
        super(graph, id, Vertex.DEFAULT_LABEL);
    }

    public InternalVertex(DefaultAGraph graph, VertexId id, String label) {
        super(graph, id, label, State.NEW);
    }

    public InternalVertex(DefaultAGraph graph, VertexId id, String label, State state) {
        super(graph, id, label, state);
    }

    @Override
    public VertexId id() {
        return (VertexId) super.id();
    }

    @Override
    public AGraphEdge addEdge(String label, Vertex inVertex, Object... keyValues) {
        Optional<Object> idOps = ElementHelper.getIdValue(keyValues);
        if (idOps.isPresent()) {
            throw Edge.Exceptions.userSuppliedIdsNotSupported();
        }
        ElementHelper.validateLabel(label);
        ElementHelper.legalPropertyKeyValueArray(keyValues);

        Preconditions.checkNotNull(inVertex, "Incoming vertex can't be null");
        Preconditions.checkArgument(inVertex instanceof AGraphVertex,
                "Incoming vertex must be an instance of InternalVertex");

        InternalVertex vertex = (InternalVertex) inVertex;
        Preconditions.checkState(this.isPresent(),
                "Could not add edge from a removed vertex: {}", this);
        Preconditions.checkState(vertex.isPresent(),
                "Could not add edge to a removed vertex: {}", vertex);

        InternalEdge edge = new InternalEdge(this.graph(), label, this, vertex);
        ElementHelper.attachProperties(edge, keyValues);

        return this.tx().addEdge(edge);
    }

    @Override
    public <V> VertexProperty<V> property(VertexProperty.Cardinality cardinality,
                                          String key, V value, Object... keyValues) {
        this.ensureElementCanModify();
        if (keyValues.length != 0) {
            throw VertexProperty.Exceptions.metaPropertiesNotSupported();
        }
        if (cardinality != VertexProperty.Cardinality.single) {
            throw new UnsupportedOperationException("Cardinality list or set is not supported");
        }

        AGraphVertexProperty<V> property = new AGraphVertexProperty<>(this, key, value);
        this.putProperty(property);
        return property;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <V> Iterator<VertexProperty<V>> properties(String... keys) {
        if (keys.length > 0) {
            return Observable.fromArray(keys)
                    .map(key -> (VertexProperty<V>) this.autoFilledProperties().get(key))
                    .filter(VertexProperty::isPresent)
                    .blockingIterable()
                    .iterator();
        } else {
            return Observable.fromIterable(this.autoFilledProperties().values())
                    .filter(AGraphProperty::isPresent)
                    .map(e -> (VertexProperty<V>) e)
                    .blockingIterable()
                    .iterator();
        }
    }

    @Override
    public Iterator<Edge> edges(Direction direction, String... edgeLabels) {
        return Iterators.transform(this.tx().edges(this, direction, edgeLabels), e -> e);
    }

    @Override
    public Iterator<Vertex> vertices(Direction direction, String... edgeLabels) {
        return Iterators.transform(this.tx().vertices(this, direction, edgeLabels), e -> e);
    }

    @Override
    public void remove() {
        super.remove();
        this.tx().removeVertex(this);
    }

    @Override
    public boolean ensureFilledProperties(boolean throwIfNotExist) {
        if (isNew() || isLoaded()) {
            return true;
        }
        if (!this.tx().fillVertexProperties(this)) {
            if (throwIfNotExist) {
                throw new NoSuchElementException("Vertex does not exist: " + this.id());
            } else return false;
        }
        return true;
    }

    @Override
    public AbstractElement copy() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String toString() {
        return StringFactory.vertexString(this);
    }
}
