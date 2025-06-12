/**
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU Lesser General Public License along with this program. If not, see
 * <http://www.gnu.org/licenses/>.
 *
 * @author Kevin Smith, Boundless, 2017
 */
package org.geowebcache.util;

import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.lessThan;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.concurrent.Callable;
import org.apache.commons.io.IOUtils;
import org.geowebcache.io.Resource;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;

public class FileMatchers {
    private FileMatchers() {
        throw new IllegalStateException();
    }

    /** Matcher for a file that exists */
    public static Matcher<File> exists() {
        return new BaseMatcher<>() {

            @Override
            public boolean matches(Object item) {
                if (item instanceof File file) {
                    return file.exists();
                } else {
                    return false;
                }
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("file that exists");
            }

            @Override
            public void describeMismatch(Object item, Description description) {
                if (item instanceof File) {
                    description.appendValue(item);
                    description.appendText(" does not exist");
                } else {
                    description.appendValue(item);
                    description.appendText(" was not a File object");
                }
            }
        };
    }

    /** Matcher for a regular (non-directory) file */
    public static Matcher<File> file() {
        return new BaseMatcher<>() {

            @Override
            public boolean matches(Object item) {
                if (item instanceof File file) {
                    return file.isFile();
                } else {
                    return false;
                }
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("file that is a file (Not a directory)");
            }

            @Override
            public void describeMismatch(Object item, Description description) {
                if (item instanceof File file) {
                    if (file.exists()) {
                        description.appendValue(item);
                        description.appendText(" is a directory");
                    } else {
                        description.appendValue(item);
                        description.appendText(" does not exist");
                    }
                } else {
                    description.appendValue(item);
                    description.appendText(" was not a File object");
                }
            }
        };
    }

    /** Matcher for a directory */
    public static Matcher<File> directory() {
        return new BaseMatcher<>() {

            @Override
            public boolean matches(Object item) {
                if (item instanceof File file) {
                    return file.isDirectory();
                } else {
                    return false;
                }
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("file that is a directory");
            }

            @Override
            public void describeMismatch(Object item, Description description) {
                if (item instanceof File file) {
                    if (file.exists()) {
                        description.appendValue(item);
                        description.appendText(" is not a directory");
                    } else {
                        description.appendValue(item);
                        description.appendText(" does not exist");
                    }
                } else {
                    description.appendValue(item);
                    description.appendText(" was not a File object");
                }
            }
        };
    }

    /** Matcher for a directory's contents */
    public static Matcher<File> directoryContaining(Matcher<Iterable<File>> filesMatcher) {
        return new BaseMatcher<>() {

            @Override
            public boolean matches(Object item) {
                if (item instanceof File file) {
                    return file.isDirectory() && filesMatcher.matches(Arrays.asList(file.listFiles()));
                } else {
                    return false;
                }
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("directory that contains ");
                description.appendDescriptionOf(filesMatcher);
            }

            @Override
            public void describeMismatch(Object item, Description description) {
                if (!(item instanceof File)) {
                    description.appendValue(item);
                    description.appendText(" was not a File object");
                } else if (!((File) item).exists()) {
                    description.appendValue(item);
                    description.appendText(" does not exist");
                } else if (!((File) item).isDirectory()) {
                    description.appendValue(item);
                    description.appendText(" is not a directory");
                } else {
                    description.appendValue(item);
                    description.appendText(" had files ");
                    filesMatcher.describeMismatch(((File) item).listFiles(), description);
                }
            }
        };
    }

    public static Matcher<File> directoryEmpty() {
        return directoryContaining(Matchers.emptyIterableOf(File.class));
    }

    /** Matcher for last modified time */
    public static Matcher<File> lastModified(final Matcher<Long> timeMatcher) {
        return new BaseMatcher<>() {

            @Override
            public boolean matches(Object item) {
                if (item instanceof File file) {
                    return timeMatcher.matches(file.lastModified());
                } else {
                    return false;
                }
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("file last modified ");
                description.appendDescriptionOf(timeMatcher);
            }

            @Override
            public void describeMismatch(Object item, Description description) {
                if (item instanceof File file) {
                    if (file.exists()) {
                        description.appendValue(item);
                        description.appendText(" had modification time ");
                        timeMatcher.describeMismatch(file.lastModified(), description);
                    } else {
                        description.appendValue(item);
                        description.appendText(" does not exist");
                    }
                } else {
                    description.appendValue(item);
                    description.appendText(" was not a File object");
                }
            }
        };
    }

    /** Matcher for file with a particular name */
    @SuppressWarnings("unchecked")
    public static Matcher<File> named(String name) {
        return both(instanceOf(File.class)).and((Matcher) hasProperty("name", equalTo(name)));
    }

    /**
     * Executes the given {@link Callable} and then returns a matcher for values of {@link System#currentTimeMillis()}
     * during the execution.
     */
    public static Matcher<Long> whileRunning(Callable<Void> stuffToDo) throws Exception {
        final long start = System.currentTimeMillis();
        stuffToDo.call();
        final long end = System.currentTimeMillis();
        return both(greaterThan(start)).and(lessThan(end));
    }

    public static Matcher<Resource> resource(final Resource expected) {
        return new BaseMatcher<>() {

            @Override
            public boolean matches(Object item) {
                if (item instanceof Resource resource) {
                    try (InputStream itemStream = resource.getInputStream();
                            InputStream expectedStream = expected.getInputStream()) {
                        return IOUtils.contentEquals(itemStream, expectedStream);
                    } catch (IOException e) {
                        return false;
                    }
                } else {
                    return false;
                }
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("Resource with content equal to that given");
            }

            @Override
            public void describeMismatch(Object item, Description description) {
                if (item instanceof Resource) {
                    description.appendText("did not match given Resource");
                } else {
                    description.appendText("was not a Resource");
                }
            }
        };
    }
}
