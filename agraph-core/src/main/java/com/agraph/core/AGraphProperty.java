package com.agraph.core;

import com.google.common.base.Preconditions;
import org.apache.tinkerpop.gremlin.structure.Property;
import org.apache.tinkerpop.gremlin.structure.util.ElementHelper;
import org.apache.tinkerpop.gremlin.structure.util.StringFactory;

import java.util.NoSuchElementException;
import java.util.Objects;

public abstract class AGraphProperty<V> implements Property<V> {

    protected final AbstractElement owner;
    protected final String key;
    protected final V value;

    private boolean removed = false;

    public AGraphProperty(AbstractElement owner, String key, V value) {
        Preconditions.checkNotNull(owner, "Owner can not be null");
        ElementHelper.validateProperty(key, value);
        AGraphProperty.validatePropertyValueDataType(value);

        this.owner = owner;
        this.key = key;
        this.value = value;
    }

    @Override
    public String key() {
        return key;
    }

    @Override
    public V value() throws NoSuchElementException {
        this.ensurePropertyExists();
        return value;
    }

    @Override
    public void remove() {
        this.ensurePropertyExists();
        this.removed = true;
        this.owner.removeProperty(this.key);
    }

    @Override
    public boolean isPresent() {
        return !removed;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AGraphProperty)) return false;
        AGraphProperty<?> that = (AGraphProperty<?>) o;
        return Objects.equals(owner, that.owner) &&
                Objects.equals(key, that.key);
    }

    @Override
    public int hashCode() {
        return ElementHelper.hashCode(this);
    }

    @Override
    public String toString() {
        return StringFactory.propertyString(this);
    }

    protected void ensurePropertyExists() {
        if (removed) {
            throw Exceptions.propertyDoesNotExist();
        }
    }

    private static <U> void validatePropertyValueDataType(U value) {
    }
}
