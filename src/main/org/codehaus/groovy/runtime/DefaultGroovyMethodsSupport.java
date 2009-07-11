/*
 * Copyright 2003-2008 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.codehaus.groovy.runtime;

import groovy.lang.EmptyRange;
import groovy.lang.IntRange;
import org.codehaus.groovy.runtime.typehandling.DefaultTypeTransformation;

import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.*;
import java.util.logging.Logger;

/**
 * Support methods for DefaultGroovyMethods and PluginDefaultMethods.
 */
public class DefaultGroovyMethodsSupport {

    private static final Logger LOG = Logger.getLogger(DefaultGroovyMethodsSupport.class.getName());

    // helper method for getAt and putAt
    protected static RangeInfo subListBorders(int size, IntRange range) {
        int from = normaliseIndex(DefaultTypeTransformation.intUnbox(range.getFrom()), size);
        int to = normaliseIndex(DefaultTypeTransformation.intUnbox(range.getTo()), size);
        boolean reverse = range.isReverse();
        if (from > to) {
            // support list[1..-1]
            int tmp = to;
            to = from;
            from = tmp;
            reverse = !reverse;
        }
        return new RangeInfo(from, to + 1, reverse);
    }

    // helper method for getAt and putAt
    protected static RangeInfo subListBorders(int size, EmptyRange range) {
        int from = normaliseIndex(DefaultTypeTransformation.intUnbox(range.getFrom()), size);
        return new RangeInfo(from, from, false);
    }

    /**
     * This converts a possibly negative index to a real index into the array.
     *
     * @param i    the unnormalised index
     * @param size the array size
     * @return the normalised index
     */
    protected static int normaliseIndex(int i, int size) {
        int temp = i;
        if (i < 0) {
            i += size;
        }
        if (i < 0) {
            throw new ArrayIndexOutOfBoundsException("Negative array index [" + temp + "] too large for array size " + size);
        }
        return i;
    }

    /**
     * Close the Closeable. Logging a warning if any problems occur.
     *
     * @param c the thing to close
     */
    public static void closeWithWarning(Closeable c) {
        if (c != null) {
            try {
                c.close();
            } catch (IOException e) {
                LOG.warning("Caught exception during close(): " + e);
            }
        }
    }

    /**
     * Close the Closeable. Ignore any problems that might occur.
     *
     * @param c the thing to close
     */
    public static void closeQuietly(Closeable c) {
        if (c != null) {
            try {
                c.close();
            } catch (IOException e) {
                /* ignore */
            }
        }
    }

    protected static class RangeInfo {
        public int from, to;
        public boolean reverse;

        public RangeInfo(int from, int to, boolean reverse) {
            this.from = from;
            this.to = to;
            this.reverse = reverse;
        }
    }

    protected static <T> Collection<T> cloneSimilarCollection(Collection<T> orig, int newCapacity) {
        Collection<T> answer = (Collection<T>) cloneObject(orig);
        if (answer != null) return answer;
        answer = cloneCollectionFromClass(orig);
        if (answer != null) return answer;

        // fall back to creation
        answer = createSimilarCollection(orig, newCapacity);
        answer.addAll(orig);
        return answer;
    }

    private static Object cloneObject(Object orig) {
        if (orig instanceof Cloneable) {
            try {
                return InvokerHelper.invokeMethod(orig, "clone", new Object[0]);
            } catch (Exception ex) {
                // ignore
            }
        }
        return null;
    }

    protected static Collection createSimilarOrDefaultCollection(Object object) {
        if (object instanceof Collection) {
            return createSimilarCollection((Collection) object);
        }
        return new ArrayList();
    }

    protected static <T> Collection<T> createSimilarCollection(Collection<T> collection) {
        return createSimilarCollection(collection, collection.size());
    }

    protected static <T> Collection<T> createSimilarCollection(Collection<T> orig, int newCapacity) {
        if (orig instanceof Set) {
            return createSimilarSet((Set<T>) orig);
        }
        if (orig instanceof List) {
            return createSimilarList((List<T>) orig, newCapacity);
        }
        Collection<T> answer = createCollectionFromClass(orig);
        if (answer != null) return answer;

        if (orig instanceof Queue) {
            return new LinkedList<T>();
        }
        return new ArrayList<T>(newCapacity);
    }

    protected static <T> List<T> createSimilarList(List<T> orig, int newCapacity) {
        List<T> answer = (List<T>) createCollectionFromClass(orig);
        if (answer != null) return answer;

        if (orig instanceof LinkedList) {
            answer = new LinkedList<T>();
        } else if (orig instanceof Stack) {
            answer = new Stack<T>();
        } else if (orig instanceof Vector) {
            answer = new Vector<T>();
        } else {
            answer = new ArrayList<T>(newCapacity);
        }
        return answer;
    }

