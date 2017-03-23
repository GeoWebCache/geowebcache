package org.geowebcache.util;

import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThan;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Callable;

import org.apache.commons.io.IOUtils;
import org.geowebcache.io.Resource;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;

public class FileMatchers {
    private FileMatchers() {throw new IllegalStateException();};
    
    /**
     * Matcher for a file that exists
     * @return
     */
    public static Matcher<File> exists() {
        return new BaseMatcher<File>() {
            
            @Override
            public boolean matches(Object item) {
                if(item instanceof File) {
                    return ((File) item).exists();
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
                if(item instanceof File) {
                    description.appendValue(item);
                    description.appendText(" does not exist");
                } else {
                    description.appendValue(item);
                    description.appendText(" was not a File object");
                }
            }
        };
    }
    
    /**
     * Matcher for a regular (non-directory) file
     * @return
     */
    public static Matcher<File> file() {
        return new BaseMatcher<File>() {
            
            @Override
            public boolean matches(Object item) {
                if(item instanceof File) {
                    return ((File) item).isFile();
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
                if(item instanceof File) {
                    if(((File) item).exists()) {
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
    
    /**
     * Matcher for a directory
     * @return
     */
    public static Matcher<File> directory() {
        return new BaseMatcher<File>() {
            
            @Override
            public boolean matches(Object item) {
                if(item instanceof File) {
                    return ((File) item).isDirectory();
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
                if(item instanceof File) {
                    if(((File) item).exists()) {
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
    
    /**
     * Matcher for a directory's contents
     * @return
     */
    public static Matcher<File> directoryContaining(Matcher<Iterable<File>> filesMatcher) {
        return new BaseMatcher<File>() {
            
            @Override
            public boolean matches(Object item) {
                if(item instanceof File) {
                    return ((File) item).isDirectory() && filesMatcher.matches(((File) item).listFiles());
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
                if(! (item instanceof File)) {
                    description.appendValue(item);
                    description.appendText(" was not a File object");
                } else if(! ((File) item).exists()) {
                    description.appendValue(item);
                    description.appendText(" does not exist");
                } else if(! ((File) item).isDirectory()) {
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
    
    /**
     * Matcher for last modified time
     * @param timeMatcher
     * @return
     */
    public static Matcher<File> lastModified(final Matcher<Long> timeMatcher) {
        return new BaseMatcher<File>() {
            
            @Override
            public boolean matches(Object item) {
                if(item instanceof File) {
                    return timeMatcher.matches(((File) item).lastModified());
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
                if(item instanceof File) {
                    if(((File) item).exists()) {
                        description.appendValue(item);
                        description.appendText(" had modification time ");
                        timeMatcher.describeMismatch(((File) item).lastModified(), description);
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
    
    /**
     * Executes the given {@link Callable} and then returns a matcher for values of 
     * {@link System.currentTimeMillis} during the execution.
     * @param stuffToDo
     * @return
     * @throws any exceptions thrown by stuffToDo
     */
    public static Matcher<Long> whileRunning(Callable<Void> stuffToDo) throws Exception {
        final long start = System.currentTimeMillis();
        stuffToDo.call();
        final long end = System.currentTimeMillis();
        return both(greaterThan(start)).and(lessThan(end));
    }
    
    public static Matcher<Resource> resource(final Resource expected) {
        return new BaseMatcher<Resource>() {
            
            @Override
            public boolean matches(Object item) {
                if(item instanceof Resource) {
                    try(
                        InputStream itemStream = ((Resource) item).getInputStream();
                        InputStream expectedStream = expected.getInputStream();
                    ) {
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
                if(item instanceof Resource) {
                    description.appendText("did not match given Resource");
                } else {
                    description.appendText("was not a Resource");
                }
            }
            
        };
    }
}
