/*
 * PropertyPaths.java
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

import java.util.Iterator;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import com.github.robtimus.obfuscation.jackson.JSONObfuscator.PropertyPath;

final class PropertyPaths {

    private PropertyPaths() {
    }

    static String join(List<String> properties, String prefix, String postfix) {
        StringBuilder sb = new StringBuilder();
        sb.append(prefix);
        join(properties, sb);
        sb.append(postfix);
        return sb.toString();
    }

    private static void join(List<String> properties, StringBuilder target) {
        Iterator<String> iterator = properties.iterator();
        appendProperty(iterator.next(), target);
        while (iterator.hasNext()) {
            target.append('.');
            appendProperty(iterator.next(), target);
        }
    }

    private static void appendProperty(String property, StringBuilder sb) {
        if (property.indexOf('.') == -1) {
            sb.append(property);
        } else {
            sb.append('"').append(property).append('"');
        }
    }

    private static boolean equalsStartingAt(List<String> properties, List<String> expected, int start, BiPredicate<String, String> equals) {
        int propertiesSize = properties.size();
        int expectedSize = expected.size();
        int startIndex = start >= 0 ? start : propertiesSize + start;

        if (propertiesSize < expectedSize || startIndex < 0 || startIndex + expectedSize > propertiesSize) {
            return false;
        }
        for (Iterator<String> i = properties.listIterator(startIndex), j = expected.iterator(); j.hasNext(); ) {
            if (!equals.test(i.next(), j.next())) {
                return false;
            }
        }
        return true;
    }

    record IsMatcher(List<String> properties) implements PropertyPath.Matcher {

        @Override
        public boolean test(PropertyPath path) {
            return properties.equals(path.properties());
        }

        @Override
        @SuppressWarnings("nls")
        public String toString() {
            return join(properties, "is(", ")");
        }
    }

    record IsIgnoreCaseMatcher(List<String> properties) implements PropertyPath.Matcher {

        @Override
        public boolean test(PropertyPath path) {
            return properties.size() == path.length()
                    && hasEqualProperties(path);
        }

        private boolean hasEqualProperties(PropertyPath path) {
            for (Iterator<String> i = path.iterator(), j = properties.iterator(); j.hasNext(); ) {
                if (!i.next().equalsIgnoreCase(j.next())) {
                    return false;
                }
            }
            return true;
        }

        @Override
        @SuppressWarnings("nls")
        public String toString() {
            return join(properties, "isIgnoreCase(", ")");
        }
    }

    record StartsWithMatcher(List<String> prefix) implements PropertyPath.Matcher {

        @Override
        public boolean test(PropertyPath path) {
            return equalsStartingAt(path.properties(), prefix, 0, String::equals);
        }

        @Override
        @SuppressWarnings("nls")
        public String toString() {
            return join(prefix, "startsWith(", ")");
        }
    }

    record StartsWithIgnoreCaseMatcher(List<String> prefix) implements PropertyPath.Matcher {

        @Override
        public boolean test(PropertyPath path) {
            return equalsStartingAt(path.properties(), prefix, 0, String::equalsIgnoreCase);
        }

        @Override
        @SuppressWarnings("nls")
        public String toString() {
            return join(prefix, "startsWithIgnoreCase(", ")");
        }
    }

    record EndsWithMatcher(List<String> postfix) implements PropertyPath.Matcher {

        @Override
        public boolean test(PropertyPath path) {
            return equalsStartingAt(path.properties(), postfix, -postfix.size(), String::equals);
        }

        @Override
        @SuppressWarnings("nls")
        public String toString() {
            return join(postfix, "endsWith(", ")");
        }
    }

    record EndsWithIgnoreCaseMatcher(List<String> postfix) implements PropertyPath.Matcher {

        @Override
        public boolean test(PropertyPath path) {
            return equalsStartingAt(path.properties(), postfix, -postfix.size(), String::equalsIgnoreCase);
        }

        @Override
        @SuppressWarnings("nls")
        public String toString() {
            return join(postfix, "endsWithIgnoreCase(", ")");
        }
    }

    record ContainsAtMatcher(int index, List<String> properties) implements PropertyPath.Matcher {

        @Override
        public boolean test(PropertyPath path) {
            return equalsStartingAt(path.properties(), properties, index, String::equals);
        }

        @Override
        @SuppressWarnings("nls")
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("containsAt(").append(index).append(", ");
            join(properties, sb);
            sb.append(')');
            return sb.toString();
        }
    }

    record ContainsAtIgnoreCaseMatcher(int index, List<String> properties) implements PropertyPath.Matcher {

        @Override
        public boolean test(PropertyPath path) {
            return equalsStartingAt(path.properties(), properties, index, String::equalsIgnoreCase);
        }

        @Override
        @SuppressWarnings("nls")
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("containsAtIgnoreCase(").append(index).append(", ");
            join(properties, sb);
            sb.append(')');
            return sb.toString();
        }
    }

    record AndMatcher(Predicate<? super PropertyPath> first, Predicate<? super PropertyPath> second) implements PropertyPath.Matcher {

        @Override
        public boolean test(PropertyPath path) {
            return first.test(path) && second.test(path);
        }

        @Override
        @SuppressWarnings("nls")
        public String toString() {
            return "and(" + first + ", " + second + ")";
        }
    }

    record OrMatcher(Predicate<? super PropertyPath> first, Predicate<? super PropertyPath> second) implements PropertyPath.Matcher {

        @Override
        public boolean test(PropertyPath path) {
            return first.test(path) || second.test(path);
        }

        @Override
        @SuppressWarnings("nls")
        public String toString() {
            return "or(" + first + ", " + second + ")";
        }
    }

    record NotMatcher(PropertyPath.Matcher negated) implements PropertyPath.Matcher {

        @Override
        public boolean test(PropertyPath path) {
            return !negated.test(path);
        }

        @Override
        public PropertyPath.Matcher negate() {
            return negated;
        }

        @Override
        @SuppressWarnings("nls")
        public String toString() {
            return "not(" + negated + ")";
        }
    }
}
