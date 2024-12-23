package org.geowebcache.util;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import org.junit.rules.ExternalResource;

/**
 * Allows a singleton value to be set during a test and restored after.
 *
 * @author Kevin Smith, Boundless
 * @param <T>
 */
public abstract class SetSingletonRule<T> extends ExternalResource {

    T oldValue;

    Supplier<T> newValue;

    protected void setNewValue(Supplier<T> newValue) {
        this.newValue = newValue;
    }

    /** @param newValue Supplier for value to set the value to on start. Do not set on test start if null. */
    public SetSingletonRule(@Nullable Supplier<T> newValue) {
        super();
        this.newValue = newValue;
    }

    public SetSingletonRule() {
        this(null);
    }

    /**
     * @param get Getter for the singleton value
     * @param set Setter for the singleton value
     * @param newValue Supplier for value to set the value to on start. Do not set on test start if null.
     */
    public static <T> SetSingletonRule<T> create(Supplier<T> get, Consumer<T> set, @Nullable Supplier<T> newValue) {
        return new SetSingletonRule<>(newValue) {

            @Override
            public void setValue(T value) {
                set.accept(value);
            }

            @Override
            public T getValue() {
                return get.get();
            }
        };
    }

    /**
     * @param get Getter for the singleton value
     * @param set Setter for the singleton value
     */
    public static <T> SetSingletonRule<T> create(Supplier<T> get, Consumer<T> set) {
        return create(get, set, null);
    }

    @Override
    protected void before() throws Throwable {
        oldValue = getValue();
        init();
    }

    @Override
    protected void after() {
        setValue(oldValue);
    }

    /** Setter for the singleton value */
    public abstract void setValue(T value);

    protected void init() {
        if (Objects.nonNull(newValue)) {
            setValue(newValue.get());
        }
    }

    /** Getter for the singleton value */
    public abstract T getValue();

    /** Get the old value of the singleton */
    public T getOldValue() {
        return oldValue;
    }
}