    protected static <T> Set<T> createSimilarSet(Set<T> orig) {
        Set<T> answer = (Set<T>) createCollectionFromClass(orig);
        if (answer != null) return answer;

        // fall back to some defaults
        if (orig instanceof SortedSet) {
            return new TreeSet<T>();
        }
        if (orig instanceof LinkedHashSet) {
            return new LinkedHashSet<T>();
        }
        return new HashSet<T>();
    }

    protected static <K, V> Map<K, V> createSimilarMap(Map<K, V> orig) {
        Map<K, V> answer = createMapFromClass(orig);
        if (answer != null) return answer;

        // fall back to some defaults
        if (orig instanceof SortedMap) {
            return new TreeMap<K, V>();
        }
        if (orig instanceof Properties) {
            return (Map<K, V>) new Properties();
        }
        if (orig instanceof Hashtable) {
            return new Hashtable<K, V>();
        }
        return new LinkedHashMap<K, V>();
    }

    private static <T> Collection<T> createCollectionFromClass(Collection<T> orig) {
        if (orig instanceof AbstractCollection) {
            try {
                final Constructor constructor = orig.getClass().getConstructor();
                return (Collection<T>) constructor.newInstance();
            } catch (Exception e) {
                // ignore
            }
        }
        return null;
    }

    private static <T> Collection<T> cloneCollectionFromClass(Collection<T> orig) {
        if (orig instanceof AbstractCollection) {
			try {
				final Constructor constructor = orig.getClass().getConstructor(Collection.class);
				return (Collection<T>) constructor.newInstance(orig);
			} catch (Exception e) {
				// ignore
			}
			try {
				final Constructor constructor = orig.getClass().getConstructor();
				final Collection<T> result = (Collection<T>) constructor.newInstance();
				result.addAll(orig);
				return result;
			} catch (Exception e) {
				// ignore
			}
		}
        return null;
    }

    private static <K, V> Map<K, V> createMapFromClass(Map<K, V> orig) {
        if ((orig instanceof AbstractMap) || (orig instanceof Hashtable)) {
            try {
                final Constructor constructor = orig.getClass().getConstructor();
                return (Map<K, V>) constructor.newInstance();
            } catch (Exception e) {
                // ignore
            }
        }
        return null;
    }

    private static <K, V> Map<K, V> cloneMapFromClass(Map<K, V> orig) {
        if ((orig instanceof AbstractMap) || (orig instanceof Hashtable)) {
			try {
				final Constructor constructor = orig.getClass().getConstructor(Map.class);
				return (Map<K, V>) constructor.newInstance(orig);
			} catch (Exception e) {
				// ignore
			}
			try {
				final Constructor constructor = orig.getClass().getConstructor();
				final Map<K, V> result = (Map<K, V>) constructor.newInstance();
				result.putAll(orig);
				return result;
			} catch (Exception e) {
				// ignore
			}
		}
        return null;
    }

    protected static <K, V> Map<K ,V> cloneSimilarMap(Map<K, V> orig) {
        Map<K, V> answer = (Map<K, V>) cloneObject(orig);
        if (answer != null) return answer;
        answer = cloneMapFromClass(orig);
        if (answer != null) return answer;

        // fall back to some defaults
        if (orig instanceof TreeMap)
            return new TreeMap<K, V>(orig);

        if (orig instanceof Properties) {
            Map<K, V> map = (Map<K, V>) new Properties();
            map.putAll(orig);
            return map;
        }

        if (orig instanceof Hashtable)
            return new Hashtable<K, V>(orig);

        return new LinkedHashMap<K, V>(orig);
    }

    /**
     * Determines if all items of this array are of the same type.
     *
     * @param cols an array of collections
     * @return true if the collections are all of the same type
     */
    protected static boolean sameType(Collection[] cols) {
        List all = new LinkedList();
        for (Collection col : cols) {
            all.addAll(col);
        }
        if (all.size() == 0)
            return true;

        Object first = all.get(0);

        //trying to determine the base class of the collections
        //special case for Numbers
        Class baseClass;
        if (first instanceof Number) {
            baseClass = Number.class;
        } else if (first == null) {
            baseClass = NullObject.class;
        } else {
            baseClass = first.getClass();
        }

        for (Collection col : cols) {
            for (Object o : col) {
                if (!baseClass.isInstance(o)) {
                    return false;
                }
            }
        }
        return true;
    }
}
