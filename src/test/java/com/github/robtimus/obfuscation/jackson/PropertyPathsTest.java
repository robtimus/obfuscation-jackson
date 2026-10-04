/*
 * PropertyPathsTest.java
 * Copyright 2026 Rob Spoor
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

package com.github.robtimus.obfuscation.jackson;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Answers;
import com.github.robtimus.obfuscation.jackson.JSONObfuscator.PropertyPath;

@SuppressWarnings("nls")
class PropertyPathsTest {

    @Nested
    class Is {

        @Test
        void testExpectedShorterThanPath() {
            PropertyPath.Matcher matcher = PropertyPath.is("a", "b");

            PropertyPath path = newPath("a", "b", "c");

            assertFalse(matcher.test(path));
        }

        @Test
        void testExpectedEqualsPath() {
            PropertyPath.Matcher matcher = PropertyPath.is("a", "b", "c");

            PropertyPath path = newPath("a", "b", "c");

            assertTrue(matcher.test(path));
        }

        @Test
        void testExpectedLargerThanPath() {
            PropertyPath.Matcher matcher = PropertyPath.is("a", "b", "c");

            PropertyPath path = newPath("a", "b");

            assertFalse(matcher.test(path));
        }

        @Test
        void testMismatchAtFirst() {
            PropertyPath.Matcher matcher = PropertyPath.is("a", "b", "c");

            PropertyPath path = newPath("A", "b", "c");

            assertFalse(matcher.test(path));
        }

        @Test
        void testMismatchAtLast() {
            PropertyPath.Matcher matcher = PropertyPath.is("a", "b", "c");

            PropertyPath path = newPath("a", "b", "C");

            assertFalse(matcher.test(path));
        }

        @Test
        void testToString() {
            PropertyPath.Matcher matcher = PropertyPath.is("a", "b", "c.d");

            assertEquals("is(a.b.\"c.d\")", matcher.toString());
        }
    }

    @Nested
    class IsIgnoreCase {

        @Test
        void testExpectedShorterThanPath() {
            PropertyPath.Matcher matcher = PropertyPath.isIgnoreCase("a", "b");

            PropertyPath path = newPath("a", "b", "c");

            assertFalse(matcher.test(path));
        }

        @Test
        void testExpectedEqualsPath() {
            PropertyPath.Matcher matcher = PropertyPath.isIgnoreCase("a", "b", "c");

            PropertyPath path = newPath("a", "b", "c");

            assertTrue(matcher.test(path));
        }

        @Test
        void testExpectedLargerThanPath() {
            PropertyPath.Matcher matcher = PropertyPath.isIgnoreCase("a", "b", "c");

            PropertyPath path = newPath("a", "b");

            assertFalse(matcher.test(path));
        }

        @Test
        void testCaseMismatchOnly() {
            PropertyPath.Matcher matcher = PropertyPath.isIgnoreCase("a", "b", "c");

            PropertyPath path = newPath("A", "B", "C");

            assertTrue(matcher.test(path));
        }

        @Test
        void testMismatchAtFirst() {
            PropertyPath.Matcher matcher = PropertyPath.isIgnoreCase("a", "b", "c");

            PropertyPath path = newPath("d", "b", "c");

            assertFalse(matcher.test(path));
        }

        @Test
        void testMismatchAtLast() {
            PropertyPath.Matcher matcher = PropertyPath.isIgnoreCase("a", "b", "c");

            PropertyPath path = newPath("a", "b", "d");

            assertFalse(matcher.test(path));
        }

        @Test
        void testToString() {
            PropertyPath.Matcher matcher = PropertyPath.isIgnoreCase("a", "b", "c.d");

            assertEquals("isIgnoreCase(a.b.\"c.d\")", matcher.toString());
        }
    }

    @Nested
    class StartsWith {

        @Test
        void testPrefixShorterThanPath() {
            PropertyPath.Matcher matcher = PropertyPath.startsWith("a", "b");

            PropertyPath path = newPath("a", "b", "c");

            assertTrue(matcher.test(path));
        }

        @Test
        void testPrefixEqualsPath() {
            PropertyPath.Matcher matcher = PropertyPath.startsWith("a", "b", "c");

            PropertyPath path = newPath("a", "b", "c");

            assertTrue(matcher.test(path));
        }

        @Test
        void testPrefixLargerThanPath() {
            PropertyPath.Matcher matcher = PropertyPath.startsWith("a", "b", "c");

            PropertyPath path = newPath("a", "b");

            assertFalse(matcher.test(path));
        }

        @Test
        void testMismatchAtFirst() {
            PropertyPath.Matcher matcher = PropertyPath.startsWith("a", "b", "c");

            PropertyPath path = newPath("A", "b", "c");

            assertFalse(matcher.test(path));
        }

        @Test
        void testMismatchAtLast() {
            PropertyPath.Matcher matcher = PropertyPath.startsWith("a", "b", "c");

            PropertyPath path = newPath("a", "b", "C");

            assertFalse(matcher.test(path));
        }

        @Test
        void testToString() {
            PropertyPath.Matcher matcher = PropertyPath.startsWith("a", "b", "c.d");

            assertEquals("startsWith(a.b.\"c.d\")", matcher.toString());
        }
    }

    @Nested
    class StartsWithIgnoreCase {

        @Test
        void testPrefixShorterThanPath() {
            PropertyPath.Matcher matcher = PropertyPath.startsWithIgnoreCase("a", "b");

            PropertyPath path = newPath("a", "b", "c");

            assertTrue(matcher.test(path));
        }

        @Test
        void testPrefixEqualsPath() {
            PropertyPath.Matcher matcher = PropertyPath.startsWithIgnoreCase("a", "b", "c");

            PropertyPath path = newPath("a", "b", "c");

            assertTrue(matcher.test(path));
        }

        @Test
        void testPrefixLargerThanPath() {
            PropertyPath.Matcher matcher = PropertyPath.startsWithIgnoreCase("a", "b", "c");

            PropertyPath path = newPath("a", "b");

            assertFalse(matcher.test(path));
        }

        @Test
        void testCaseMismatchOnly() {
            PropertyPath.Matcher matcher = PropertyPath.startsWithIgnoreCase("a", "b", "c");

            PropertyPath path = newPath("A", "B", "C");

            assertTrue(matcher.test(path));
        }

        @Test
        void testMismatchAtFirst() {
            PropertyPath.Matcher matcher = PropertyPath.startsWithIgnoreCase("a", "b", "c");

            PropertyPath path = newPath("d", "b", "c");

            assertFalse(matcher.test(path));
        }

        @Test
        void testMismatchAtLast() {
            PropertyPath.Matcher matcher = PropertyPath.startsWithIgnoreCase("a", "b", "c");

            PropertyPath path = newPath("a", "b", "d");

            assertFalse(matcher.test(path));
        }

        @Test
        void testToString() {
            PropertyPath.Matcher matcher = PropertyPath.startsWithIgnoreCase("a", "b", "c.d");

            assertEquals("startsWithIgnoreCase(a.b.\"c.d\")", matcher.toString());
        }
    }

    @Nested
    class EndsWith {

        @Test
        void testPostfixShorterThanPath() {
            PropertyPath.Matcher matcher = PropertyPath.endsWith("b", "c");

            PropertyPath path = newPath("a", "b", "c");

            assertTrue(matcher.test(path));
        }

        @Test
        void testPostfixEqualsPath() {
            PropertyPath.Matcher matcher = PropertyPath.endsWith("a", "b", "c");

            PropertyPath path = newPath("a", "b", "c");

            assertTrue(matcher.test(path));
        }

        @Test
        void testPostfixLargerThanPath() {
            PropertyPath.Matcher matcher = PropertyPath.endsWith("a", "b", "c");

            PropertyPath path = newPath("a", "b");

            assertFalse(matcher.test(path));
        }

        @Test
        void testMismatchAtFirst() {
            PropertyPath.Matcher matcher = PropertyPath.endsWith("a", "b", "c");

            PropertyPath path = newPath("A", "b", "c");

            assertFalse(matcher.test(path));
        }

        @Test
        void testMismatchAtLast() {
            PropertyPath.Matcher matcher = PropertyPath.endsWith("a", "b", "c");

            PropertyPath path = newPath("a", "b", "C");

            assertFalse(matcher.test(path));
        }

        @Test
        void testToString() {
            PropertyPath.Matcher matcher = PropertyPath.endsWith("a", "b", "c.d");

            assertEquals("endsWith(a.b.\"c.d\")", matcher.toString());
        }
    }

    @Nested
    class EndsWithIgnoreCase {

        @Test
        void testPostfixShorterThanPath() {
            PropertyPath.Matcher matcher = PropertyPath.endsWithIgnoreCase("b", "c");

            PropertyPath path = newPath("a", "b", "c");

            assertTrue(matcher.test(path));
        }

        @Test
        void testPostfixEqualsPath() {
            PropertyPath.Matcher matcher = PropertyPath.endsWithIgnoreCase("a", "b", "c");

            PropertyPath path = newPath("a", "b", "c");

            assertTrue(matcher.test(path));
        }

        @Test
        void testPostfixLargerThanPath() {
            PropertyPath.Matcher matcher = PropertyPath.endsWithIgnoreCase("a", "b", "c");

            PropertyPath path = newPath("a", "b");

            assertFalse(matcher.test(path));
        }

        @Test
        void testCaseMismatchOnly() {
            PropertyPath.Matcher matcher = PropertyPath.endsWithIgnoreCase("a", "b", "c");

            PropertyPath path = newPath("A", "B", "C");

            assertTrue(matcher.test(path));
        }

        @Test
        void testMismatchAtFirst() {
            PropertyPath.Matcher matcher = PropertyPath.endsWithIgnoreCase("a", "b", "c");

            PropertyPath path = newPath("d", "b", "c");

            assertFalse(matcher.test(path));
        }

        @Test
        void testMismatchAtLast() {
            PropertyPath.Matcher matcher = PropertyPath.endsWithIgnoreCase("a", "b", "c");

            PropertyPath path = newPath("a", "b", "d");

            assertFalse(matcher.test(path));
        }

        @Test
        void testToString() {
            PropertyPath.Matcher matcher = PropertyPath.endsWithIgnoreCase("a", "b", "c.d");

            assertEquals("endsWithIgnoreCase(a.b.\"c.d\")", matcher.toString());
        }
    }

    @Nested
    class ContainsAt {

        @Nested
        class ZeroIndex {

            @Test
            void testExpectedShorterThanPath() {
                PropertyPath.Matcher matcher = PropertyPath.containsAt(0, "a", "b");

                PropertyPath path = newPath("a", "b", "c");

                assertTrue(matcher.test(path));
            }

            @Test
            void testExpectedEqualsPath() {
                PropertyPath.Matcher matcher = PropertyPath.containsAt(0, "a", "b");

                PropertyPath path = newPath("a", "b");

                assertTrue(matcher.test(path));
            }

            @Test
            void testExpectedLargerThanPath() {
                PropertyPath.Matcher matcher = PropertyPath.containsAt(0, "a", "b", "c");

                PropertyPath path = newPath("a", "b");

                assertFalse(matcher.test(path));
            }

            @Test
            void testMismatchAtFirst() {
                PropertyPath.Matcher matcher = PropertyPath.containsAt(0, "a", "b");

                PropertyPath path = newPath("A", "b", "c");

                assertFalse(matcher.test(path));
            }

            @Test
            void testMismatchAtLast() {
                PropertyPath.Matcher matcher = PropertyPath.containsAt(0, "a", "b");

                PropertyPath path = newPath("a", "B", "c");

                assertFalse(matcher.test(path));
            }

            @Test
            void testToString() {
                PropertyPath.Matcher matcher = PropertyPath.containsAt(0, "a", "b.c");

                assertEquals("containsAt(0, a.\"b.c\")", matcher.toString());
            }
        }

        @Nested
        class PositiveIndex {

            @Test
            void testExpectedShorterThanPath() {
                PropertyPath.Matcher matcher = PropertyPath.containsAt(1, "b", "c");

                PropertyPath path = newPath("a", "b", "c", "d");

                assertTrue(matcher.test(path));
            }

            @Test
            void testExpectedEqualsPath() {
                PropertyPath.Matcher matcher = PropertyPath.containsAt(1, "b", "c");

                PropertyPath path = newPath("a", "b", "c");

                assertTrue(matcher.test(path));
            }

            @Test
            void testExpectedLargerThanPath() {
                PropertyPath.Matcher matcher = PropertyPath.containsAt(1, "b", "c");

                PropertyPath path = newPath("a", "b");

                assertFalse(matcher.test(path));
            }

            @Test
            void testMismatchAtFirst() {
                PropertyPath.Matcher matcher = PropertyPath.containsAt(1, "b", "c");

                PropertyPath path = newPath("a", "B", "c");

                assertFalse(matcher.test(path));
            }

            @Test
            void testMismatchAtLast() {
                PropertyPath.Matcher matcher = PropertyPath.containsAt(1, "b", "c");

                PropertyPath path = newPath("a", "b", "C");

                assertFalse(matcher.test(path));
            }

            @Test
            void testToString() {
                PropertyPath.Matcher matcher = PropertyPath.containsAt(1, "b", "c.d");

                assertEquals("containsAt(1, b.\"c.d\")", matcher.toString());
            }
        }

        @Nested
        class NegativeIndex {

            @Test
            void testExpectedShorterThanPath() {
                PropertyPath.Matcher matcher = PropertyPath.containsAt(-3, "b", "c");

                PropertyPath path = newPath("a", "b", "c", "d");

                assertTrue(matcher.test(path));
            }

            @Test
            void testExpectedEqualsPath() {
                PropertyPath.Matcher matcher = PropertyPath.containsAt(-2, "b", "c");

                PropertyPath path = newPath("a", "b", "c");

                assertTrue(matcher.test(path));
            }

            @Test
            void testExpectedLargerThanPath() {
                PropertyPath.Matcher matcher = PropertyPath.containsAt(-3, "b", "c");

                PropertyPath path = newPath("a", "b", "c");

                assertFalse(matcher.test(path));
            }

            @Test
            void testMismatchAtFirst() {
                PropertyPath.Matcher matcher = PropertyPath.containsAt(-2, "b", "c");

                PropertyPath path = newPath("a", "B", "c");

                assertFalse(matcher.test(path));
            }

            @Test
            void testMismatchAtLast() {
                PropertyPath.Matcher matcher = PropertyPath.containsAt(-2, "b", "c");

                PropertyPath path = newPath("a", "b", "C");

                assertFalse(matcher.test(path));
            }

            @Test
            void testToString() {
                PropertyPath.Matcher matcher = PropertyPath.containsAt(-2, "b", "c.d");

                assertEquals("containsAt(-2, b.\"c.d\")", matcher.toString());
            }
        }

        @Nested
        class TooHighIndex {

            @Test
            void testExpectedShorterThanPath() {
                PropertyPath path = newPath("a", "b", "c", "d");

                testMismatch(path);
            }

            @Test
            void testExpectedEqualsPath() {
                PropertyPath path = newPath("a", "b", "c");

                testMismatch(path);
            }

            @Test
            void testExpectedLargerThanPath() {
                PropertyPath path = newPath("a", "b");

                testMismatch(path);
            }

            private void testMismatch(PropertyPath path) {
                PropertyPath.Matcher matcher = PropertyPath.containsAt(10, "b", "c");

                assertFalse(matcher.test(path));
            }

            @Test
            void testToString() {
                PropertyPath.Matcher matcher = PropertyPath.containsAt(10, "b", "c.d");

                assertEquals("containsAt(10, b.\"c.d\")", matcher.toString());
            }
        }

        @Nested
        class TooLowIndex {

            @Test
            void testExpectedShorterThanPath() {
                PropertyPath path = newPath("a", "b", "c", "d");

                testMismatch(path);
            }

            @Test
            void testExpectedEqualsPath() {
                PropertyPath path = newPath("a", "b", "c");

                testMismatch(path);
            }

            @Test
            void testExpectedLargerThanPath() {
                PropertyPath path = newPath("a", "b");

                testMismatch(path);
            }

            private void testMismatch(PropertyPath path) {
                PropertyPath.Matcher matcher = PropertyPath.containsAt(-10, "a", "b");

                assertFalse(matcher.test(path));
            }

            @Test
            void testToString() {
                PropertyPath.Matcher matcher = PropertyPath.containsAt(-10, "a", "b.c");

                assertEquals("containsAt(-10, a.\"b.c\")", matcher.toString());
            }
        }
    }

    @Nested
    class ContainsAtIgnoreCase {

        @Nested
        class ZeroIndex {

            @Test
            void testExpectedShorterThanPath() {
                PropertyPath.Matcher matcher = PropertyPath.containsAtIgnoreCase(0, "a", "b");

                PropertyPath path = newPath("a", "b", "c");

                assertTrue(matcher.test(path));
            }

            @Test
            void testExpectedEqualsPath() {
                PropertyPath.Matcher matcher = PropertyPath.containsAtIgnoreCase(0, "a", "b");

                PropertyPath path = newPath("a", "b");

                assertTrue(matcher.test(path));
            }

            @Test
            void testExpectedLargerThanPath() {
                PropertyPath.Matcher matcher = PropertyPath.containsAtIgnoreCase(0, "a", "b", "c");

                PropertyPath path = newPath("a", "b");

                assertFalse(matcher.test(path));
            }

            @Test
            void testCaseMismatchOnly() {
                PropertyPath.Matcher matcher = PropertyPath.containsAtIgnoreCase(0, "a", "b", "c");

                PropertyPath path = newPath("A", "B", "C");

                assertTrue(matcher.test(path));
            }

            @Test
            void testMismatchAtFirst() {
                PropertyPath.Matcher matcher = PropertyPath.containsAtIgnoreCase(0, "a", "b");

                PropertyPath path = newPath("d", "b", "c");

                assertFalse(matcher.test(path));
            }

            @Test
            void testMismatchAtLast() {
                PropertyPath.Matcher matcher = PropertyPath.containsAtIgnoreCase(0, "a", "b");

                PropertyPath path = newPath("a", "d", "c");

                assertFalse(matcher.test(path));
            }

            @Test
            void testToString() {
                PropertyPath.Matcher matcher = PropertyPath.containsAtIgnoreCase(0, "a", "b.c");

                assertEquals("containsAtIgnoreCase(0, a.\"b.c\")", matcher.toString());
            }
        }

        @Nested
        class PositiveIndex {

            @Test
            void testExpectedShorterThanPath() {
                PropertyPath.Matcher matcher = PropertyPath.containsAtIgnoreCase(1, "b", "c");

                PropertyPath path = newPath("a", "b", "c", "d");

                assertTrue(matcher.test(path));
            }

            @Test
            void testExpectedEqualsPath() {
                PropertyPath.Matcher matcher = PropertyPath.containsAtIgnoreCase(1, "b", "c");

                PropertyPath path = newPath("a", "b", "c");

                assertTrue(matcher.test(path));
            }

            @Test
            void testExpectedLargerThanPath() {
                PropertyPath.Matcher matcher = PropertyPath.containsAtIgnoreCase(1, "b", "c");

                PropertyPath path = newPath("a", "b");

                assertFalse(matcher.test(path));
            }

            @Test
            void testCaseMismatchOnly() {
                PropertyPath.Matcher matcher = PropertyPath.containsAtIgnoreCase(1, "b", "c");

                PropertyPath path = newPath("A", "B", "C");

                assertTrue(matcher.test(path));
            }

            @Test
            void testMismatchAtFirst() {
                PropertyPath.Matcher matcher = PropertyPath.containsAtIgnoreCase(1, "b", "c");

                PropertyPath path = newPath("a", "d", "c");

                assertFalse(matcher.test(path));
            }

            @Test
            void testMismatchAtLast() {
                PropertyPath.Matcher matcher = PropertyPath.containsAtIgnoreCase(1, "b", "c");

                PropertyPath path = newPath("a", "b", "d");

                assertFalse(matcher.test(path));
            }

            @Test
            void testToString() {
                PropertyPath.Matcher matcher = PropertyPath.containsAtIgnoreCase(1, "b", "c.d");

                assertEquals("containsAtIgnoreCase(1, b.\"c.d\")", matcher.toString());
            }
        }

        @Nested
        class NegativeIndex {

            @Test
            void testExpectedShorterThanPath() {
                PropertyPath.Matcher matcher = PropertyPath.containsAtIgnoreCase(-3, "b", "c");

                PropertyPath path = newPath("a", "b", "c", "d");

                assertTrue(matcher.test(path));
            }

            @Test
            void testExpectedEqualsPath() {
                PropertyPath.Matcher matcher = PropertyPath.containsAtIgnoreCase(-2, "b", "c");

                PropertyPath path = newPath("a", "b", "c");

                assertTrue(matcher.test(path));
            }

            @Test
            void testExpectedLargerThanPath() {
                PropertyPath.Matcher matcher = PropertyPath.containsAtIgnoreCase(-3, "b", "c");

                PropertyPath path = newPath("a", "b", "c");

                assertFalse(matcher.test(path));
            }

            @Test
            void testCaseMismatchOnly() {
                PropertyPath.Matcher matcher = PropertyPath.containsAtIgnoreCase(-2, "b", "c");

                PropertyPath path = newPath("A", "B", "C");

                assertTrue(matcher.test(path));
            }

            @Test
            void testMismatchAtFirst() {
                PropertyPath.Matcher matcher = PropertyPath.containsAtIgnoreCase(-2, "b", "c");

                PropertyPath path = newPath("a", "d", "c");

                assertFalse(matcher.test(path));
            }

            @Test
            void testMismatchAtLast() {
                PropertyPath.Matcher matcher = PropertyPath.containsAtIgnoreCase(-2, "b", "c");

                PropertyPath path = newPath("a", "b", "d");

                assertFalse(matcher.test(path));
            }

            @Test
            void testToString() {
                PropertyPath.Matcher matcher = PropertyPath.containsAtIgnoreCase(-2, "b", "c.d");

                assertEquals("containsAtIgnoreCase(-2, b.\"c.d\")", matcher.toString());
            }
        }

        @Nested
        class TooHighIndex {

            @Test
            void testExpectedShorterThanPath() {
                PropertyPath path = newPath("a", "b", "c", "d");

                testMismatch(path);
            }

            @Test
            void testExpectedEqualsPath() {
                PropertyPath path = newPath("a", "b", "c");

                testMismatch(path);
            }

            @Test
            void testExpectedLargerThanPath() {
                PropertyPath path = newPath("a", "b");

                testMismatch(path);
            }

            private void testMismatch(PropertyPath path) {
                PropertyPath.Matcher matcher = PropertyPath.containsAtIgnoreCase(10, "b", "c");

                assertFalse(matcher.test(path));
            }

            @Test
            void testToString() {
                PropertyPath.Matcher matcher = PropertyPath.containsAtIgnoreCase(10, "b", "c.d");

                assertEquals("containsAtIgnoreCase(10, b.\"c.d\")", matcher.toString());
            }
        }

        @Nested
        class TooLowIndex {

            @Test
            void testExpectedShorterThanPath() {
                PropertyPath path = newPath("a", "b", "c", "d");

                testMismatch(path);
            }

            @Test
            void testExpectedEqualsPath() {
                PropertyPath path = newPath("a", "b", "c");

                testMismatch(path);
            }

            @Test
            void testExpectedLargerThanPath() {
                PropertyPath path = newPath("a", "b");

                testMismatch(path);
            }

            private void testMismatch(PropertyPath path) {
                PropertyPath.Matcher matcher = PropertyPath.containsAtIgnoreCase(-10, "a", "b");

                assertFalse(matcher.test(path));
            }

            @Test
            void testToString() {
                PropertyPath.Matcher matcher = PropertyPath.containsAtIgnoreCase(-10, "a", "b.c");

                assertEquals("containsAtIgnoreCase(-10, a.\"b.c\")", matcher.toString());
            }
        }
    }

    @Nested
    class And {

        @Test
        void testNoMatch() {
            PropertyPath.Matcher first = mock();
            when(first.test(any())).thenReturn(false);
            when(first.and(any())).thenAnswer(Answers.CALLS_REAL_METHODS);

            PropertyPath.Matcher second = mock();

            PropertyPath.Matcher matcher = first.and(second);

            PropertyPath path = newPath("a", "b", "c");

            assertFalse(matcher.test(path));

            verify(first).test(path);
            verify(first).and(second);
            verifyNoMoreInteractions(first, second);
        }

        @Test
        void testFirstMatches() {
            PropertyPath.Matcher first = mock();
            when(first.test(any())).thenReturn(true);
            when(first.and(any())).thenAnswer(Answers.CALLS_REAL_METHODS);

            PropertyPath.Matcher second = mock();
            when(second.test(any())).thenReturn(false);

            PropertyPath.Matcher matcher = first.and(second);

            PropertyPath path = newPath("a", "b", "c");

            assertFalse(matcher.test(path));

            verify(first).test(path);
            verify(first).and(second);
            verify(second).test(path);
            verifyNoMoreInteractions(first, second);
        }

        @Test
        void testBothMatch() {
            PropertyPath.Matcher first = mock();
            when(first.test(any())).thenReturn(true);
            when(first.and(any())).thenAnswer(Answers.CALLS_REAL_METHODS);

            PropertyPath.Matcher second = mock();
            when(second.test(any())).thenReturn(true);

            PropertyPath.Matcher matcher = first.and(second);

            PropertyPath path = newPath("a", "b", "c");

            assertTrue(matcher.test(path));

            verify(first).test(path);
            verify(first).and(second);
            verify(second).test(path);
            verifyNoMoreInteractions(first, second);
        }

        @Test
        void testToString() {
            PropertyPath.Matcher matcher = PropertyPath.startsWith("a").and(PropertyPath.endsWith("c"));

            assertEquals("and(startsWith(a), endsWith(c))", matcher.toString());
        }
    }

    @Nested
    class Or {

        @Test
        void testNoMatch() {
            PropertyPath.Matcher first = mock();
            when(first.test(any())).thenReturn(false);
            when(first.or(any())).thenAnswer(Answers.CALLS_REAL_METHODS);

            PropertyPath.Matcher second = mock();
            when(second.test(any())).thenReturn(false);

            PropertyPath.Matcher matcher = first.or(second);

            PropertyPath path = newPath("a", "b", "c");

            assertFalse(matcher.test(path));

            verify(first).test(path);
            verify(first).or(second);
            verify(second).test(path);
            verifyNoMoreInteractions(first, second);
        }

        @Test
        void testFirstMatches() {
            PropertyPath.Matcher first = mock();
            when(first.test(any())).thenReturn(true);
            when(first.or(any())).thenAnswer(Answers.CALLS_REAL_METHODS);

            PropertyPath.Matcher second = mock();

            PropertyPath.Matcher matcher = first.or(second);

            PropertyPath path = newPath("a", "b", "c");

            assertTrue(matcher.test(path));

            verify(first).test(path);
            verify(first).or(second);
            verifyNoMoreInteractions(first, second);
        }

        @Test
        void testSecondMatches() {
            PropertyPath.Matcher first = mock();
            when(first.test(any())).thenReturn(false);
            when(first.or(any())).thenAnswer(Answers.CALLS_REAL_METHODS);

            PropertyPath.Matcher second = mock();
            when(second.test(any())).thenReturn(true);

            PropertyPath.Matcher matcher = first.or(second);

            PropertyPath path = newPath("a", "b", "c");

            assertTrue(matcher.test(path));

            verify(first).test(path);
            verify(first).or(second);
            verify(second).test(path);
            verifyNoMoreInteractions(first, second);
        }

        @Test
        void testToString() {
            PropertyPath.Matcher matcher = PropertyPath.startsWith("a").or(PropertyPath.endsWith("c"));

            assertEquals("or(startsWith(a), endsWith(c))", matcher.toString());
        }
    }

    @Nested
    class Not {

        @Test
        void testNegatedDoesNotMatch() {
            PropertyPath.Matcher negated = mock();
            when(negated.test(any())).thenReturn(false);
            when(negated.negate()).thenAnswer(Answers.CALLS_REAL_METHODS);

            PropertyPath.Matcher matcher = negated.negate();

            PropertyPath path = newPath("a", "b", "c");

            assertTrue(matcher.test(path));

            verify(negated).test(path);
            verify(negated).negate();
            verifyNoMoreInteractions(negated);
        }

        @Test
        void testNegatedMatches() {
            PropertyPath.Matcher negated = mock();
            when(negated.test(any())).thenReturn(true);
            when(negated.negate()).thenAnswer(Answers.CALLS_REAL_METHODS);

            PropertyPath.Matcher matcher = negated.negate();

            PropertyPath path = newPath("a", "b", "c");

            assertFalse(matcher.test(path));

            verify(negated).test(path);
            verify(negated).negate();
            verifyNoMoreInteractions(negated);
        }

        @ParameterizedTest
        @ValueSource(booleans = { true, false })
        void testNegativeTwiceIsIdentity(boolean match) {
            PropertyPath.Matcher negated = mock();
            when(negated.test(any())).thenReturn(match);
            when(negated.negate()).thenAnswer(Answers.CALLS_REAL_METHODS);

            PropertyPath.Matcher matcher = negated.negate().negate();

            PropertyPath path = newPath("a", "b", "c");

            assertEquals(match, matcher.test(path));

            verify(negated).test(path);
            verify(negated).negate();
            verifyNoMoreInteractions(negated);
        }

        @Test
        void testToString() {
            PropertyPath.Matcher matcher = PropertyPath.startsWith("a").negate();

            assertEquals("not(startsWith(a))", matcher.toString());
        }
    }

    private static PropertyPath newPath(String... properties) {
        PropertyPath path = new PropertyPath();
        for (String property : properties) {
            path.push(property);
        }
        return path;
    }
}
