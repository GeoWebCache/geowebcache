package org.geowebcache.util;

import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.apache.commons.logging.Log;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;

/**
 * Rule that injects a wrapper around a commons logger allowing tests to verify logging
 *
 * @author Kevin Smith, Boundless
 */
public abstract class LogTestWrapper extends SetSingletonRule<Log> implements Log {

    /**
     * Commons-logging log levels
     *
     * @author Kevin Smith, Boundless
     */
    public static enum Level implements Comparable<Level> {
        FATAL,
        ERROR,
        WARN,
        INFO,
        DEBUG,
        TRACE
    };

    private Optional<Level> level;

    /** Create a wrapper that overrides the log level */
    public LogTestWrapper(Level level) {
        super();
        setNewValue(() -> this);
        this.level = Optional.of(level);
    }

    /** Create a wrapper */
    public LogTestWrapper() {
        super();
        setNewValue(() -> this);
        this.level = Optional.empty();
    }

    @Override
    public boolean isDebugEnabled() {
        return level.map(Level.DEBUG::compareTo)
                .map(x -> x >= 0)
                .orElse(getOldValue().isDebugEnabled());
    }

    @Override
    public boolean isErrorEnabled() {
        return level.map(Level.ERROR::compareTo)
                .map(x -> x >= 0)
                .orElse(getOldValue().isErrorEnabled());
    }

    @Override
    public boolean isFatalEnabled() {
        return level.map(Level.FATAL::compareTo)
                .map(x -> x >= 0)
                .orElse(getOldValue().isFatalEnabled());
    }

    @Override
    public boolean isInfoEnabled() {
        return level.map(Level.INFO::compareTo)
                .map(x -> x >= 0)
                .orElse(getOldValue().isInfoEnabled());
    }

    @Override
    public boolean isTraceEnabled() {
        return level.map(Level.TRACE::compareTo)
                .map(x -> x >= 0)
                .orElse(getOldValue().isTraceEnabled());
    }

    @Override
    public boolean isWarnEnabled() {
        return level.map(Level.WARN::compareTo)
                .map(x -> x >= 0)
                .orElse(getOldValue().isWarnEnabled());
    }

    /**
     * An intry in the log
     *
     * @author Kevin Smith, Boundless
     */
    public static class LogEntry {
        private final Level level;
        private final Object message;
        private final Optional<Throwable> thrown;

        public LogEntry(Level level, Object message, Throwable thrown) {
            super();
            this.level = level;
            this.message = message;
            this.thrown = Optional.of(thrown);
        }

        public LogEntry(Level level, Object message) {
            super();
            this.level = level;
            this.message = message;
            this.thrown = Optional.empty();
        }

        public Level getLevel() {
            return level;
        }

        public Object getMessage() {
            return message;
        }

        public Optional<Throwable> getThrown() {
            return thrown;
        }

        @Override
        public String toString() {
            return "LogEntry [level=" + level + ", message=" + message + ", thrown=" + thrown + "]";
        }
    }

    List<LogEntry> entries = new LinkedList<>();

    /** Get the entries that have been logged by the wrapper */
    public List<LogEntry> getEntries() {
        return entries;
    }

    /** Add an entry to the log */
    protected void log(Level level, Object message, Throwable thrown) {
        entries.add(new LogEntry(level, message, thrown));
    }

    /** Add an entry to the log */
    protected void log(Level level, Object message) {
        entries.add(new LogEntry(level, message));
    }

    @Override
    public void trace(Object message) {
        log(Level.TRACE, message);
        getOldValue().trace(message);
    }

    @Override
    public void trace(Object message, Throwable t) {
        log(Level.TRACE, message, t);
        getOldValue().trace(message, t);
    }

    @Override
    public void debug(Object message) {
        log(Level.DEBUG, message);
        getOldValue().debug(message);
    }

    @Override
    public void debug(Object message, Throwable t) {
        log(Level.DEBUG, message, t);
        getOldValue().debug(message, t);
    }

    @Override
    public void info(Object message) {
        log(Level.INFO, message);
        getOldValue().info(message);
    }

    @Override
    public void info(Object message, Throwable t) {
        log(Level.INFO, message, t);
        getOldValue().info(message, t);
    }

    @Override
    public void warn(Object message) {
        log(Level.WARN, message);
        getOldValue().warn(message);
    }

    @Override
    public void warn(Object message, Throwable t) {
        log(Level.WARN, message, t);
        getOldValue().warn(message, t);
    }

    @Override
    public void error(Object message) {
        log(Level.ERROR, message);
        getOldValue().error(message);
    }

    @Override
    public void error(Object message, Throwable t) {
        log(Level.ERROR, message, t);
        getOldValue().error(message, t);
    }

    @Override
    public void fatal(Object message) {
        log(Level.FATAL, message);
        getOldValue().fatal(message);
    }

    @Override
    public void fatal(Object message, Throwable t) {
        log(Level.FATAL, message, t);
        getOldValue().fatal(message, t);
    }

    /** Matcher for the level of an entry */
    public static Matcher<LogEntry> level(Level level) {
        return Matchers.describedAs(
                "Log entry at level %0", hasProperty("level", is(level)), level);
    }

    /** Matcher for the message of an entry */
    public static Matcher<LogEntry> message(Matcher<String> match) {
        return Matchers.describedAs(
                "Log entry with message %0", hasProperty("message", match), match);
    }

    /** Matcher for the exception causing the log entry */
    public static Matcher<LogEntry> thrown(Matcher<Throwable> match) {
        return Matchers.describedAs("Log entry with cause", hasProperty("thrown", match), match);
    }

    /** Create a log wrapper using the given injector methods */
    public static LogTestWrapper wrap(Supplier<Log> get, Consumer<Log> set) {
        return new LogTestWrapper() {

            @Override
            public void setValue(Log value) {
                set.accept(value);
            }

            @Override
            public Log getValue() {
                return get.get();
            }
        };
    }

    /** Create a log wrapper using the given injector methods and level */
    public static LogTestWrapper wrap(Supplier<Log> get, Consumer<Log> set, Level level) {
        return new LogTestWrapper(level) {

            @Override
            public void setValue(Log value) {
                set.accept(value);
            }

            @Override
            public Log getValue() {
                return get.get();
            }
        };
    }
}
