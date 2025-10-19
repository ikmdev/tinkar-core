/*
 * Copyright © 2015 Integrated Knowledge Management (support@ikm.dev)
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
package dev.ikm.tinkar.collection;

import org.eclipse.collections.api.InternalIterable;
import org.eclipse.collections.api.PrimitiveIterable;
import org.eclipse.collections.api.bag.Bag;
import org.eclipse.collections.api.bag.sorted.SortedBag;
import org.eclipse.collections.api.block.predicate.Predicate;
import org.eclipse.collections.api.collection.ImmutableCollection;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.map.ImmutableMapIterable;
import org.eclipse.collections.api.map.MapIterable;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.api.map.MutableMapIterable;
import org.eclipse.collections.api.map.sorted.SortedMapIterable;
import org.eclipse.collections.api.multimap.Multimap;
import org.eclipse.collections.api.multimap.bag.BagMultimap;
import org.eclipse.collections.api.multimap.list.ListMultimap;
import org.eclipse.collections.api.multimap.set.SetMultimap;
import org.eclipse.collections.api.multimap.sortedbag.SortedBagMultimap;
import org.eclipse.collections.api.multimap.sortedset.SortedSetMultimap;
import org.eclipse.collections.api.set.ImmutableSet;
import org.eclipse.collections.api.set.MutableSet;
import org.eclipse.collections.impl.block.factory.Comparators;
import org.eclipse.collections.impl.block.factory.Predicates;
import org.eclipse.collections.impl.block.procedure.CollectionAddProcedure;
import org.eclipse.collections.impl.factory.Lists;
import org.eclipse.collections.impl.factory.Sets;
import org.eclipse.collections.impl.list.mutable.FastList;
import org.eclipse.collections.impl.map.mutable.UnifiedMap;
import org.eclipse.collections.impl.set.mutable.UnifiedSet;
import org.eclipse.collections.impl.tuple.ImmutableEntry;
import org.eclipse.collections.impl.utility.ArrayIterate;
import org.eclipse.collections.impl.utility.Iterate;
import org.junit.jupiter.api.Assertions;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.NotSerializableException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Base64;
import java.util.Base64.Decoder;
import java.util.Base64.Encoder;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.concurrent.Callable;

/**
 * An extension of the Assert class, which adds useful additional "assert" methods.
 * You can import this class instead of Assert, and use it thus, e.g.:
 * <pre>
 *     Verify.assertEquals("fred", name);  // from original Assert class
 *     Verify.assertContains("fred", nameList);  // from new extensions
 *     Verify.assertBefore("fred", "jim", orderedNamesList);  // from new extensions
 * </pre>
 */
public final class Verify
{
    private static final int MAX_DIFFERENCES = 5;
    private static final byte[] LINE_SEPARATOR = {'\n'};
    private static final Encoder ENCODER = Base64.getMimeEncoder(76, LINE_SEPARATOR);
    private static final Decoder DECODER = Base64.getMimeDecoder();

    private Verify()
    {
        throw new AssertionError("Suppress default constructor for noninstantiability");
    }

    /**
     * Mangles the stack trace of {@link AssertionError} so that it looks like its been thrown from the line that
     * called to a custom assertion.
     * <p>
     * This method behaves identically to {@link #throwMangledException(AssertionError, int)} and is provided
     * for convenience for assert methods that only want to pop two stack frames. The only time that you would want to
     * call the other {@link #throwMangledException(AssertionError, int)} method is if you have a custom assert
     * that calls another custom assert i.e. the source line calling the custom asserts is more than two stack frames
     * away
     *
     * @param e The exception to mangle.
     * @see #throwMangledException(AssertionError, int)
     */
    public static void throwMangledException(AssertionError e)
    {
        /*
         * Note that we actually remove 3 frames from the stack trace because
         * we wrap the real method doing the work: e.fillInStackTrace() will
         * include us in the exceptions stack frame.
         */
        Verify.throwMangledException(e, 3);
    }

    /**
     * Mangles the stack trace of {@link AssertionError} so that it looks like
     * its been thrown from the line that called to a custom assertion.
     * <p>
     * This is useful for when you are in a debugging session and you want to go to the source
     * of the problem in the test case quickly. The regular use case for this would be something
     * along the lines of:
     * <pre>
     * public class TestFoo extends junit.framework.TestCase
     * {
     *   public void testFoo() throws Exception
     *   {
     *     Foo foo = new Foo();
     *     ...
     *     assertFoo(foo);
     *   }
     *
     *   // Custom assert
     *   private static void assertFoo(Foo foo)
     *   {
     *     try
     *     {
     *       assertEquals(...);
     *       ...
     *       assertSame(...);
     *     }
     *     catch (AssertionFailedException e)
     *     {
     *       AssertUtils.throwMangledException(e, 2);
     *     }
     *   }
     * }
     * </pre>
     * <p>
     * Without the {@code try ... catch} block around lines 11-13 the stack trace following a test failure
     * would look a little like:
     *
     * <pre>
     * java.lang.AssertionError: ...
     *  at TestFoo.assertFoo(TestFoo.java:11)
     *  at TestFoo.testFoo(TestFoo.java:5)
     *  at sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
     *  at sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:39)
     *  at sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:25)
     *  at java.lang.reflect.Method.invoke(Method.java:324)
     *  ...
     * </pre>
     * <p>
     * Note that the source of the error isn't readily apparent as the first line in the stack trace
     * is the code within the custom Assertions. If we were debugging the failure we would be more interested
     * in the second line of the stack trace which shows us where in our tests the assert failed.
     * <p>
     * With the {@code try ... catch} block around lines 11-13 the stack trace would look like the
     * following:
     *
     * <pre>
     * java.lang.AssertionError: ...
     *  at TestFoo.testFoo(TestFoo.java:5)
     *  at sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
     *  at sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:39)
     *  at sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:25)
     *  at java.lang.reflect.Method.invoke(Method.java:324)
     *  ...
     * </pre>
     * <p>
     * Here the source of the error is more visible as we can instantly see that the testFoo test is
     * failing at line 5.
     *
     * @param e           The exception to mangle.
     * @param framesToPop The number of frames to remove from the stack trace.
     * @throws AssertionError that was given as an argument with its stack trace mangled.
     */
    public static void throwMangledException(AssertionError e, int framesToPop)
    {
        e.fillInStackTrace();
        StackTraceElement[] stackTrace = e.getStackTrace();
        StackTraceElement[] newStackTrace = new StackTraceElement[stackTrace.length - framesToPop];
        System.arraycopy(stackTrace, framesToPop, newStackTrace, 0, newStackTrace.length);
        e.setStackTrace(newStackTrace);
        throw e;
    }

    public static void fail(String message, Throwable cause)
    {
        AssertionError failedException = new AssertionError(message, cause);
        Verify.throwMangledException(failedException);
    }

    /**
     * Assert that two items are not the same. If one item is null, the the other must be non-null.
     *
     * @deprecated in 3.0. Use {@link Assertions#assertNotEquals(Object, Object, String)} in JUnit 4.11 instead.
     */
    @Deprecated
    public static void assertNotEquals(String itemsName, Object item1, Object item2)
    {
        try
        {
            if (Comparators.nullSafeEquals(item1, item2) || Comparators.nullSafeEquals(item2, item1))
            {
                Assertions.fail(itemsName + " should not be equal, item1:<" + item1 + ">, item2:<" + item2 + '>');
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that two items are not the same. If one item is null, the the other must be non-null.
     *
     * @deprecated in 3.0. Use {@link Assertions#assertNotEquals(Object, Object)} in JUnit 4.11 instead.
     */
    @Deprecated
    public static void assertNotEquals(Object item1, Object item2)
    {
        try
        {
            Verify.assertNotEquals("items", item1, item2);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Asserts that two Strings are not equal.
     *
     * @deprecated in 3.0. Use {@link Assertions#assertNotEquals(Object, Object, String)} in JUnit 4.11 instead.
     */
    @Deprecated
    public static void assertNotEquals(String itemName, String notExpected, String actual)
    {
        try
        {
            if (Comparators.nullSafeEquals(notExpected, actual))
            {
                Assertions.fail(itemName + " should not equal:<" + notExpected + '>');
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Asserts that two Strings are not equal.
     *
     * @deprecated in 3.0. Use {@link Assertions#assertNotEquals(Object, Object)} in JUnit 4.11 instead.
     */
    @Deprecated
    public static void assertNotEquals(String notExpected, String actual)
    {
        try
        {
            Verify.assertNotEquals("string", notExpected, actual);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Asserts that two doubles are not equal concerning a delta. If the expected value is infinity then the delta value
     * is ignored.
     *
     */
    @Deprecated
    public static void assertNotEquals(String itemName, double notExpected, double actual, double delta)
    {
        // handle infinity specially since subtracting to infinite values gives NaN and the
        // the following test fails
        try
        {
            //noinspection FloatingPointEquality
            if (Double.isInfinite(notExpected) && notExpected == actual || Math.abs(notExpected - actual) <= delta)
            {
                Assertions.fail(itemName + " should not be equal:<" + notExpected + '>');
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Asserts that two doubles are not equal concerning a delta. If the expected value is infinity then the delta value
     * is ignored.
     *
     */
    @Deprecated
    public static void assertNotEquals(double notExpected, double actual, double delta)
    {
        try
        {
            Verify.assertNotEquals("double", notExpected, actual, delta);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Asserts that two floats are not equal concerning a delta. If the expected value is infinity then the delta value
     * is ignored.
     */
    public static void assertNotEquals(String itemName, float notExpected, float actual, float delta)
    {
        try
        {
            // handle infinity specially since subtracting to infinite values gives NaN and the
            // the following test fails
            //noinspection FloatingPointEquality
            if (Float.isInfinite(notExpected) && notExpected == actual || Math.abs(notExpected - actual) <= delta)
            {
                Assertions.fail(itemName + " should not be equal:<" + notExpected + '>');
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Asserts that two floats are not equal concerning a delta. If the expected value is infinity then the delta value
     * is ignored.
     */
    public static void assertNotEquals(float expected, float actual, float delta)
    {
        try
        {
            Verify.assertNotEquals("float", expected, actual, delta);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Asserts that two longs are not equal.
     *
     */
    @Deprecated
    public static void assertNotEquals(String itemName, long notExpected, long actual)
    {
        try
        {
            if (notExpected == actual)
            {
                Assertions.fail(itemName + " should not be equal:<" + notExpected + '>');
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Asserts that two longs are not equal.
     *
     */
    @Deprecated
    public static void assertNotEquals(long notExpected, long actual)
    {
        try
        {
            Verify.assertNotEquals("long", notExpected, actual);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Asserts that two booleans are not equal.
     */
    public static void assertNotEquals(String itemName, boolean notExpected, boolean actual)
    {
        try
        {
            if (notExpected == actual)
            {
                Assertions.fail(itemName + " should not be equal:<" + notExpected + '>');
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Asserts that two booleans are not equal.
     */
    public static void assertNotEquals(boolean notExpected, boolean actual)
    {
        try
        {
            Verify.assertNotEquals("boolean", notExpected, actual);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Asserts that two bytes are not equal.
     */
    public static void assertNotEquals(String itemName, byte notExpected, byte actual)
    {
        try
        {
            if (notExpected == actual)
            {
                Assertions.fail(itemName + " should not be equal:<" + notExpected + '>');
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Asserts that two bytes are not equal.
     */
    public static void assertNotEquals(byte notExpected, byte actual)
    {
        try
        {
            Verify.assertNotEquals("byte", notExpected, actual);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Asserts that two chars are not equal.
     */
    public static void assertNotEquals(String itemName, char notExpected, char actual)
    {
        try
        {
            if (notExpected == actual)
            {
                Assertions.fail(itemName + " should not be equal:<" + notExpected + '>');
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Asserts that two chars are not equal.
     */
    public static void assertNotEquals(char notExpected, char actual)
    {
        try
        {
            Verify.assertNotEquals("char", notExpected, actual);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Asserts that two shorts are not equal.
     */
    public static void assertNotEquals(String itemName, short notExpected, short actual)
    {
        try
        {
            if (notExpected == actual)
            {
                Assertions.fail(itemName + " should not be equal:<" + notExpected + '>');
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Asserts that two shorts are not equal.
     */
    public static void assertNotEquals(short notExpected, short actual)
    {
        try
        {
            Verify.assertNotEquals("short", notExpected, actual);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Asserts that two ints are not equal.
     *
     */
    @Deprecated
    public static void assertNotEquals(String itemName, int notExpected, int actual)
    {
        try
        {
            if (notExpected == actual)
            {
                Assertions.fail(itemName + " should not be equal:<" + notExpected + '>');
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Asserts that two ints are not equal.
     *
     */
    @Deprecated
    public static void assertNotEquals(int notExpected, int actual)
    {
        try
        {
            Verify.assertNotEquals("int", notExpected, actual);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@link Iterable} is empty.
     */
    public static void assertEmpty(Iterable<?> actualIterable)
    {
        try
        {
            Verify.assertEmpty("iterable", actualIterable);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@link Collection} is empty.
     */
    public static void assertEmpty(String iterableName, Iterable<?> actualIterable)
    {
        try
        {
            Verify.assertObjectNotNull(iterableName, actualIterable);

            if (Iterate.notEmpty(actualIterable))
            {
                Assertions.fail(iterableName + " should be empty; actual size:<" + Iterate.sizeOf(actualIterable) + '>');
            }
            if (!Iterate.isEmpty(actualIterable))
            {
                Assertions.fail(iterableName + " should be empty; actual size:<" + Iterate.sizeOf(actualIterable) + '>');
            }
            if (Iterate.sizeOf(actualIterable) != 0)
            {
                Assertions.fail(iterableName + " should be empty; actual size:<" + Iterate.sizeOf(actualIterable) + '>');
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@link MutableMapIterable} is empty.
     */
    public static void assertEmpty(MutableMapIterable<?, ?> actualMutableMapIterable)
    {
        try
        {
            Verify.assertEmpty("mutableMapIterable", actualMutableMapIterable);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@link Collection} is empty.
     */
    public static void assertEmpty(String mutableMapIterableName, MutableMapIterable<?, ?> actualMutableMapIterable)
    {
        try
        {
            Verify.assertObjectNotNull(mutableMapIterableName, actualMutableMapIterable);

            if (Iterate.notEmpty(actualMutableMapIterable))
            {
                Assertions.fail(mutableMapIterableName + " should be empty; actual size:<" + Iterate.sizeOf(actualMutableMapIterable) + '>');
            }
            if (!Iterate.isEmpty(actualMutableMapIterable))
            {
                Assertions.fail(mutableMapIterableName + " should be empty; actual size:<" + Iterate.sizeOf(actualMutableMapIterable) + '>');
            }
            if (!actualMutableMapIterable.isEmpty())
            {
                Assertions.fail(mutableMapIterableName + " should be empty; actual size:<" + Iterate.sizeOf(actualMutableMapIterable) + '>');
            }
            if (actualMutableMapIterable.notEmpty())
            {
                Assertions.fail(mutableMapIterableName + " should be empty; actual size:<" + Iterate.sizeOf(actualMutableMapIterable) + '>');
            }
            if (actualMutableMapIterable.size() != 0)
            {
                Assertions.fail(mutableMapIterableName + " should be empty; actual size:<" + actualMutableMapIterable.size() + '>');
            }
            if (actualMutableMapIterable.keySet().size() != 0)
            {
                Assertions.fail(mutableMapIterableName + " should be empty; actual size:<" + actualMutableMapIterable.keySet().size() + '>');
            }
            if (actualMutableMapIterable.values().size() != 0)
            {
                Assertions.fail(mutableMapIterableName + " should be empty; actual size:<" + actualMutableMapIterable.values().size() + '>');
            }
            if (actualMutableMapIterable.entrySet().size() != 0)
            {
                Assertions.fail(mutableMapIterableName + " should be empty; actual size:<" + actualMutableMapIterable.entrySet().size() + '>');
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@link PrimitiveIterable} is empty.
     */
    public static void assertEmpty(PrimitiveIterable primitiveIterable)
    {
        try
        {
            Verify.assertEmpty("primitiveIterable", primitiveIterable);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@link PrimitiveIterable} is empty.
     */
    public static void assertEmpty(String iterableName, PrimitiveIterable primitiveIterable)
    {
        try
        {
            Verify.assertObjectNotNull(iterableName, primitiveIterable);

            if (primitiveIterable.notEmpty())
            {
                Assertions.fail(iterableName + " should be empty; actual size:<" + primitiveIterable.size() + '>');
            }
            if (!primitiveIterable.isEmpty())
            {
                Assertions.fail(iterableName + " should be empty; actual size:<" + primitiveIterable.size() + '>');
            }
            if (primitiveIterable.size() != 0)
            {
                Assertions.fail(iterableName + " should be empty; actual size:<" + primitiveIterable.size() + '>');
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@link Iterable} is empty.
     */
    public static void assertIterableEmpty(Iterable<?> iterable)
    {
        try
        {
            Verify.assertIterableEmpty("iterable", iterable);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@link Iterable} is empty.
     */
    public static void assertIterableEmpty(String iterableName, Iterable<?> iterable)
    {
        try
        {
            Verify.assertObjectNotNull(iterableName, iterable);

            if (Iterate.notEmpty(iterable))
            {
                Assertions.fail(iterableName + " should be empty; actual size:<" + Iterate.sizeOf(iterable) + '>');
            }
            if (!Iterate.isEmpty(iterable))
            {
                Assertions.fail(iterableName + " should be empty; actual size:<" + Iterate.sizeOf(iterable) + '>');
            }
            if (Iterate.sizeOf(iterable) != 0)
            {
                Assertions.fail(iterableName + " should be empty; actual size:<" + Iterate.sizeOf(iterable) + '>');
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given object is an instanceof expectedClassType.
     */
    public static void assertInstanceOf(Class<?> expectedClassType, Object actualObject)
    {
        try
        {
            Verify.assertInstanceOf(actualObject.getClass().getName(), expectedClassType, actualObject);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given object is an instanceof expectedClassType.
     */
    public static void assertInstanceOf(String objectName, Class<?> expectedClassType, Object actualObject)
    {
        try
        {
            if (!expectedClassType.isInstance(actualObject))
            {
                Assertions.fail(objectName + " is not an instance of " + expectedClassType.getName());
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given object is not an instanceof expectedClassType.
     */
    public static void assertNotInstanceOf(Class<?> expectedClassType, Object actualObject)
    {
        try
        {
            Verify.assertNotInstanceOf(actualObject.getClass().getName(), expectedClassType, actualObject);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given object is not an instanceof expectedClassType.
     */
    public static void assertNotInstanceOf(String objectName, Class<?> expectedClassType, Object actualObject)
    {
        try
        {
            if (expectedClassType.isInstance(actualObject))
            {
                Assertions.fail(objectName + " is an instance of " + expectedClassType.getName());
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@link Map} is empty.
     */
    public static void assertEmpty(Map<?, ?> actualMap)
    {
        try
        {
            Verify.assertEmpty("map", actualMap);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@link Multimap} is empty.
     */
    public static void assertEmpty(Multimap<?, ?> actualMultimap)
    {
        try
        {
            Verify.assertEmpty("multimap", actualMultimap);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@link Multimap} is empty.
     */
    public static void assertEmpty(String multimapName, Multimap<?, ?> actualMultimap)
    {
        try
        {
            Verify.assertObjectNotNull(multimapName, actualMultimap);

            if (actualMultimap.notEmpty())
            {
                Assertions.fail(multimapName + " should be empty; actual size:<" + actualMultimap.size() + '>');
            }
            if (!actualMultimap.isEmpty())
            {
                Assertions.fail(multimapName + " should be empty; actual size:<" + actualMultimap.size() + '>');
            }
            if (actualMultimap.size() != 0)
            {
                Assertions.fail(multimapName + " should be empty; actual size:<" + actualMultimap.size() + '>');
            }
            if (actualMultimap.sizeDistinct() != 0)
            {
                Assertions.fail(multimapName + " should be empty; actual size:<" + actualMultimap.size() + '>');
            }
            if (actualMultimap.keyBag().size() != 0)
            {
                Assertions.fail(multimapName + " should be empty; actual size:<" + actualMultimap.keyBag().size() + '>');
            }
            if (actualMultimap.keysView().size() != 0)
            {
                Assertions.fail(multimapName + " should be empty; actual size:<" + actualMultimap.keysView().size() + '>');
            }
            if (actualMultimap.valuesView().size() != 0)
            {
                Assertions.fail(multimapName + " should be empty; actual size:<" + actualMultimap.valuesView().size() + '>');
            }
            if (actualMultimap.keyValuePairsView().size() != 0)
            {
                Assertions.fail(multimapName + " should be empty; actual size:<" + actualMultimap.keyValuePairsView().size() + '>');
            }
            if (actualMultimap.keyMultiValuePairsView().size() != 0)
            {
                Assertions.fail(multimapName + " should be empty; actual size:<" + actualMultimap.keyMultiValuePairsView().size() + '>');
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@link Map} is empty.
     */
    public static void assertEmpty(String mapName, Map<?, ?> actualMap)
    {
        try
        {
            Verify.assertObjectNotNull(mapName, actualMap);

            if (!actualMap.isEmpty())
            {
                Assertions.fail(mapName + " should be empty; actual size:<" + actualMap.size() + '>');
            }
            if (actualMap.size() != 0)
            {
                Assertions.fail(mapName + " should be empty; actual size:<" + actualMap.size() + '>');
            }
            if (actualMap.keySet().size() != 0)
            {
                Assertions.fail(mapName + " should be empty; actual size:<" + actualMap.keySet().size() + '>');
            }
            if (actualMap.values().size() != 0)
            {
                Assertions.fail(mapName + " should be empty; actual size:<" + actualMap.values().size() + '>');
            }
            if (actualMap.entrySet().size() != 0)
            {
                Assertions.fail(mapName + " should be empty; actual size:<" + actualMap.entrySet().size() + '>');
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@link Iterable} is <em>not</em> empty.
     */
    public static void assertNotEmpty(Iterable<?> actualIterable)
    {
        try
        {
            Verify.assertNotEmpty("iterable", actualIterable);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@link Iterable} is <em>not</em> empty.
     */
    public static void assertNotEmpty(String iterableName, Iterable<?> actualIterable)
    {
        try
        {
            Verify.assertObjectNotNull(iterableName, actualIterable);
            Assertions.assertFalse(Iterate.isEmpty(actualIterable), iterableName + " should be non-empty, but was empty");
            Assertions.assertTrue(Iterate.notEmpty(actualIterable), iterableName + " should be non-empty, but was empty");
            Assertions.assertNotEquals(0, Iterate.sizeOf(actualIterable), iterableName + " should be non-empty, but was empty");
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@link MutableMapIterable} is <em>not</em> empty.
     */
    public static void assertNotEmpty(MutableMapIterable<?, ?> actualMutableMapIterable)
    {
        try
        {
            Verify.assertNotEmpty("mutableMapIterable", actualMutableMapIterable);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@link MutableMapIterable} is <em>not</em> empty.
     */
    public static void assertNotEmpty(String mutableMapIterableName, MutableMapIterable<?, ?> actualMutableMapIterable)
    {
        try
        {
            Verify.assertObjectNotNull(mutableMapIterableName, actualMutableMapIterable);
            Assertions.assertFalse(Iterate.isEmpty(actualMutableMapIterable), mutableMapIterableName + " should be non-empty, but was empty");
            Assertions.assertTrue(Iterate.notEmpty(actualMutableMapIterable), mutableMapIterableName + " should be non-empty, but was empty");
            Assertions.assertTrue(actualMutableMapIterable.notEmpty(), mutableMapIterableName + " should be non-empty, but was empty");
            Assertions.assertNotEquals(0, actualMutableMapIterable.size(), mutableMapIterableName + " should be non-empty, but was empty");
            Assertions.assertNotEquals(0, actualMutableMapIterable.keySet().size(), mutableMapIterableName + " should be non-empty, but was empty");
            Assertions.assertNotEquals(0, actualMutableMapIterable.values().size(), mutableMapIterableName + " should be non-empty, but was empty");
            Assertions.assertNotEquals(0, actualMutableMapIterable.entrySet().size(), mutableMapIterableName + " should be non-empty, but was empty");
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@link PrimitiveIterable} is <em>not</em> empty.
     */
    public static void assertNotEmpty(PrimitiveIterable primitiveIterable)
    {
        try
        {
            Verify.assertNotEmpty("primitiveIterable", primitiveIterable);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@link PrimitiveIterable} is <em>not</em> empty.
     */
    public static void assertNotEmpty(String iterableName, PrimitiveIterable primitiveIterable)
    {
        try
        {
            Verify.assertObjectNotNull(iterableName, primitiveIterable);
            Assertions.assertFalse(primitiveIterable.isEmpty(), iterableName + " should be non-empty, but was empty");
            Assertions.assertTrue(primitiveIterable.notEmpty(), iterableName + " should be non-empty, but was empty");
            Assertions.assertNotEquals(0, primitiveIterable.size(), iterableName + " should be non-empty, but was empty");
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@link Iterable} is <em>not</em> empty.
     */
    public static void assertIterableNotEmpty(Iterable<?> iterable)
    {
        try
        {
            Verify.assertIterableNotEmpty("iterable", iterable);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@link Iterable} is <em>not</em> empty.
     */
    public static void assertIterableNotEmpty(String iterableName, Iterable<?> iterable)
    {
        try
        {
            Verify.assertObjectNotNull(iterableName, iterable);
            Assertions.assertFalse(Iterate.isEmpty(iterable), iterableName + " should be non-empty, but was empty");
            Assertions.assertTrue(Iterate.notEmpty(iterable), iterableName + " should be non-empty, but was empty");
            Assertions.assertNotEquals(0, Iterate.sizeOf(iterable), iterableName + " should be non-empty, but was empty");
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@link Map} is <em>not</em> empty.
     */
    public static void assertNotEmpty(Map<?, ?> actualMap)
    {
        try
        {
            Verify.assertNotEmpty("map", actualMap);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@link Map} is <em>not</em> empty.
     */
    public static void assertNotEmpty(String mapName, Map<?, ?> actualMap)
    {
        try
        {
            Verify.assertObjectNotNull(mapName, actualMap);
            Assertions.assertFalse(actualMap.isEmpty(), mapName + " should be non-empty, but was empty");
            Assertions.assertNotEquals(0, actualMap.size(), mapName + " should be non-empty, but was empty");
            Assertions.assertNotEquals(0, actualMap.keySet().size(), mapName + " should be non-empty, but was empty");
            Assertions.assertNotEquals(0, actualMap.values().size(), mapName + " should be non-empty, but was empty");
            Assertions.assertNotEquals(0, actualMap.entrySet().size(), mapName + " should be non-empty, but was empty");
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@link Multimap} is <em>not</em> empty.
     */
    public static void assertNotEmpty(Multimap<?, ?> actualMultimap)
    {
        try
        {
            Verify.assertNotEmpty("multimap", actualMultimap);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@link Multimap} is <em>not</em> empty.
     */
    public static void assertNotEmpty(String multimapName, Multimap<?, ?> actualMultimap)
    {
        try
        {
            Verify.assertObjectNotNull(multimapName, actualMultimap);
            Assertions.assertTrue(actualMultimap.notEmpty(), multimapName + " should be non-empty, but was empty");
            Assertions.assertFalse(actualMultimap.isEmpty(), multimapName + " should be non-empty, but was empty");
            Assertions.assertNotEquals(0, actualMultimap.size(), multimapName + " should be non-empty, but was empty");
            Assertions.assertNotEquals(0, actualMultimap.sizeDistinct(), multimapName + " should be non-empty, but was empty");
            Assertions.assertNotEquals(0, actualMultimap.keyBag().size(), multimapName + " should be non-empty, but was empty");
            Assertions.assertNotEquals(0, actualMultimap.keysView().size(), multimapName + " should be non-empty, but was empty");
            Assertions.assertNotEquals(0, actualMultimap.valuesView().size(), multimapName + " should be non-empty, but was empty");
            Assertions.assertNotEquals(0, actualMultimap.keyValuePairsView().size(), multimapName + " should be non-empty, but was empty");
            Assertions.assertNotEquals(0, actualMultimap.keyMultiValuePairsView().size(), multimapName + " should be non-empty, but was empty");
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    public static <T> void assertNotEmpty(String itemsName, T[] items)
    {
        try
        {
            Verify.assertObjectNotNull(itemsName, items);
            Verify.assertNotEquals(itemsName, 0, items.length);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    public static <T> void assertNotEmpty(T[] items)
    {
        try
        {
            Verify.assertNotEmpty("items", items);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert the size of the given array.
     */
    public static void assertSize(int expectedSize, Object[] actualArray)
    {
        try
        {
            Verify.assertSize("array", expectedSize, actualArray);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert the size of the given array.
     */
    public static void assertSize(String arrayName, int expectedSize, Object[] actualArray)
    {
        try
        {
            Assertions.assertNotNull(actualArray, arrayName + " should not be null");

            int actualSize = actualArray.length;
            if (actualSize != expectedSize)
            {
                Assertions.fail("Incorrect size for "
                        + arrayName
                        + "; expected:<"
                        + expectedSize
                        + "> but was:<"
                        + actualSize
                        + '>');
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert the size of the given {@link Iterable}.
     */
    public static void assertSize(int expectedSize, Iterable<?> actualIterable)
    {
        try
        {
            Verify.assertSize("iterable", expectedSize, actualIterable);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert the size of the given {@link Iterable}.
     */
    public static void assertSize(
            String iterableName,
            int expectedSize,
            Iterable<?> actualIterable)
    {
        try
        {
            Verify.assertObjectNotNull(iterableName, actualIterable);

            int actualSize = Iterate.sizeOf(actualIterable);
            if (actualSize != expectedSize)
            {
                Assertions.fail("Incorrect size for "
                        + iterableName
                        + "; expected:<"
                        + expectedSize
                        + "> but was:<"
                        + actualSize
                        + '>');
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert the size of the given {@link PrimitiveIterable}.
     */
    public static void assertSize(int expectedSize, PrimitiveIterable primitiveIterable)
    {
        try
        {
            Verify.assertSize("primitiveIterable", expectedSize, primitiveIterable);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert the size of the given {@link PrimitiveIterable}.
     */
    public static void assertSize(
            String primitiveIterableName,
            int expectedSize,
            PrimitiveIterable actualPrimitiveIterable)
    {
        try
        {
            Verify.assertObjectNotNull(primitiveIterableName, actualPrimitiveIterable);

            int actualSize = actualPrimitiveIterable.size();
            if (actualSize != expectedSize)
            {
                Assertions.fail("Incorrect size for "
                        + primitiveIterableName
                        + "; expected:<"
                        + expectedSize
                        + "> but was:<"
                        + actualSize
                        + '>');
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert the size of the given {@link Iterable}.
     */
    public static void assertIterableSize(int expectedSize, Iterable<?> actualIterable)
    {
        try
        {
            Verify.assertIterableSize("iterable", expectedSize, actualIterable);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert the size of the given {@link Iterable}.
     */
    public static void assertIterableSize(
            String iterableName,
            int expectedSize,
            Iterable<?> actualIterable)
    {
        try
        {
            Verify.assertObjectNotNull(iterableName, actualIterable);

            int actualSize = Iterate.sizeOf(actualIterable);
            if (actualSize != expectedSize)
            {
                Assertions.fail("Incorrect size for "
                        + iterableName
                        + "; expected:<"
                        + expectedSize
                        + "> but was:<"
                        + actualSize
                        + '>');
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert the size of the given {@link Map}.
     */
    public static void assertSize(String mapName, int expectedSize, Map<?, ?> actualMap)
    {
        try
        {
            Verify.assertSize(mapName, expectedSize, actualMap.keySet());
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert the size of the given {@link Map}.
     */
    public static void assertSize(int expectedSize, Map<?, ?> actualMap)
    {
        try
        {
            Verify.assertSize("map", expectedSize, actualMap);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert the size of the given {@link Multimap}.
     */
    public static void assertSize(int expectedSize, Multimap<?, ?> actualMultimap)
    {
        try
        {
            Verify.assertSize("multimap", expectedSize, actualMultimap);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert the size of the given {@link Multimap}.
     */
    public static void assertSize(String multimapName, int expectedSize, Multimap<?, ?> actualMultimap)
    {
        try
        {
            int actualSize = actualMultimap.size();
            if (actualSize != expectedSize)
            {
                Assertions.fail("Incorrect size for "
                        + multimapName
                        + "; expected:<"
                        + expectedSize
                        + "> but was:<"
                        + actualSize
                        + '>');
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert the size of the given {@link MutableMapIterable}.
     */
    public static void assertSize(int expectedSize, MutableMapIterable<?, ?> mutableMapIterable)
    {
        try
        {
            Verify.assertSize("map", expectedSize, mutableMapIterable);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert the size of the given {@link MutableMapIterable}.
     */
    public static void assertSize(String mapName, int expectedSize, MutableMapIterable<?, ?> mutableMapIterable)
    {
        try
        {
            int actualSize = mutableMapIterable.size();
            if (actualSize != expectedSize)
            {
                Assertions.fail("Incorrect size for " + mapName + "; expected:<" + expectedSize + "> but was:<" + actualSize + '>');
            }
            int keySetSize = mutableMapIterable.keySet().size();
            if (keySetSize != expectedSize)
            {
                Assertions.fail("Incorrect size for " + mapName + ".keySet(); expected:<" + expectedSize + "> but was:<" + actualSize + '>');
            }
            int valuesSize = mutableMapIterable.values().size();
            if (valuesSize != expectedSize)
            {
                Assertions.fail("Incorrect size for " + mapName + ".values(); expected:<" + expectedSize + "> but was:<" + actualSize + '>');
            }
            int entrySetSize = mutableMapIterable.entrySet().size();
            if (entrySetSize != expectedSize)
            {
                Assertions.fail("Incorrect size for " + mapName + ".entrySet(); expected:<" + expectedSize + "> but was:<" + actualSize + '>');
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert the size of the given {@link ImmutableSet}.
     */
    public static void assertSize(int expectedSize, ImmutableSet<?> actualImmutableSet)
    {
        try
        {
            Verify.assertSize("immutable set", expectedSize, actualImmutableSet);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert the size of the given {@link ImmutableSet}.
     */
    public static void assertSize(String immutableSetName, int expectedSize, ImmutableSet<?> actualImmutableSet)
    {
        try
        {
            int actualSize = actualImmutableSet.size();
            if (actualSize != expectedSize)
            {
                Assertions.fail("Incorrect size for "
                        + immutableSetName
                        + "; expected:<"
                        + expectedSize
                        + "> but was:<"
                        + actualSize
                        + '>');
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@code stringToFind} is contained within the {@code stringToSearch}.
     */
    public static void assertContains(String stringToFind, String stringToSearch)
    {
        try
        {
            Verify.assertContains("string", stringToFind, stringToSearch);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@code unexpectedString} is <em>not</em> contained within the {@code stringToSearch}.
     */
    public static void assertNotContains(String unexpectedString, String stringToSearch)
    {
        try
        {
            Verify.assertNotContains("string", unexpectedString, stringToSearch);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@code stringToFind} is contained within the {@code stringToSearch}.
     */
    public static void assertContains(String stringName, String stringToFind, String stringToSearch)
    {
        try
        {
            Assertions.assertNotNull("stringToFind should not be null", stringToFind);
            Assertions.assertNotNull("stringToSearch should not be null", stringToSearch);

            if (!stringToSearch.contains(stringToFind))
            {
                Assertions.fail(stringName
                        + " did not contain stringToFind:<"
                        + stringToFind
                        + "> in stringToSearch:<"
                        + stringToSearch
                        + '>');
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@code unexpectedString} is <em>not</em> contained within the {@code stringToSearch}.
     */
    public static void assertNotContains(String stringName, String unexpectedString, String stringToSearch)
    {
        try
        {
            Assertions.assertNotNull("unexpectedString should not be null", unexpectedString);
            Assertions.assertNotNull("stringToSearch should not be null", stringToSearch);

            if (stringToSearch.contains(unexpectedString))
            {
                Assertions.fail(stringName
                        + " contains unexpectedString:<"
                        + unexpectedString
                        + "> in stringToSearch:<"
                        + stringToSearch
                        + '>');
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    public static <T> void assertCount(
            int expectedCount,
            Iterable<T> iterable,
            Predicate<? super T> predicate)
    {
        Assertions.assertEquals(expectedCount, Iterate.count(iterable, predicate));
    }

    public static <T> void assertAllSatisfy(Iterable<T> iterable, Predicate<? super T> predicate)
    {
        try
        {
            Verify.assertAllSatisfy("The following items failed to satisfy the condition", iterable, predicate);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    public static <K, V> void assertAllSatisfy(Map<K, V> map, Predicate<? super V> predicate)
    {
        try
        {
            Verify.assertAllSatisfy(map.values(), predicate);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    public static <T> void assertAllSatisfy(String message, Iterable<T> iterable, Predicate<? super T> predicate)
    {
        try
        {
            MutableList<T> unacceptable = Iterate.reject(iterable, predicate, Lists.mutable.of());
            if (unacceptable.notEmpty())
            {
                Assertions.fail(message + " <" + unacceptable + '>');
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    public static <T> void assertAnySatisfy(Iterable<T> iterable, Predicate<? super T> predicate)
    {
        try
        {
            Verify.assertAnySatisfy("No items satisfied the condition", iterable, predicate);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    public static <K, V> void assertAnySatisfy(Map<K, V> map, Predicate<? super V> predicate)
    {
        try
        {
            Verify.assertAnySatisfy(map.values(), predicate);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    public static <T> void assertAnySatisfy(String message, Iterable<T> iterable, Predicate<? super T> predicate)
    {
        try
        {
            Assertions.assertTrue(Predicates.<T>anySatisfy(predicate).accept(iterable), message);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    public static <T> void assertNoneSatisfy(Iterable<T> iterable, Predicate<? super T> predicate)
    {
        try
        {
            Verify.assertNoneSatisfy("The following items satisfied the condition", iterable, predicate);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    public static <K, V> void assertNoneSatisfy(Map<K, V> map, Predicate<? super V> predicate)
    {
        try
        {
            Verify.assertNoneSatisfy(map.values(), predicate);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    public static <T> void assertNoneSatisfy(String message, Iterable<T> iterable, Predicate<? super T> predicate)
    {
        try
        {
            MutableList<T> unacceptable = Iterate.select(iterable, predicate, Lists.mutable.empty());
            if (unacceptable.notEmpty())
            {
                Assertions.fail(message + " <" + unacceptable + '>');
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@link Map} contains all of the given keys and values.
     */
    public static void assertContainsAllKeyValues(Map<?, ?> actualMap, Object... keyValues)
    {
        try
        {
            Verify.assertContainsAllKeyValues("map", actualMap, keyValues);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@link Map} contains all of the given keys and values.
     */
    public static void assertContainsAllKeyValues(
            String mapName,
            Map<?, ?> actualMap,
            Object... expectedKeyValues)
    {
        try
        {
            Verify.assertNotEmpty("Expected keys/values in assertion", expectedKeyValues);

            if (expectedKeyValues.length % 2 != 0)
            {
                Assertions.fail("Odd number of keys and values (every key must have a value)");
            }

            Verify.assertObjectNotNull(mapName, actualMap);
            Verify.assertMapContainsKeys(mapName, actualMap, expectedKeyValues);
            Verify.assertMapContainsValues(mapName, actualMap, expectedKeyValues);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@link MapIterable} contains all of the given keys and values.
     */
    public static void assertContainsAllKeyValues(MapIterable<?, ?> mapIterable, Object... keyValues)
    {
        try
        {
            Verify.assertContainsAllKeyValues("map", mapIterable, keyValues);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@link MapIterable} contains all of the given keys and values.
     */
    public static void assertContainsAllKeyValues(
            String mapIterableName,
            MapIterable<?, ?> mapIterable,
            Object... expectedKeyValues)
    {
        try
        {
            Verify.assertNotEmpty("Expected keys/values in assertion", expectedKeyValues);

            if (expectedKeyValues.length % 2 != 0)
            {
                Assertions.fail("Odd number of keys and values (every key must have a value)");
            }

            Verify.assertObjectNotNull(mapIterableName, mapIterable);
            Verify.assertMapContainsKeys(mapIterableName, mapIterable, expectedKeyValues);
            Verify.assertMapContainsValues(mapIterableName, mapIterable, expectedKeyValues);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@link MutableMapIterable} contains all of the given keys and values.
     */
    public static void assertContainsAllKeyValues(MutableMapIterable<?, ?> mutableMapIterable, Object... keyValues)
    {
        try
        {
            Verify.assertContainsAllKeyValues("map", mutableMapIterable, keyValues);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@link MutableMapIterable} contains all of the given keys and values.
     */
    public static void assertContainsAllKeyValues(
            String mutableMapIterableName,
            MutableMapIterable<?, ?> mutableMapIterable,
            Object... expectedKeyValues)
    {
        try
        {
            Verify.assertNotEmpty("Expected keys/values in assertion", expectedKeyValues);

            if (expectedKeyValues.length % 2 != 0)
            {
                Assertions.fail("Odd number of keys and values (every key must have a value)");
            }

            Verify.assertObjectNotNull(mutableMapIterableName, mutableMapIterable);
            Verify.assertMapContainsKeys(mutableMapIterableName, mutableMapIterable, expectedKeyValues);
            Verify.assertMapContainsValues(mutableMapIterableName, mutableMapIterable, expectedKeyValues);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@link ImmutableMapIterable} contains all of the given keys and values.
     */
    public static void assertContainsAllKeyValues(ImmutableMapIterable<?, ?> immutableMapIterable, Object... keyValues)
    {
        try
        {
            Verify.assertContainsAllKeyValues("map", immutableMapIterable, keyValues);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@link ImmutableMapIterable} contains all of the given keys and values.
     */
    public static void assertContainsAllKeyValues(
            String immutableMapIterableName,
            ImmutableMapIterable<?, ?> immutableMapIterable,
            Object... expectedKeyValues)
    {
        try
        {
            Verify.assertNotEmpty("Expected keys/values in assertion", expectedKeyValues);

            if (expectedKeyValues.length % 2 != 0)
            {
                Assertions.fail("Odd number of keys and values (every key must have a value)");
            }

            Verify.assertObjectNotNull(immutableMapIterableName, immutableMapIterable);
            Verify.assertMapContainsKeys(immutableMapIterableName, immutableMapIterable, expectedKeyValues);
            Verify.assertMapContainsValues(immutableMapIterableName, immutableMapIterable, expectedKeyValues);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@link Collection} contains the given item.
     */
    public static void assertContains(Object expectedItem, Collection<?> actualCollection)
    {
        try
        {
            Verify.assertContains("collection", expectedItem, actualCollection);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@link Collection} contains the given item.
     */
    public static void assertContains(
            String collectionName,
            Object expectedItem,
            Collection<?> actualCollection)
    {
        try
        {
            Verify.assertObjectNotNull(collectionName, actualCollection);

            if (!actualCollection.contains(expectedItem))
            {
                Assertions.fail(collectionName + " did not contain expectedItem:<" + expectedItem + '>');
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@link ImmutableCollection} contains the given item.
     */
    public static void assertContains(
            String immutableCollectionName,
            Object expectedItem,
            ImmutableCollection<?> actualImmutableCollection)
    {
        try
        {
            Verify.assertObjectNotNull(immutableCollectionName, actualImmutableCollection);

            if (!actualImmutableCollection.contains(expectedItem))
            {
                Assertions.fail(immutableCollectionName + " did not contain expectedItem:<" + expectedItem + '>');
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    public static void assertContainsAll(
            Iterable<?> iterable,
            Object... items)
    {
        try
        {
            Verify.assertContainsAll("iterable", iterable, items);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    public static void assertContainsAll(
            String collectionName,
            Iterable<?> iterable,
            Object... items)
    {
        try
        {
            Verify.assertObjectNotNull(collectionName, iterable);

            Verify.assertNotEmpty("Expected items in assertion", items);

            Predicate<Object> containsPredicate = each -> Iterate.contains(iterable, each);

            if (!ArrayIterate.allSatisfy(items, containsPredicate))
            {
                ImmutableList<Object> result = Lists.immutable.of(items).newWithoutAll(iterable);
                Assertions.fail(collectionName + " did not contain these items" + ":<" + result + '>');
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    public static void assertListsEqual(List<?> expectedList, List<?> actualList)
    {
        try
        {
            Verify.assertListsEqual("list", expectedList, actualList);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    public static void assertListsEqual(String listName, List<?> expectedList, List<?> actualList)
    {
        try
        {
            if (expectedList == null && actualList == null)
            {
                return;
            }
            Assertions.assertNotNull(expectedList);
            Assertions.assertNotNull(actualList);
            Assertions.assertEquals(expectedList.size(), actualList.size(), listName + " size");
            for (int index = 0; index < actualList.size(); index++)
            {
                Object eachExpected = expectedList.get(index);
                Object eachActual = actualList.get(index);
                if (!Objects.equals(eachExpected, eachActual))
                {
                    Assertions.fail(eachExpected + "\n\n" + eachActual + "\n\n" +  listName + " first differed at element [" + index + "];");
                }
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    public static void assertSetsEqual(Set<?> expectedSet, Set<?> actualSet)
    {
        try
        {
            Verify.assertSetsEqual("set", expectedSet, actualSet);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    public static void assertSetsEqual(String setName, Set<?> expectedSet, Set<?> actualSet)
    {
        try
        {
            if (expectedSet == null)
            {
                Assertions.assertNull(actualSet, setName + " should be null");
                return;
            }

            Verify.assertObjectNotNull(setName, actualSet);
            Verify.assertSize(setName, expectedSet.size(), actualSet);

            if (!actualSet.equals(expectedSet))
            {
                MutableSet<?> inExpectedOnlySet = UnifiedSet.newSet(expectedSet);
                inExpectedOnlySet.removeAll(actualSet);

                int numberDifferences = inExpectedOnlySet.size();
                String message = setName + ": " + numberDifferences + " elements different.";

                if (numberDifferences > MAX_DIFFERENCES)
                {
                    Assertions.fail(message);
                }

                MutableSet<?> inActualOnlySet = UnifiedSet.newSet(actualSet);
                inActualOnlySet.removeAll(expectedSet);

                //noinspection UseOfObsoleteAssert
                Assertions.fail(inExpectedOnlySet.makeString() + "\n\n" + inActualOnlySet +  "\n\n" + message);
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    public static void assertSortedSetsEqual(SortedSet<?> expectedSet, SortedSet<?> actualSet)
    {
        try
        {
            Verify.assertSortedSetsEqual("sortedSets", expectedSet, actualSet);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    public static void assertSortedSetsEqual(String setName, SortedSet<?> expectedSet, SortedSet<?> actualSet)
    {
        try
        {
            Assertions.assertEquals(expectedSet, actualSet, setName);
            Verify.assertIterablesEqual(setName, expectedSet, actualSet);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    public static void assertSortedBagsEqual(SortedBag<?> expectedBag, SortedBag<?> actualBag)
    {
        try
        {
            Verify.assertSortedBagsEqual("sortedBags", expectedBag, actualBag);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    public static void assertSortedBagsEqual(String bagName, SortedBag<?> expectedBag, SortedBag<?> actualBag)
    {
        try
        {
            Assertions.assertEquals(expectedBag, actualBag, bagName);
            Verify.assertIterablesEqual(bagName, expectedBag, actualBag);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    public static void assertSortedMapsEqual(String mapName, SortedMapIterable<?, ?> expectedMap, SortedMapIterable<?, ?> actualMap)
    {
        try
        {
            Assertions.assertEquals(expectedMap, actualMap, mapName);
            Verify.assertIterablesEqual(mapName, expectedMap, actualMap);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    public static void assertIterablesEqual(Iterable<?> expectedIterable, Iterable<?> actualIterable)
    {
        try
        {
            Verify.assertIterablesEqual("iterables", expectedIterable, actualIterable);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    public static void assertIterablesEqual(String iterableName, Iterable<?> expectedIterable, Iterable<?> actualIterable)
    {
        try
        {
            if (expectedIterable == null)
            {
                Assertions.assertNull(actualIterable, iterableName + " should be null");
                return;
            }

            Verify.assertObjectNotNull(iterableName, actualIterable);

            if (expectedIterable instanceof InternalIterable<?> && actualIterable instanceof InternalIterable<?>)
            {
                MutableList<Object> expectedList = FastList.newList();
                MutableList<Object> actualList = FastList.newList();
                ((InternalIterable<?>) expectedIterable).forEach(CollectionAddProcedure.on(expectedList));
                ((InternalIterable<?>) actualIterable).forEach(CollectionAddProcedure.on(actualList));
                Verify.assertListsEqual(iterableName, expectedList, actualList);
            }
            else
            {
                Iterator<?> expectedIterator = expectedIterable.iterator();
                Iterator<?> actualIterator = actualIterable.iterator();
                int index = 0;

                while (expectedIterator.hasNext() && actualIterator.hasNext())
                {
                    Object eachExpected = expectedIterator.next();
                    Object eachActual = actualIterator.next();

                    if (!Objects.equals(eachExpected, eachActual))
                    {
                        //noinspection UseOfObsoleteAssert
                        Assertions.fail(eachExpected.toString() + "\n\n" + eachActual.toString() + "\n\n" + iterableName + " first differed at element [" + index + "];");
                    }
                    index++;
                }

                Assertions.assertFalse(expectedIterator.hasNext(), "Actual " + iterableName + " had " + index + " elements but expected " + iterableName + " had more.");
                Assertions.assertFalse(actualIterator.hasNext(), "Expected " + iterableName + " had " + index + " elements but actual " + iterableName + " had more.");
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    public static void assertMapsEqual(Map<?, ?> expectedMap, Map<?, ?> actualMap)
    {
        try
        {
            Verify.assertMapsEqual("map", expectedMap, actualMap);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    public static void assertMapsEqual(String mapName, Map<?, ?> expectedMap, Map<?, ?> actualMap)
    {
        try
        {
            if (expectedMap == null)
            {
                Assertions.assertNull(actualMap, mapName + " should be null");
                return;
            }

            Assertions.assertNotNull(actualMap, mapName + " should be null");

            Set<? extends Map.Entry<?, ?>> expectedEntries = expectedMap.entrySet();
            for (Map.Entry<?, ?> expectedEntry : expectedEntries)
            {
                Object expectedKey = expectedEntry.getKey();
                Object expectedValue = expectedEntry.getValue();
                Object actualValue = actualMap.get(expectedKey);
                if (!Objects.equals(actualValue, expectedValue))
                {
                    Assertions.fail("Values differ at key " + expectedKey + " expected " + expectedValue + " but was " + actualValue);
                }
            }
            Verify.assertSetsEqual(mapName + " keys", expectedMap.keySet(), actualMap.keySet());
            Verify.assertSetsEqual(mapName + " entries", expectedMap.entrySet(), actualMap.entrySet());
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    public static void assertBagsEqual(Bag<?> expectedBag, Bag<?> actualBag)
    {
        try
        {
            Verify.assertBagsEqual("bag", expectedBag, actualBag);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    public static void assertBagsEqual(String bagName, Bag<?> expectedBag, Bag<?> actualBag)
    {
        try
        {
            if (expectedBag == null)
            {
                Assertions.assertNull(actualBag, bagName + " should be null");
                return;
            }

            Assertions.assertNotNull(actualBag, bagName + " should not be null");

            Assertions.assertEquals(expectedBag.size(), actualBag.size(), bagName + " size");
            Assertions.assertEquals(expectedBag.sizeDistinct(), actualBag.sizeDistinct(), bagName + " sizeDistinct");

            expectedBag.forEachWithOccurrences((expectedKey, expectedValue) ->
            {
                int actualValue = actualBag.occurrencesOf(expectedKey);
                Assertions.assertEquals(expectedValue, actualValue, "Occurrences of " + expectedKey);
            });
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    public static <K, V> void assertListMultimapsEqual(String multimapName, ListMultimap<K, V> expectedListMultimap, ListMultimap<K, V> actualListMultimap)
    {
        try
        {
            if (expectedListMultimap == null)
            {
                Assertions.assertNull(actualListMultimap, multimapName + " should be null");
                return;
            }

            Assertions.assertNotNull(actualListMultimap, multimapName + " should not be null");

            Assertions.assertEquals(expectedListMultimap.size(), actualListMultimap.size(), multimapName + " size");
            Verify.assertBagsEqual(multimapName + " keyBag", expectedListMultimap.keyBag(), actualListMultimap.keyBag());

            for (K key : expectedListMultimap.keysView())
            {
                Verify.assertListsEqual(multimapName + " value list for key:" + key, (List<V>) expectedListMultimap.get(key), (List<V>) actualListMultimap.get(key));
            }
            Assertions.assertEquals(expectedListMultimap, actualListMultimap, multimapName);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    public static <K, V> void assertSetMultimapsEqual(String multimapName, SetMultimap<K, V> expectedSetMultimap, SetMultimap<K, V> actualSetMultimap)
    {
        try
        {
            if (expectedSetMultimap == null)
            {
                Assertions.assertNull(actualSetMultimap,multimapName + " should be null");
                return;
            }

            Assertions.assertNotNull(actualSetMultimap, multimapName + " should not be null");

            Assertions.assertEquals(expectedSetMultimap.size(), actualSetMultimap.size(), multimapName + " size");
            Verify.assertBagsEqual(multimapName + " keyBag", expectedSetMultimap.keyBag(), actualSetMultimap.keyBag());

            for (K key : expectedSetMultimap.keysView())
            {
                Verify.assertSetsEqual(multimapName + " value set for key:" + key, (Set<V>) expectedSetMultimap.get(key), (Set<V>) actualSetMultimap.get(key));
            }
            Assertions.assertEquals(expectedSetMultimap, actualSetMultimap, multimapName);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }
    public static <K, V> void assertBagMultimapsEqual(String multimapName, BagMultimap<K, V> expectedBagMultimap, BagMultimap<K, V> actualBagMultimap)
    {
        try
        {
            if (expectedBagMultimap == null)
            {
                Assertions.assertNull(actualBagMultimap, multimapName + " should be null");
                return;
            }

            Assertions.assertNotNull(actualBagMultimap, multimapName + " should not be null");

            Assertions.assertEquals(expectedBagMultimap.size(), actualBagMultimap.size(), multimapName + " size");
            Verify.assertBagsEqual(multimapName + " keyBag", expectedBagMultimap.keyBag(), actualBagMultimap.keyBag());

            for (K key : expectedBagMultimap.keysView())
            {
                Verify.assertBagsEqual(multimapName + " value bag for key:" + key, expectedBagMultimap.get(key), actualBagMultimap.get(key));
            }
            Assertions.assertEquals(expectedBagMultimap, actualBagMultimap, multimapName);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    public static <K, V> void assertSortedSetMultimapsEqual(String multimapName, SortedSetMultimap<K, V> expectedSortedSetMultimap, SortedSetMultimap<K, V> actualSortedSetMultimap)
    {
        try
        {
            if (expectedSortedSetMultimap == null)
            {
                Assertions.assertNull(actualSortedSetMultimap, multimapName + " should be null");
                return;
            }

            Assertions.assertNotNull(actualSortedSetMultimap, multimapName + " should not be null");

            Assertions.assertEquals(expectedSortedSetMultimap.size(), actualSortedSetMultimap.size(), multimapName + " size");
            Verify.assertBagsEqual(multimapName + " keyBag", expectedSortedSetMultimap.keyBag(), actualSortedSetMultimap.keyBag());

            for (K key : expectedSortedSetMultimap.keysView())
            {
                Verify.assertSortedSetsEqual(multimapName + " value set for key:" + key, (SortedSet<V>) expectedSortedSetMultimap.get(key), (SortedSet<V>) actualSortedSetMultimap.get(key));
            }
            Assertions.assertEquals(expectedSortedSetMultimap, actualSortedSetMultimap, multimapName);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    public static <K, V> void assertSortedBagMultimapsEqual(String multimapName, SortedBagMultimap<K, V> expectedSortedBagMultimap, SortedBagMultimap<K, V> actualSortedBagMultimap)
    {
        try
        {
            if (expectedSortedBagMultimap == null)
            {
                Assertions.assertNull(actualSortedBagMultimap, multimapName + " should be null");
                return;
            }

            Assertions.assertNotNull(actualSortedBagMultimap, multimapName + " should not be null");

            Assertions.assertEquals(expectedSortedBagMultimap.size(), actualSortedBagMultimap.size(), multimapName + " size");
            Verify.assertBagsEqual(multimapName + " keyBag", expectedSortedBagMultimap.keyBag(), actualSortedBagMultimap.keyBag());

            for (K key : expectedSortedBagMultimap.keysView())
            {
                Verify.assertSortedBagsEqual(multimapName + " value set for key:" + key, expectedSortedBagMultimap.get(key), actualSortedBagMultimap.get(key));
            }
            Assertions.assertEquals(expectedSortedBagMultimap, actualSortedBagMultimap, multimapName);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    private static void assertMapContainsKeys(
            String mapName,
            Map<?, ?> actualMap,
            Object... expectedKeyValues)
    {
        try
        {
            Verify.assertNotEmpty("Expected keys/values in assertion", expectedKeyValues);

            MutableList<Object> expectedKeys = Lists.mutable.of();
            for (int i = 0; i < expectedKeyValues.length; i += 2)
            {
                expectedKeys.add(expectedKeyValues[i]);
            }

            Verify.assertContainsAll(mapName + ".keySet()", actualMap.keySet(), expectedKeys.toArray());
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    private static void assertMapContainsValues(
            String mapName,
            Map<?, ?> actualMap,
            Object... expectedKeyValues)
    {
        try
        {
            Verify.assertNotEmpty("Expected keys/values in assertion", expectedKeyValues);

            MutableMap<Object, String> missingEntries = UnifiedMap.newMap();
            int i = 0;
            while (i < expectedKeyValues.length)
            {
                Object expectedKey = expectedKeyValues[i++];
                Object expectedValue = expectedKeyValues[i++];
                Object actualValue = actualMap.get(expectedKey);
                if (!Objects.equals(expectedValue, actualValue))
                {
                    missingEntries.put(
                            expectedKey,
                            "expectedValue:<" + expectedValue + ">, actualValue:<" + actualValue + '>');
                }
            }
            if (!missingEntries.isEmpty())
            {
                StringBuilder buf = new StringBuilder(mapName + " has incorrect values for keys:[");
                for (Map.Entry<Object, String> expectedEntry : missingEntries.entrySet())
                {
                    buf.append("key:<")
                            .append(expectedEntry.getKey())
                            .append(',')
                            .append(expectedEntry.getValue())
                            .append("> ");
                }
                buf.append(']');
                Assertions.fail(buf.toString());
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    private static void assertMapContainsKeys(
            String mapIterableName,
            MapIterable<?, ?> mapIterable,
            Object... expectedKeyValues)
    {
        try
        {
            Verify.assertNotEmpty("Expected keys/values in assertion", expectedKeyValues);

            MutableList<Object> expectedKeys = Lists.mutable.of();
            for (int i = 0; i < expectedKeyValues.length; i += 2)
            {
                expectedKeys.add(expectedKeyValues[i]);
            }

            Verify.assertContainsAll(mapIterableName + ".keysView()", mapIterable.keysView(), expectedKeys.toArray());
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    private static void assertMapContainsValues(
            String mapIterableName,
            MapIterable<?, ?> mapIterable,
            Object... expectedKeyValues)
    {
        try
        {
            Verify.assertNotEmpty("Expected keys/values in assertion", expectedKeyValues);

            MutableList<Object> expectedValues = Lists.mutable.of();
            for (int i = 1; i < expectedKeyValues.length; i += 2)
            {
                expectedValues.add(expectedKeyValues[i]);
            }

            Verify.assertContainsAll(mapIterableName + ".valuesView()", mapIterable.valuesView(), expectedValues.toArray());
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    private static void assertMapContainsKeys(
            String mutableMapIterableName,
            MutableMapIterable<?, ?> mutableMapIterable,
            Object... expectedKeyValues)
    {
        try
        {
            Verify.assertNotEmpty("Expected keys/values in assertion", expectedKeyValues);

            MutableList<Object> expectedKeys = Lists.mutable.of();
            for (int i = 0; i < expectedKeyValues.length; i += 2)
            {
                expectedKeys.add(expectedKeyValues[i]);
            }

            Verify.assertContainsAll(mutableMapIterableName + ".keysView()", mutableMapIterable.keysView(), expectedKeys.toArray());
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    private static void assertMapContainsValues(
            String mutableMapIterableName,
            MutableMapIterable<?, ?> mutableMapIterable,
            Object... expectedKeyValues)
    {
        try
        {
            Verify.assertNotEmpty("Expected keys/values in assertion", expectedKeyValues);

            MutableList<Object> expectedValues = Lists.mutable.of();
            for (int i = 1; i < expectedKeyValues.length; i += 2)
            {
                expectedValues.add(expectedKeyValues[i]);
            }

            Verify.assertContainsAll(mutableMapIterableName + ".valuesView()", mutableMapIterable.valuesView(), expectedValues.toArray());
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    private static void assertMapContainsKeys(
            String immutableMapIterableName,
            ImmutableMapIterable<?, ?> immutableMapIterable,
            Object... expectedKeyValues)
    {
        try
        {
            Verify.assertNotEmpty("Expected keys/values in assertion", expectedKeyValues);

            MutableList<Object> expectedKeys = Lists.mutable.of();
            for (int i = 0; i < expectedKeyValues.length; i += 2)
            {
                expectedKeys.add(expectedKeyValues[i]);
            }

            Verify.assertContainsAll(immutableMapIterableName + ".keysView()", immutableMapIterable.keysView(), expectedKeys.toArray());
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    private static void assertMapContainsValues(
            String immutableMapIterableName,
            ImmutableMapIterable<?, ?> immutableMapIterable,
            Object... expectedKeyValues)
    {
        try
        {
            Verify.assertNotEmpty("Expected keys/values in assertion", expectedKeyValues);

            MutableList<Object> expectedValues = Lists.mutable.of();
            for (int i = 1; i < expectedKeyValues.length; i += 2)
            {
                expectedValues.add(expectedKeyValues[i]);
            }

            Verify.assertContainsAll(immutableMapIterableName + ".valuesView()", immutableMapIterable.valuesView(), expectedValues.toArray());
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@link Multimap} contains an entry with the given key and value.
     */
    public static <K, V> void assertContainsEntry(
            K expectedKey,
            V expectedValue,
            Multimap<K, V> actualMultimap)
    {
        try
        {
            Verify.assertContainsEntry("multimap", expectedKey, expectedValue, actualMultimap);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@link Multimap} contains an entry with the given key and value.
     */
    public static <K, V> void assertContainsEntry(
            String multimapName,
            K expectedKey,
            V expectedValue,
            Multimap<K, V> actualMultimap)
    {
        try
        {
            Assertions.assertNotNull(actualMultimap, multimapName);

            if (!actualMultimap.containsKeyAndValue(expectedKey, expectedValue))
            {
                Assertions.fail(multimapName + " did not contain entry: <" + expectedKey + ", " + expectedValue + '>');
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert the given {@link Multimap} contains all of the given keys and values.
     */
    public static void assertContainsAllEntries(Multimap<?, ?> actualMultimap, Object... keyValues)
    {
        try
        {
            Verify.assertContainsAllEntries("multimap", actualMultimap, keyValues);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert the given {@link Multimap} contains all of the given keys and values.
     */
    public static void assertContainsAllEntries(
            String multimapName,
            Multimap<?, ?> actualMultimap,
            Object... expectedKeyValues)
    {
        try
        {
            Verify.assertNotEmpty("Expected keys/values in assertion", expectedKeyValues);

            if (expectedKeyValues.length % 2 != 0)
            {
                Assertions.fail("Odd number of keys and values (every key must have a value)");
            }

            Verify.assertObjectNotNull(multimapName, actualMultimap);

            MutableList<Map.Entry<?, ?>> missingEntries = Lists.mutable.of();
            for (int i = 0; i < expectedKeyValues.length; i += 2)
            {
                Object expectedKey = expectedKeyValues[i];
                Object expectedValue = expectedKeyValues[i + 1];

                if (!actualMultimap.containsKeyAndValue(expectedKey, expectedValue))
                {
                    missingEntries.add(new ImmutableEntry<>(expectedKey, expectedValue));
                }
            }

            if (!missingEntries.isEmpty())
            {
                Assertions.fail(multimapName + " is missing entries: " + missingEntries);
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    public static void denyContainsAny(
            String collectionName,
            Collection<?> actualCollection,
            Object... items)
    {
        try
        {
            Verify.assertNotEmpty("Expected items in assertion", items);

            Verify.assertObjectNotNull(collectionName, actualCollection);

            MutableSet<Object> intersection = Sets.intersect(UnifiedSet.newSet(actualCollection), UnifiedSet.newSetWith(items));
            if (intersection.notEmpty())
            {
                Assertions.fail(collectionName
                        + " has an intersection with these items and should not :<" + intersection + '>');
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@link Map} contains an entry with the given key.
     */
    public static void assertContainsKey(Object expectedKey, Map<?, ?> actualMap)
    {
        try
        {
            Verify.assertContainsKey("map", expectedKey, actualMap);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@link Map} contains an entry with the given key.
     */
    public static void assertContainsKey(String mapName, Object expectedKey, Map<?, ?> actualMap)
    {
        try
        {
            Assertions.assertNotNull(actualMap, mapName);

            if (!actualMap.containsKey(expectedKey))
            {
                Assertions.fail(mapName + " did not contain expectedKey:<" + expectedKey + '>');
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@link MapIterable} contains an entry with the given key.
     */
    public static void assertContainsKey(Object expectedKey, MapIterable<?, ?> mapIterable)
    {
        try
        {
            Verify.assertContainsKey("map", expectedKey, mapIterable);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@link MapIterable} contains an entry with the given key.
     */
    public static void assertContainsKey(
            String mapIterableName,
            Object expectedKey,
            MapIterable<?, ?> mapIterable)
    {
        try
        {
            Assertions.assertNotNull(mapIterable, mapIterableName);

            if (!mapIterable.containsKey(expectedKey))
            {
                Assertions.fail(mapIterableName + " did not contain expectedKey:<" + expectedKey + '>');
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@link MutableMapIterable} contains an entry with the given key.
     */
    public static void assertContainsKey(Object expectedKey, MutableMapIterable<?, ?> mutableMapIterable)
    {
        try
        {
            Verify.assertContainsKey("map", expectedKey, mutableMapIterable);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@link MutableMapIterable} contains an entry with the given key.
     */
    public static void assertContainsKey(
            String mutableMapIterableName,
            Object expectedKey,
            MutableMapIterable<?, ?> mutableMapIterable)
    {
        try
        {
            Assertions.assertNotNull(mutableMapIterable, mutableMapIterableName);

            if (!mutableMapIterable.containsKey(expectedKey))
            {
                Assertions.fail(mutableMapIterableName + " did not contain expectedKey:<" + expectedKey + '>');
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@link ImmutableMapIterable} contains an entry with the given key.
     */
    public static void assertContainsKey(Object expectedKey, ImmutableMapIterable<?, ?> immutableMapIterable)
    {
        try
        {
            Verify.assertContainsKey("map", expectedKey, immutableMapIterable);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@link ImmutableMapIterable} contains an entry with the given key.
     */
    public static void assertContainsKey(
            String immutableMapIterableName,
            Object expectedKey,
            ImmutableMapIterable<?, ?> immutableMapIterable)
    {
        try
        {
            Assertions.assertNotNull(immutableMapIterable, immutableMapIterableName);

            if (!immutableMapIterable.containsKey(expectedKey))
            {
                Assertions.fail(immutableMapIterableName + " did not contain expectedKey:<" + expectedKey + '>');
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Deny that the given {@link Map} contains an entry with the given key.
     */
    public static void denyContainsKey(Object unexpectedKey, Map<?, ?> actualMap)
    {
        try
        {
            Verify.denyContainsKey("map", unexpectedKey, actualMap);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Deny that the given {@link Map} contains an entry with the given key.
     */
    public static void denyContainsKey(String mapName, Object unexpectedKey, Map<?, ?> actualMap)
    {
        try
        {
            Assertions.assertNotNull(actualMap, mapName);

            if (actualMap.containsKey(unexpectedKey))
            {
                Assertions.fail(mapName + " contained unexpectedKey:<" + unexpectedKey + '>');
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@link Map} contains an entry with the given key and value.
     */
    public static void assertContainsKeyValue(
            Object expectedKey,
            Object expectedValue,
            Map<?, ?> actualMap)
    {
        try
        {
            Verify.assertContainsKeyValue("map", expectedKey, expectedValue, actualMap);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@link Map} contains an entry with the given key and value.
     */
    public static void assertContainsKeyValue(
            String mapName,
            Object expectedKey,
            Object expectedValue,
            Map<?, ?> actualMap)
    {
        try
        {
            Verify.assertContainsKey(mapName, expectedKey, actualMap);

            Object actualValue = actualMap.get(expectedKey);
            if (!Objects.equals(actualValue, expectedValue))
            {
                Assertions.fail(
                        mapName
                                + " entry with expectedKey:<"
                                + expectedKey
                                + "> "
                                + "did not contain expectedValue:<"
                                + expectedValue
                                + ">, "
                                + "but had actualValue:<"
                                + actualValue
                                + '>');
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@link MapIterable} contains an entry with the given key and value.
     */
    public static void assertContainsKeyValue(
            Object expectedKey,
            Object expectedValue,
            MapIterable<?, ?> mapIterable)
    {
        try
        {
            Verify.assertContainsKeyValue("map", expectedKey, expectedValue, mapIterable);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@link MapIterable} contains an entry with the given key and value.
     */
    public static void assertContainsKeyValue(
            String mapIterableName,
            Object expectedKey,
            Object expectedValue,
            MapIterable<?, ?> mapIterable)
    {
        try
        {
            Verify.assertContainsKey(mapIterableName, expectedKey, mapIterable);

            Object actualValue = mapIterable.get(expectedKey);
            if (!Objects.equals(actualValue, expectedValue))
            {
                Assertions.fail(
                        mapIterableName
                                + " entry with expectedKey:<"
                                + expectedKey
                                + "> "
                                + "did not contain expectedValue:<"
                                + expectedValue
                                + ">, "
                                + "but had actualValue:<"
                                + actualValue
                                + '>');
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@link MutableMapIterable} contains an entry with the given key and value.
     */
    public static void assertContainsKeyValue(
            Object expectedKey,
            Object expectedValue,
            MutableMapIterable<?, ?> mapIterable)
    {
        try
        {
            Verify.assertContainsKeyValue("map", expectedKey, expectedValue, mapIterable);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@link MutableMapIterable} contains an entry with the given key and value.
     */
    public static void assertContainsKeyValue(
            String mapIterableName,
            Object expectedKey,
            Object expectedValue,
            MutableMapIterable<?, ?> mutableMapIterable)
    {
        try
        {
            Verify.assertContainsKey(mapIterableName, expectedKey, mutableMapIterable);

            Object actualValue = mutableMapIterable.get(expectedKey);
            if (!Objects.equals(actualValue, expectedValue))
            {
                Assertions.fail(
                        mapIterableName
                                + " entry with expectedKey:<"
                                + expectedKey
                                + "> "
                                + "did not contain expectedValue:<"
                                + expectedValue
                                + ">, "
                                + "but had actualValue:<"
                                + actualValue
                                + '>');
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@link ImmutableMapIterable} contains an entry with the given key and value.
     */
    public static void assertContainsKeyValue(
            Object expectedKey,
            Object expectedValue,
            ImmutableMapIterable<?, ?> mapIterable)
    {
        try
        {
            Verify.assertContainsKeyValue("map", expectedKey, expectedValue, mapIterable);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@link ImmutableMapIterable} contains an entry with the given key and value.
     */
    public static void assertContainsKeyValue(
            String mapIterableName,
            Object expectedKey,
            Object expectedValue,
            ImmutableMapIterable<?, ?> immutableMapIterable)
    {
        try
        {
            Verify.assertContainsKey(mapIterableName, expectedKey, immutableMapIterable);

            Object actualValue = immutableMapIterable.get(expectedKey);
            if (!Objects.equals(actualValue, expectedValue))
            {
                Assertions.fail(
                        mapIterableName
                                + " entry with expectedKey:<"
                                + expectedKey
                                + "> "
                                + "did not contain expectedValue:<"
                                + expectedValue
                                + ">, "
                                + "but had actualValue:<"
                                + actualValue
                                + '>');
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@link Collection} does <em>not</em> contain the given item.
     */
    public static void assertNotContains(Object unexpectedItem, Collection<?> actualCollection)
    {
        try
        {
            Verify.assertNotContains("collection", unexpectedItem, actualCollection);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@link Collection} does <em>not</em> contain the given item.
     */
    public static void assertNotContains(
            String collectionName,
            Object unexpectedItem,
            Collection<?> actualCollection)
    {
        try
        {
            Verify.assertObjectNotNull(collectionName, actualCollection);

            if (actualCollection.contains(unexpectedItem))
            {
                Assertions.fail(collectionName + " should not contain unexpectedItem:<" + unexpectedItem + '>');
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@link Iterable} does <em>not</em> contain the given item.
     */
    public static void assertNotContains(Object unexpectedItem, Iterable<?> iterable)
    {
        try
        {
            Verify.assertNotContains("iterable", unexpectedItem, iterable);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@link Iterable} does <em>not</em> contain the given item.
     */
    public static void assertNotContains(
            String collectionName,
            Object unexpectedItem,
            Iterable<?> iterable)
    {
        try
        {
            Verify.assertObjectNotNull(collectionName, iterable);

            if (Iterate.contains(iterable, unexpectedItem))
            {
                Assertions.fail(collectionName + " should not contain unexpectedItem:<" + unexpectedItem + '>');
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@link Collection} does <em>not</em> contain the given item.
     */
    public static void assertNotContainsKey(Object unexpectedKey, Map<?, ?> actualMap)
    {
        try
        {
            Verify.assertNotContainsKey("map", unexpectedKey, actualMap);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@link Collection} does <em>not</em> contain the given item.
     */
    public static void assertNotContainsKey(String mapName, Object unexpectedKey, Map<?, ?> actualMap)
    {
        try
        {
            Verify.assertObjectNotNull(mapName, actualMap);

            if (actualMap.containsKey(unexpectedKey))
            {
                Assertions.fail(mapName + " should not contain unexpectedItem:<" + unexpectedKey + '>');
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the formerItem appears before the latterItem in the given {@link Collection}.
     * Both the formerItem and the latterItem must appear in the collection, or this assert will fail.
     */
    public static void assertBefore(Object formerItem, Object latterItem, List<?> actualList)
    {
        try
        {
            Verify.assertBefore("list", formerItem, latterItem, actualList);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the formerItem appears before the latterItem in the given {@link Collection}.
     * {@link #assertContains(String, Object, Collection)} will be called for both the formerItem and the
     * latterItem, prior to the "before" assertion.
     */
    public static void assertBefore(
            String listName,
            Object formerItem,
            Object latterItem,
            List<?> actualList)
    {
        try
        {
            Verify.assertObjectNotNull(listName, actualList);
            Verify.assertNotEquals(
                    "Bad test, formerItem and latterItem are equal, listName:<" + listName + '>',
                    formerItem,
                    latterItem);
            Verify.assertContainsAll(listName, actualList, formerItem, latterItem);
            int formerPosition = actualList.indexOf(formerItem);
            int latterPosition = actualList.indexOf(latterItem);
            if (latterPosition < formerPosition)
            {
                Assertions.fail("Items in "
                        + listName
                        + " are in incorrect order; "
                        + "expected formerItem:<"
                        + formerItem
                        + "> "
                        + "to appear before latterItem:<"
                        + latterItem
                        + ">, but didn't");
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    public static void assertObjectNotNull(String objectName, Object actualObject)
    {
        try
        {
            Assertions.assertNotNull(actualObject, objectName + " should not be null");
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@code item} is at the {@code index} in the given {@link List}.
     */
    public static void assertItemAtIndex(Object expectedItem, int index, List<?> list)
    {
        try
        {
            Verify.assertItemAtIndex("list", expectedItem, index, list);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@code item} is at the {@code index} in the given {@code array}.
     */
    public static void assertItemAtIndex(Object expectedItem, int index, Object[] array)
    {
        try
        {
            Verify.assertItemAtIndex("array", expectedItem, index, array);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    public static <T> void assertStartsWith(T[] array, T... items)
    {
        try
        {
            Verify.assertNotEmpty("Expected items in assertion", items);

            for (int i = 0; i < items.length; i++)
            {
                T item = items[i];
                Verify.assertItemAtIndex("array", item, i, array);
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    public static <T> void assertStartsWith(List<T> list, T... items)
    {
        try
        {
            Verify.assertStartsWith("list", list, items);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    public static <T> void assertStartsWith(String listName, List<T> list, T... items)
    {
        try
        {
            Verify.assertNotEmpty("Expected items in assertion", items);

            for (int i = 0; i < items.length; i++)
            {
                T item = items[i];
                Verify.assertItemAtIndex(listName, item, i, list);
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    public static <T> void assertEndsWith(List<T> list, T... items)
    {
        try
        {
            Verify.assertNotEmpty("Expected items in assertion", items);

            for (int i = 0; i < items.length; i++)
            {
                T item = items[i];
                Verify.assertItemAtIndex("list", item, list.size() - items.length + i, list);
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    public static <T> void assertEndsWith(T[] array, T... items)
    {
        try
        {
            Verify.assertNotEmpty("Expected items in assertion", items);

            for (int i = 0; i < items.length; i++)
            {
                T item = items[i];
                Verify.assertItemAtIndex("array", item, array.length - items.length + i, array);
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@code item} is at the {@code index} in the given {@link List}.
     */
    public static void assertItemAtIndex(
            String listName,
            Object expectedItem,
            int index,
            List<?> list)
    {
        try
        {
            Verify.assertObjectNotNull(listName, list);

            Object actualItem = list.get(index);
            if (!Objects.equals(expectedItem, actualItem))
            {
                Assertions.assertEquals(
                        expectedItem,
                        actualItem,
                        listName + " has incorrect element at index:<" + index + '>');
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@code item} is at the {@code index} in the given {@link List}.
     */
    public static void assertItemAtIndex(
            String arrayName,
            Object expectedItem,
            int index,
            Object[] array)
    {
        try
        {
            Assertions.assertNotNull(array);
            Object actualItem = array[index];
            if (!Objects.equals(expectedItem, actualItem))
            {
                Assertions.assertEquals(
                        expectedItem,
                        actualItem,
                        arrayName + " has incorrect element at index:<" + index + '>'
                );
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    public static void assertPostSerializedEqualsAndHashCode(Object object)
    {
        try
        {
            Object deserialized = SerializeTestHelper.serializeDeserialize(object);
            Verify.assertEqualsAndHashCode("objects", object, deserialized);
            Assertions.assertNotSame(object, deserialized, "not same object");
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    public static void assertPostSerializedEqualsHashCodeAndToString(Object object)
    {
        try
        {
            Object deserialized = SerializeTestHelper.serializeDeserialize(object);
            Verify.assertEqualsAndHashCode("objects", object, deserialized);
            Assertions.assertNotSame(object, deserialized, "not same object");
            Assertions.assertEquals("not same toString", object.toString(), deserialized.toString());
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    public static void assertPostSerializedIdentity(Object object)
    {
        try
        {
            Object deserialized = SerializeTestHelper.serializeDeserialize(object);
            Verify.assertEqualsAndHashCode("objects", object, deserialized);
            Assertions.assertSame(object, deserialized, "same object");
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    public static void assertSerializedForm(String expectedBase64Form, Object actualObject)
    {
        try
        {
            Verify.assertInstanceOf(Serializable.class, actualObject);
            Assertions.assertEquals(
                    "Serialization was broken.",
                    expectedBase64Form,
                    Verify.encodeObject(actualObject));
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    public static void assertSerializedForm(
            long expectedSerialVersionUID,
            String expectedBase64Form,
            Object actualObject)
    {
        try
        {
            Verify.assertInstanceOf(Serializable.class, actualObject);

            Assertions.assertEquals(
                    "Serialization was broken.",
                    expectedBase64Form,
                    Verify.encodeObject(actualObject));

            Object decodeToObject = Verify.decodeObject(expectedBase64Form);

            Assertions.assertEquals(
                    expectedSerialVersionUID,
                    ObjectStreamClass.lookup(decodeToObject.getClass()).getSerialVersionUID(),
                    "serialVersionUID's differ"
            );
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    public static void assertDeserializedForm(String expectedBase64Form, Object actualObject)
    {
        try
        {
            Verify.assertInstanceOf(Serializable.class, actualObject);

            Object decodeToObject = Verify.decodeObject(expectedBase64Form);
            Assertions.assertEquals(decodeToObject, actualObject, "Serialization was broken.");
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    private static Object decodeObject(String expectedBase64Form)
    {
        try
        {
            byte[] bytes = DECODER.decode(expectedBase64Form);
            return new ObjectInputStream(new ByteArrayInputStream(bytes)).readObject();
        }
        catch (IOException | ClassNotFoundException e)
        {
            throw new AssertionError(e);
        }
    }

    private static String encodeObject(Object actualObject)
    {
        try
        {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);
            objectOutputStream.writeObject(actualObject);
            objectOutputStream.flush();
            objectOutputStream.close();

            String string = ENCODER.encodeToString(byteArrayOutputStream.toByteArray());
            return Verify.addFinalNewline(string);
        }
        catch (IOException e)
        {
            throw new AssertionError(e);
        }
    }

    private static String addFinalNewline(String string)
    {
        if (string.length() % 77 == 76)
        {
            return string + '\n';
        }
        return string;
    }

    public static void assertNotSerializable(Object actualObject)
    {
        try
        {
            Verify.assertThrows(NotSerializableException.class, () ->
            {
                new ObjectOutputStream(new ByteArrayOutputStream()).writeObject(actualObject);
                return null;
            });
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that {@code objectA} and {@code objectB} are equal (via the {@link Object#equals(Object)} method,
     * and that they both return the same {@link Object#hashCode()}.
     */
    public static void assertEqualsAndHashCode(Object objectA, Object objectB)
    {
        try
        {
            Verify.assertEqualsAndHashCode("objects", objectA, objectB);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Asserts that a value is negative.
     */
    public static void assertNegative(int value)
    {
        try
        {
            Assertions.assertTrue(value < 0, value + " is not negative");
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Asserts that a value is positive.
     */
    public static void assertPositive(int value)
    {
        try
        {
            Assertions.assertTrue(value > 0, value + " is not positive");
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Asserts that a value is positive.
     */
    public static void assertZero(int value)
    {
        try
        {
            Assertions.assertEquals(0, value);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that {@code objectA} and {@code objectB} are equal (via the {@link Object#equals(Object)} method,
     * and that they both return the same {@link Object#hashCode()}.
     */
    public static void assertEqualsAndHashCode(String itemNames, Object objectA, Object objectB)
    {
        try
        {
            if (objectA == null || objectB == null)
            {
                Assertions.fail("Neither item should be null: <" + objectA + "> <" + objectB + '>');
            }

            Assertions.assertFalse(objectA.equals(null), "Neither item should equal null");
            Assertions.assertFalse(objectB.equals(null), "Neither item should equal null");
            Verify.assertNotEquals("Neither item should equal new Object()", objectA.equals(new Object()));
            Verify.assertNotEquals("Neither item should equal new Object()", objectB.equals(new Object()));
            Assertions.assertEquals(objectA, objectA, "Expected " + itemNames + " to be equal.");
            Assertions.assertEquals(objectB, objectB, "Expected " + itemNames + " to be equal.");
            Assertions.assertEquals(objectA, objectB, "Expected " + itemNames + " to be equal.");
            Assertions.assertEquals(objectB, objectA, "Expected " + itemNames + " to be equal.");
            Assertions.assertEquals(
                    objectA.hashCode(),
                    objectB.hashCode(),
                    "Expected " + itemNames + " to have the same hashCode()."
                    );
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * @deprecated since 8.2.0 as will not work with Java 9
     */
    @Deprecated
    public static void assertShallowClone(Cloneable object)
    {
        try
        {
            Verify.assertShallowClone("object", object);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * @deprecated since 8.2.0 as will not work with Java 9
     */
    @Deprecated
    public static void assertShallowClone(String itemName, Cloneable object)
    {
        try
        {
            Method method = Object.class.getDeclaredMethod("clone", (Class<?>[]) null);
            method.setAccessible(true);
            Object clone = method.invoke(object);
            String prefix = itemName + " and its clone";
            Assertions.assertNotSame(object, clone, prefix);
            Verify.assertEqualsAndHashCode(prefix, object, clone);
        }
        catch (IllegalArgumentException | IllegalAccessException | NoSuchMethodException | SecurityException | InvocationTargetException e)
        {
            throw new AssertionError(e.getLocalizedMessage());
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    public static <T> void assertClassNonInstantiable(Class<T> aClass)
    {
        try
        {
            try
            {
                aClass.newInstance();
                Assertions.fail("Expected class '" + aClass + "' to be non-instantiable");
            }
            catch (InstantiationException e)
            {
                // pass
            }
            catch (IllegalAccessException e)
            {
                if (Verify.canInstantiateThroughReflection(aClass))
                {
                    Assertions.fail("Expected constructor of non-instantiable class '" + aClass + "' to throw an exception, but didn't");
                }
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    private static <T> boolean canInstantiateThroughReflection(Class<T> aClass)
    {
        try
        {
            Constructor<T> declaredConstructor = aClass.getDeclaredConstructor();
            declaredConstructor.setAccessible(true);
            declaredConstructor.newInstance();
            return true;
        }
        catch (NoSuchMethodException | AssertionError | IllegalAccessException | InstantiationException | InvocationTargetException e)
        {
            return false;
        }
    }

    public static void assertError(Class<? extends Error> expectedErrorClass, Runnable code)
    {
        try
        {
            code.run();
        }
        catch (Error ex)
        {
            try
            {
                Assertions.assertSame(
                        expectedErrorClass,
                        ex.getClass(),
                        "Caught error of type <"
                                + ex.getClass().getName()
                                + ">, expected one of type <"
                                + expectedErrorClass.getName()
                                + '>'
                        );
                return;
            }
            catch (AssertionError e)
            {
                Verify.throwMangledException(e);
            }
        }

        try
        {
            Assertions.fail("Block did not throw an error of type " + expectedErrorClass.getName());
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Runs the {@link Callable} {@code code} and asserts that it throws an {@code Exception} of the type
     * {@code expectedExceptionClass}.
     * <p>
     * {@code Callable} is most appropriate when a checked exception will be thrown.
     * If a subclass of {@link RuntimeException} will be thrown, the form
     * {@link #assertThrows(Class, Runnable)} may be more convenient.
     * <p>
     * e.g.
     * <pre>
     * Verify.<b>assertThrows</b>(StringIndexOutOfBoundsException.class, new Callable&lt;String&gt;()
     * {
     *    public String call() throws Exception
     *    {
     *        return "Craig".substring(42, 3);
     *    }
     * });
     * </pre>
     *
     * @see #assertThrows(Class, Runnable)
     */
    public static void assertThrows(
            Class<? extends Exception> expectedExceptionClass,
            Callable<?> code)
    {
        try
        {
            code.call();
        }
        catch (Exception ex)
        {
            try
            {
                Assertions.assertSame(
                        expectedExceptionClass,
                        ex.getClass(),
                        "Caught exception of type <"
                                + ex.getClass().getName()
                                + ">, expected one of type <"
                                + expectedExceptionClass.getName()
                                + '>'
                                + '\n'
                                + "Exception Message: " + ex.getMessage()
                                + '\n'
                        );
                return;
            }
            catch (AssertionError e)
            {
                Verify.throwMangledException(e);
            }
        }

        try
        {
            Assertions.fail("Block did not throw an exception of type " + expectedExceptionClass.getName());
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Runs the {@link Runnable} {@code code} and asserts that it throws an {@code Exception} of the type
     * {@code expectedExceptionClass}.
     * <p>
     * {@code Runnable} is most appropriate when a subclass of {@link RuntimeException} will be thrown.
     * If a checked exception will be thrown, the form {@link #assertThrows(Class, Callable)} may be more
     * convenient.
     * <p>
     * e.g.
     * <pre>
     * Verify.<b>assertThrows</b>(NullPointerException.class, new Runnable()
     * {
     *    public void run()
     *    {
     *        final Integer integer = null;
     *        LOGGER.info(integer.toString());
     *    }
     * });
     * </pre>
     *
     * @see #assertThrows(Class, Callable)
     */
    public static void assertThrows(
            Class<? extends Exception> expectedExceptionClass,
            Runnable code)
    {
        try
        {
            code.run();
        }
        catch (RuntimeException ex)
        {
            try
            {
                Assertions.assertSame(
                        expectedExceptionClass,
                        ex.getClass(),
                        "Caught exception of type <"
                                + ex.getClass().getName()
                                + ">, expected one of type <"
                                + expectedExceptionClass.getName()
                                + '>'
                                + '\n'
                                + "Exception Message: " + ex.getMessage()
                                + '\n'
                        );
                return;
            }
            catch (AssertionError e)
            {
                Verify.throwMangledException(e);
            }
        }

        try
        {
            Assertions.fail("Block did not throw an exception of type " + expectedExceptionClass.getName());
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Runs the {@link Callable} {@code code} and asserts that it throws an {@code Exception} of the type
     * {@code expectedExceptionClass}, which contains a cause of type expectedCauseClass.
     * <p>
     * {@code Callable} is most appropriate when a checked exception will be thrown.
     * If a subclass of {@link RuntimeException} will be thrown, the form
     * {@link #assertThrowsWithCause(Class, Class, Runnable)} may be more convenient.
     * <p>
     * e.g.
     * <pre>
     * Verify.assertThrowsWithCause(RuntimeException.class, IOException.class, new Callable&lt;Void&gt;()
     * {
     *    public Void call() throws Exception
     *    {
     *        try
     *        {
     *            new File("").createNewFile();
     *        }
     *        catch (final IOException e)
     *        {
     *            throw new RuntimeException("Uh oh!", e);
     *        }
     *        return null;
     *    }
     * });
     * </pre>
     *
     * @see #assertThrowsWithCause(Class, Class, Runnable)
     */
    public static void assertThrowsWithCause(
            Class<? extends Exception> expectedExceptionClass,
            Class<? extends Throwable> expectedCauseClass,
            Callable<?> code)
    {
        try
        {
            code.call();
        }
        catch (Exception ex)
        {
            try
            {
                Assertions.assertSame(
                        expectedExceptionClass,
                        ex.getClass(),
                        "Caught exception of type <"
                                + ex.getClass().getName()
                                + ">, expected one of type <"
                                + expectedExceptionClass.getName()
                                + '>'
                        );
                Throwable actualCauseClass = ex.getCause();
                Assertions.assertNotNull(
                        actualCauseClass,
                        "Caught exception with null cause, expected cause of type <"
                                + expectedCauseClass.getName()
                                + '>'
                        );
                Assertions.assertSame(
                        expectedCauseClass,
                        actualCauseClass.getClass(),
                        "Caught exception with cause of type<"
                                + actualCauseClass.getClass().getName()
                                + ">, expected cause of type <"
                                + expectedCauseClass.getName()
                                + '>'
                        );
                return;
            }
            catch (AssertionError e)
            {
                Verify.throwMangledException(e);
            }
        }

        try
        {
            Assertions.fail("Block did not throw an exception of type " + expectedExceptionClass.getName());
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Runs the {@link Runnable} {@code code} and asserts that it throws an {@code Exception} of the type
     * {@code expectedExceptionClass}, which contains a cause of type expectedCauseClass.
     * <p>
     * {@code Runnable} is most appropriate when a subclass of {@link RuntimeException} will be thrown.
     * If a checked exception will be thrown, the form {@link #assertThrowsWithCause(Class, Class, Callable)}
     * may be more convenient.
     * <p>
     * e.g.
     * <pre>
     * Verify.assertThrowsWithCause(RuntimeException.class, StringIndexOutOfBoundsException.class, new Runnable()
     * {
     *    public void run()
     *    {
     *        try
     *        {
     *            LOGGER.info("Craig".substring(42, 3));
     *        }
     *        catch (final StringIndexOutOfBoundsException e)
     *        {
     *            throw new RuntimeException("Uh oh!", e);
     *        }
     *    }
     * });
     * </pre>
     *
     * @see #assertThrowsWithCause(Class, Class, Callable)
     */
    public static void assertThrowsWithCause(
            Class<? extends Exception> expectedExceptionClass,
            Class<? extends Throwable> expectedCauseClass,
            Runnable code)
    {
        try
        {
            code.run();
        }
        catch (RuntimeException ex)
        {
            try
            {
                Assertions.assertSame(
                        expectedExceptionClass,
                        ex.getClass(),
                        "Caught exception of type <"
                                + ex.getClass().getName()
                                + ">, expected one of type <"
                                + expectedExceptionClass.getName()
                                + '>'
                );
                Throwable actualCauseClass = ex.getCause();
                Assertions.assertNotNull(
                        actualCauseClass,
                        "Caught exception with null cause, expected cause of type <"
                                + expectedCauseClass.getName()
                                + '>'
                        );
                Assertions.assertSame(
                        expectedCauseClass,
                        actualCauseClass.getClass(),
                        "Caught exception with cause of type<"
                                + actualCauseClass.getClass().getName()
                                + ">, expected cause of type <"
                                + expectedCauseClass.getName()
                                + '>'
                        );
                return;
            }
            catch (AssertionError e)
            {
                Verify.throwMangledException(e);
            }
        }

        try
        {
            Assertions.fail("Block did not throw an exception of type " + expectedExceptionClass.getName());
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }
}
